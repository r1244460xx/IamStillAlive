package com.imsa.backend.service;

import com.imsa.backend.entity.AlertDeliveryRecord;
import com.imsa.backend.entity.AlertRecord;
import com.imsa.backend.entity.EmergencyContact;
import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.AlertStatus;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.AlertDeliveryRecordRepository;
import com.imsa.backend.repository.AlertRecordRepository;
import com.imsa.backend.repository.EmergencyContactRepository;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import com.imsa.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSafetyService {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final AlertDeliveryRecordRepository alertDeliveryRecordRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final NotificationService notificationService;

    /**
     * 檢查所有活躍使用者的安全狀態（最佳實踐架構）：
     * 1. 主循環不加全域 @Transactional 大事務，避免長時間鎖定多筆使用者資料與佔用 DB 連線池。
     * 2. 每個使用者呼叫獨立短事務 markAlertedIfStillOverdue（執行耗時 < 1ms，更新後立即 COMMIT 釋放行鎖）。
     * 3. 只有在 DB 真正搶下 CAS（updatedRows > 0）後，才在「事務外」觸發簡訊/通報，徹底解耦 DB 鎖定與外部 I/O。
     * 4. 判定逾期時建立 AlertRecord（狀態為 PENDING_SMS），多位聯絡人群發，並隔離發送失敗。
     */
    public void checkActiveUsersSafety() {
        log.info("開始執行單身人士安全活躍度檢測...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(12);

        // 批次查詢：撈出潛在逾期名單（唯讀快照，不佔用行寫鎖）
        List<User> overdueSafeUsers = userRepository.findByStatusAndSafetyStatusAndLastActiveAtBefore(
                UserStatus.ACTIVE, SafetyStatus.SAFE, threshold);

        if (overdueSafeUsers.isEmpty()) {
            log.info("檢測完成：目前無任何逾期未打卡之使用者。");
            return;
        }

        for (User user : overdueSafeUsers) {
            // 步驟 1：獨立短事務（Atomic CAS UPDATE）
            int updatedRows = userRepository.markAlertedIfStillOverdue(user.getId(), threshold);

            if (updatedRows == 0) {
                log.info("⏩ [原子防護] 使用者 {} (ID: {}) 於排程處理期間已完成打卡，略過警報觸發！", 
                        user.getNickname(), user.getId());
                continue;
            }

            // 步驟 2：事務外處理通報（與 DB 交易解耦）
            LocalDateTime lastActive = user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt();
            long hoursSinceLastActive = Duration.between(lastActive, now).toHours();

            log.debug("🚨 [告警觸發] 判定使用者 [{}] (ID: {}) 已逾期 {} 小時未打卡，開始執行緊急通報流程...",
                    user.getNickname(), user.getId(), hoursSinceLastActive);
            log.warn("🚨 警報發送判定：使用者 {} (ID: {}) 已 {} 小時未登入打卡！最後活躍時間：{}", 
                    user.getNickname(), user.getId(), hoursSinceLastActive, lastActive);

            try {
                processUserAlert(user, lastActive, hoursSinceLastActive, now);
            } catch (Exception e) {
                log.error("❌ 處理使用者 {} (ID: {}) 警報通報時發生未預期異常: {}", user.getNickname(), user.getId(), e.getMessage(), e);
            }
        }

        log.info("單身人士安全活躍度檢測執行完畢。");
    }

    private void processUserAlert(User user, LocalDateTime lastActive, long hoursSinceLastActive, LocalDateTime now) {
        List<EmergencyContact> contacts = emergencyContactRepository.findByUserIdOrderByCreatedAtAsc(user.getId());

        if (contacts.isEmpty()) {
            AlertRecord alertRecord = AlertRecord.builder()
                    .user(user)
                    .status(AlertStatus.NO_CONTACT_PHONE)
                    .triggerTime(now)
                    .lastActiveAt(lastActive)
                    .hoursOverdue(hoursSinceLastActive)
                    .totalContacts(0)
                    .successCount(0)
                    .failedCount(0)
                    .messageContent("該使用者尚未設定緊急聯絡人")
                    .build();
            alertRecordRepository.save(alertRecord);

            log.debug("⚠️ [告警跳過] 使用者 [{}] (ID: {}) 尚未設定緊急聯絡人，告警紀錄已標記為：[{}]",
                    user.getNickname(), user.getId(), AlertStatus.NO_CONTACT_PHONE.getDescription());
            log.warn("⚠️ 使用者 {} 尚未設定緊急聯絡人，無法發送緊急簡訊。", user.getNickname());
            return;
        }

        // 構建通報簡訊內容
        String smsContent = buildSmsContent(user, lastActive);

        // 建立告警事件主表 (alert_records)
        AlertRecord alertRecord = AlertRecord.builder()
                .user(user)
                .status(AlertStatus.PENDING_SMS)
                .triggerTime(now)
                .lastActiveAt(lastActive)
                .hoursOverdue(hoursSinceLastActive)
                .totalContacts(contacts.size())
                .successCount(0)
                .failedCount(0)
                .messageContent(smsContent)
                .build();
        AlertRecord savedAlertRecord = alertRecordRepository.save(alertRecord);

        log.info("=== 🚨 [12 小時緊急通報群發開始] 使用者: {}，聯絡人數: {} ===", user.getNickname(), contacts.size());

        int successCount = 0;
        int failedCount = 0;

        for (EmergencyContact contact : contacts) {
            // 建立個別聯絡人發送明細 (alert_delivery_records)
            AlertDeliveryRecord deliveryRecord = AlertDeliveryRecord.builder()
                    .alertRecord(savedAlertRecord)
                    .contactName(contact.getName())
                    .contactPhone(contact.getPhone())
                    .status(AlertStatus.PENDING_SMS)
                    .build();
            deliveryRecord = alertDeliveryRecordRepository.save(deliveryRecord);

            // 🌟 故障隔離：個別發送簡訊，單一聯絡人失敗/例外絕不中斷迴圈！
            try {
                log.info("📱 正在發送緊急簡訊至 [{}] ({}, 字數: {} 字)...", 
                        contact.getName(), contact.getPhone(), smsContent.length());
                if (smsContent.length() > 70) {
                    log.warn("⚠️ [注意] 簡訊字數超過 70 字 (當前: {} 字)，電信商將拆分成多則長簡訊計費！", smsContent.length());
                }

                boolean success = notificationService.sendEmergencyAlert(contact.getPhone(), smsContent);
                if (success) {
                    deliveryRecord.setStatus(AlertStatus.SMS_SENT);
                    deliveryRecord.setSentAt(LocalDateTime.now());
                    successCount++;
                    log.info("✅ 聯絡人 [{}] ({}) 告警簡訊已成功寄出！", contact.getName(), contact.getPhone());
                } else {
                    deliveryRecord.setStatus(AlertStatus.SMS_FAILED);
                    deliveryRecord.setErrorMessage("簡訊服務回傳發送失敗");
                    failedCount++;
                    log.warn("⚠️ 聯絡人 [{}] ({}) 簡訊發送回傳失敗", contact.getName(), contact.getPhone());
                }
            } catch (Exception e) {
                deliveryRecord.setStatus(AlertStatus.SMS_FAILED);
                deliveryRecord.setErrorMessage("發送異常: " + e.getMessage());
                failedCount++;
                log.error("❌ 聯絡人 [{}] ({}) 簡訊發送拋出例外: {} (已隔離並繼續下一位)", 
                        contact.getName(), contact.getPhone(), e.getMessage(), e);
            }
            alertDeliveryRecordRepository.save(deliveryRecord);
        }

        // 迴圈結束後更新告警事件主表統計
        savedAlertRecord.setSuccessCount(successCount);
        savedAlertRecord.setFailedCount(failedCount);
        if (successCount > 0 && failedCount == 0) {
            savedAlertRecord.setStatus(AlertStatus.SMS_SENT);
        } else if (successCount > 0 && failedCount > 0) {
            savedAlertRecord.setStatus(AlertStatus.PARTIALLY_SENT);
        } else {
            savedAlertRecord.setStatus(AlertStatus.SMS_FAILED);
        }
        alertRecordRepository.save(savedAlertRecord);

        log.info("=== 🚨 [12 小時緊急通報群發結束] 總計: {}, 成功: {}, 失敗: {}, 最終狀態: [{}] ===",
                contacts.size(), successCount, failedCount, savedAlertRecord.getStatus().getDescription());
    }

    private String buildSmsContent(User user, LocalDateTime lastLoginTime) {
        // 檢測使用者最後一筆打卡紀錄是否為手機低電量/關機
        Optional<LoginRecord> lastRecord = loginRecordRepository.findFirstByUserIdOrderByLoginTimeDesc(user.getId());
        boolean isShutdown = false;
        if (lastRecord.isPresent()) {
            LoginRecord record = lastRecord.get();
            String netType = record.getNetworkType();
            String remark = record.getRemark();
            if ((netType != null && netType.toLowerCase().contains("shutdown")) ||
                (remark != null && remark.contains("關機"))) {
                isShutdown = true;
                log.warn("💡 [重要線索] 該使用者裝置最後一筆紀錄為【手機關機/低電量】(類型: {}, 備註: {}, 時間: {})", 
                        netType, remark, record.getLoginTime());
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        String timeStr = lastLoginTime != null ? lastLoginTime.format(formatter) : "未知";

        if (isShutdown) {
            return String.format("【IMSA緊急通報】親友%s(%s)逾12時未回報(最後在線關機:%s)，請先電話確認安全！",
                    user.getNickname(), user.getPhone(), timeStr);
        } else {
            return String.format("【IMSA緊急通報】您的親友%s(%s)已逾12小時未回報平安(最後在線:%s)，請速確認安全！",
                    user.getNickname(), user.getPhone(), timeStr);
        }
    }
}
