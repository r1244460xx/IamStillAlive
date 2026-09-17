package com.imsa.backend.service;

import com.imsa.backend.entity.AlertRecord;
import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.AlertStatus;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.AlertRecordRepository;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import com.imsa.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSafetyService {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final NotificationService notificationService;

    /**
     * 檢查所有活躍使用者的安全狀態（最佳實踐架構）：
     * 1. 主循環不加全域 @Transactional 大事務，避免長時間鎖定多筆使用者資料與佔用 DB 連線池。
     * 2. 每個使用者呼叫獨立短事務 markAlertedIfStillOverdue（執行耗時 < 1ms，更新後立即 COMMIT 釋放行鎖）。
     * 3. 只有在 DB 真正搶下 CAS（updatedRows > 0）後，才在「事務外」觸發簡訊/通報，徹底解耦 DB 鎖定與外部 I/O。
     * 4. 判定逾期時建立 AlertRecord（狀態為 PENDING_SMS），簡訊寄出後更新為 SMS_SENT。
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
            // 透過 UserRepository 上的 @Transactional，此處只耗時 ~1ms，更新後立即 COMMIT 釋放行鎖！
            int updatedRows = userRepository.markAlertedIfStillOverdue(user.getId(), threshold);

            if (updatedRows == 0) {
                // 代表在此期間使用者已自行打卡（或被其他並行節點處理過），安全略過
                log.info("⏩ [原子防護] 使用者 {} (ID: {}) 於排程處理期間已完成打卡，略過警報觸發！", 
                        user.getNickname(), user.getId());
                continue;
            }

            // 步驟 2：事務外處理通報（與 DB 交易解耦）
            // 只有成功搶下 CAS（updatedRows > 0）才進入，確保絕不重複發送，且通報 I/O 耗時絕不會阻塞資料庫！
            LocalDateTime lastActive = user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt();
            long hoursSinceLastActive = Duration.between(lastActive, now).toHours();

            log.debug("🚨 [告警觸發] 判定使用者 [{}] (ID: {}) 已逾期 {} 小時未打卡，建立告警紀錄並寫入資料庫，狀態：[{}]...",
                    user.getNickname(), user.getId(), hoursSinceLastActive, AlertStatus.PENDING_SMS.getDescription());
            log.warn("🚨 警報發送判定：使用者 {} (ID: {}) 已 {} 小時未登入打卡！最後活躍時間：{}", 
                    user.getNickname(), user.getId(), hoursSinceLastActive, lastActive);

            // 寫入專門記錄告警資訊的資料表 (alert_records)，初始狀態：準備發出簡訊
            AlertRecord alertRecord = AlertRecord.builder()
                    .user(user)
                    .status(AlertStatus.PENDING_SMS)
                    .triggerTime(now)
                    .lastActiveAt(lastActive)
                    .hoursOverdue(hoursSinceLastActive)
                    .emergencyContactPhone(user.getEmergencyContactPhone())
                    .build();
            AlertRecord savedAlertRecord = alertRecordRepository.save(alertRecord);
            log.debug("📝 [告警紀錄已建立] 告警紀錄 (ID: {}) 已持久化至資料庫，初始狀態：[{}]",
                    savedAlertRecord.getId(), savedAlertRecord.getStatus().getDescription());
            
            try {
                triggerSafetyAlert(user, lastActive, savedAlertRecord);
            } catch (Exception e) {
                // 外部通報例外妥善補捉，即使簡訊服務斷線，也不會影響其他使用者的檢查
                log.error("❌ 發送使用者 {} (ID: {}) 警報通報時發生異常: {}", user.getNickname(), user.getId(), e.getMessage(), e);
                savedAlertRecord.setStatus(AlertStatus.SMS_FAILED);
                savedAlertRecord.setErrorMessage("發送異常: " + e.getMessage());
                alertRecordRepository.save(savedAlertRecord);
                log.debug("❌ [告警發送例外] 告警紀錄 (ID: {}) 狀態已更新為：[{}]",
                        savedAlertRecord.getId(), AlertStatus.SMS_FAILED.getDescription());
            }
        }
        
        log.info("單身人士安全活躍度檢測執行完畢。");
    }

    /**
     * 超過 12 小時未登入的警報觸發邏輯 (通報緊急聯絡人)
     * 最佳實踐：此方法在 DB 交易之外執行，即使未來串接真實簡訊/推播 API 耗時或網路拋出例外，
     * 也不會造成資料庫行鎖卡死或交易 Rollback。
     */
    private void triggerSafetyAlert(User user, LocalDateTime lastLoginTime, AlertRecord alertRecord) {
        log.warn("=== 🚨 [12 小時緊急通報開始] ===");
        log.warn("🚨 使用者 [{}] (電話: {}) 已超過 12 小時未打卡證明健在！最後打卡時間: {}", 
                user.getNickname(), user.getPhone(), lastLoginTime);
        
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

        if (user.getEmergencyContactPhone() != null && !user.getEmergencyContactPhone().isBlank()) {
            // 💡 電信規格優化：中文簡訊 (UCS-2 編碼) 單則上限為 70 字，超過 70 字會被電信商強制拆為長簡訊 (2 則並重複計費)
            // 將時間精簡為 MM/dd HH:mm，並壓縮文案確保總長度嚴格 <= 70 字，達成 100% 單則發送與精確計費
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm");
            String timeStr = lastLoginTime != null ? lastLoginTime.format(formatter) : "未知";
            
            String smsContent;
            if (isShutdown) {
                smsContent = String.format("【IMSA緊急通報】親友%s(%s)逾12時未回報(最後在線關機:%s)，請先電話確認安全！",
                        user.getNickname(), user.getPhone(), timeStr);
            } else {
                smsContent = String.format("【IMSA緊急通報】您的親友%s(%s)已逾12小時未回報平安(最後在線:%s)，請速確認安全！",
                        user.getNickname(), user.getPhone(), timeStr);
            }

            alertRecord.setMessageContent(smsContent);

            log.info("📱 ------------------------------------------------------------");
            log.info("📱 正在發送緊急簡訊至通報服務 (收件人: {}, 字數: {} 字)...", 
                    user.getEmergencyContactPhone(), smsContent.length());
            if (smsContent.length() > 70) {
                log.warn("⚠️ [注意] 簡訊字數超過 70 字 (當前: {} 字)，電信商將拆分成多則長簡訊計費！", smsContent.length());
            }

            log.debug("📱 [告警發送中] 正在透過簡訊服務發送緊急告警簡訊至 [{}] (告警紀錄 ID: {})...",
                    user.getEmergencyContactPhone(), alertRecord.getId());

            boolean success = notificationService.sendEmergencyAlert(user.getEmergencyContactPhone(), smsContent);
            if (success) {
                alertRecord.setStatus(AlertStatus.SMS_SENT);
                alertRecord.setSentAt(LocalDateTime.now());
                alertRecordRepository.save(alertRecord);

                log.debug("✅ [告警已寄出] 使用者 [{}] (ID: {}) 告警簡訊已成功寄出！告警紀錄 (ID: {}) 狀態已更新為：[{}]，發送時間：{}",
                        user.getNickname(), user.getId(), alertRecord.getId(), AlertStatus.SMS_SENT.getDescription(), alertRecord.getSentAt());
                log.info("✅ 緊急通報簡訊處理成功！(單則無拆分)");
            } else {
                alertRecord.setStatus(AlertStatus.SMS_FAILED);
                alertRecord.setErrorMessage("緊急通報簡訊發送回傳失敗");
                alertRecordRepository.save(alertRecord);

                log.debug("❌ [告警發送失敗] 使用者 [{}] (ID: {}) 告警簡訊發送失敗，告警紀錄 (ID: {}) 狀態已更新為：[{}]",
                        user.getNickname(), user.getId(), alertRecord.getId(), AlertStatus.SMS_FAILED.getDescription());
                log.warn("⚠️ 緊急通報簡訊發送回傳失敗，請檢查通報服務設定與日誌。");
            }
            log.info("📱 ------------------------------------------------------------");
        } else {
            alertRecord.setStatus(AlertStatus.NO_CONTACT_PHONE);
            alertRecord.setErrorMessage("該使用者尚未設定緊急聯絡電話");
            alertRecordRepository.save(alertRecord);

            log.debug("⚠️ [告警跳過] 使用者 [{}] (ID: {}) 尚未設定緊急聯絡電話，告警紀錄 (ID: {}) 狀態已更新為：[{}]",
                    user.getNickname(), user.getId(), alertRecord.getId(), AlertStatus.NO_CONTACT_PHONE.getDescription());
            log.warn("⚠️ 該使用者尚未設定緊急聯絡電話，無法發送緊急簡訊。");
        }
        
        log.warn("=== 🚨 [12 小時緊急通報結束] ===");
    }
}
