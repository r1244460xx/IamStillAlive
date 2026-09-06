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
     * 超過 24 小時未打卡 -> 觸發 ALERTED 警報通報緊急聯絡人 (並檢測最後打卡是否為關機)
     */
    @Transactional
    public void checkActiveUsersSafety() {
        log.info("開始執行單身人士安全活躍度檢測...");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(24);

        // 高效批次查詢：直接撈出狀態為 ACTIVE、目前為 SAFE、且最後活躍時間已超過 24 小時之使用者
        List<User> overdueSafeUsers = userRepository.findByStatusAndSafetyStatusAndLastActiveAtBefore(
                UserStatus.ACTIVE, SafetyStatus.SAFE, threshold);

        if (overdueSafeUsers.isEmpty()) {
            log.info("檢測完成：目前無任何逾期未打卡之使用者。");
            return;
        }

        for (User user : overdueSafeUsers) {
            // Case 6: 資料庫層級條件式原子更新（Conditional Atomic UPDATE）
            // 在行級鎖保護下原子判定：若在排程處理期間使用者剛好完成打卡，更新 0 筆，直接放棄警報！
            int updatedRows = userRepository.markAlertedIfStillOverdue(user.getId(), threshold);

            if (updatedRows == 0) {
                log.info("⏩ [原子防護] 使用者 {} (ID: {}) 於排程處理期間已完成打卡，略過警報觸發！", 
                        user.getNickname(), user.getId());
                continue;
            }

            LocalDateTime lastActive = user.getLastActiveAt() != null ? user.getLastActiveAt() : user.getCreatedAt();
            long hoursSinceLastActive = Duration.between(lastActive, now).toHours();

            log.warn("🚨 警報發送判定：使用者 {} (ID: {}) 已 {} 小時未登入打卡！最後活躍時間：{}", 
                    user.getNickname(), user.getId(), hoursSinceLastActive, lastActive);
            
            triggerSafetyAlert(user, lastActive);
            // 狀態已由 markAlertedIfStillOverdue 在 DB 原子更新為 ALERTED，不需再調用 userRepository.save() 避免舊資料覆蓋
        }
        
        log.info("單身人士安全活躍度檢測執行完畢。");
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
