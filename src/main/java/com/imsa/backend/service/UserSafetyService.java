package com.imsa.backend.service;

import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 檢查所有活躍使用者的安全狀態：
     * 1. 第 24 小時：超過 24 小時未打卡 -> 觸發 ALERTED 警報通報緊急聯絡人 (並檢測最後打卡是否為關機)
     * 2. 第 22 小時：超過 22 小時未打卡 -> 觸發 WARNING 預警僅通知本人手機 (避免狼來了誤報)
     */
    @Transactional
    public void checkActiveUsersSafety() {
        log.info("開始執行單身人士安全活躍度檢測...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold24h = now.minusHours(24);
        LocalDateTime threshold22h = now.minusHours(22);

        // ==========================================
        // 階段一：檢測滿 24 小時逾期使用者 (SAFE 或 WARNING -> ALERTED)
        // ==========================================
        List<User> overdueAlertUsers = userRepository.findByStatusAndSafetyStatusInAndLastActiveAtBefore(
                UserStatus.ACTIVE, List.of(SafetyStatus.SAFE, SafetyStatus.WARNING), threshold24h);

        if (!overdueAlertUsers.isEmpty()) {
            for (User user : overdueAlertUsers) {
                LocalDateTime lastActive = user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt();
                long hoursSinceLastActive = Duration.between(lastActive, now).toHours();

                log.warn("🚨 警報發送判定：使用者 {} (ID: {}) 已 {} 小時未登入打卡！最後活躍時間：{}", 
                        user.getNickname(), user.getId(), hoursSinceLastActive, lastActive);
                
                triggerSafetyAlert(user, lastActive);

                // 更新狀態為 ALERTED 並存檔，避免下一次排程掃描時重複報警
                user.setSafetyStatus(SafetyStatus.ALERTED);
                userRepository.save(user);
            }
            log.info("第 24 小時逾期檢測完畢，共觸發 {} 筆緊急聯絡人警報。", overdueAlertUsers.size());
        }

        // ==========================================
        // 階段二：檢測滿 22 小時預警使用者 (SAFE -> WARNING，僅提醒本人)
        // ==========================================
        List<User> overdueWarningUsers = userRepository.findByStatusAndSafetyStatusAndLastActiveAtBefore(
                UserStatus.ACTIVE, SafetyStatus.SAFE, threshold22h);

        if (!overdueWarningUsers.isEmpty()) {
            for (User user : overdueWarningUsers) {
                LocalDateTime lastActive = user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt();
                long hoursSinceLastActive = Duration.between(lastActive, now).toHours();

                log.warn("⚠️ 預警發送判定：使用者 {} (ID: {}) 已 {} 小時未打卡，觸發 22 小時本人預警！", 
                        user.getNickname(), user.getId(), hoursSinceLastActive);
                
                triggerSafetyWarning(user, lastActive);

                // 更新狀態為 WARNING 並存檔，等待 2 小時緩衝期
                user.setSafetyStatus(SafetyStatus.WARNING);
                userRepository.save(user);
            }
            log.info("第 22 小時預警檢測完畢，共觸發 {} 筆本人預警通報。", overdueWarningUsers.size());
        }

        if (overdueAlertUsers.isEmpty() && overdueWarningUsers.isEmpty()) {
            log.info("檢測完成：目前無任何預警或逾期未打卡之使用者。");
        }
    }

    /**
     * 22 小時本人預警通報 (僅發送至本人手機，絕不驚動緊急聯絡人)
     */
    private void triggerSafetyWarning(User user, LocalDateTime lastLoginTime) {
        log.warn("=== 🔔 [22 小時本人預警] ===");
        log.warn("⚠️ 使用者 [{}] (電話: {}) 已超過 22 小時未解鎖打卡！", user.getNickname(), user.getPhone());
        log.warn("📱 準備向本人手機發送預警推播：「您已超過 22 小時未解鎖手機，請解鎖確認平安；若滿 24 小時將正式通報緊急聯絡人。」");
        log.warn("=========================");
    }

    /**
     * 超過 24 小時未登入的警報觸發邏輯 (通報緊急聯絡人)
     */
    private void triggerSafetyAlert(User user, LocalDateTime lastLoginTime) {
        log.warn("=== 🚨 [24 小時緊急通報] ===");
        log.warn("🚨 使用者 [{}] (電話: {}) 已超過 24 小時未打卡證明健在！最後打卡時間: {}", 
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
            log.warn("📞 準備發送緊急通報至緊急聯絡人電話: [{}]", user.getEmergencyContactPhone());
            if (isShutdown) {
                log.warn("✉️ 通報附註：⚠️ 系統附註：該受保護裝置最後紀錄為【低電量關機】，可能僅為手機斷電，請先嘗試電話聯繫確認。");
            }
        } else {
            log.warn("⚠️ 該使用者尚未設定緊急聯絡電話，僅發送通知給本人。");
        }
        
        // TODO: 可在此串接第三方簡訊服務、語音通報或 LINE Notify
        log.warn("=========================");
    }
}
