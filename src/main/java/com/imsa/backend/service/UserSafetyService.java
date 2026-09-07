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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSafetyService {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;

    /**
     * 檢查所有活躍使用者的安全狀態（最佳實踐架構）：
     * 1. 主循環不加全域 @Transactional 大事務，避免長時間鎖定多筆使用者資料與佔用 DB 連線池。
     * 2. 每個使用者呼叫獨立短事務 markAlertedIfStillOverdue（執行耗時 < 1ms，更新後立即 COMMIT 釋放行鎖）。
     * 3. 只有在 DB 真正搶下 CAS（updatedRows > 0）後，才在「事務外」觸發簡訊/通報，徹底解耦 DB 鎖定與外部 I/O。
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

            log.warn("🚨 警報發送判定：使用者 {} (ID: {}) 已 {} 小時未登入打卡！最後活躍時間：{}", 
                    user.getNickname(), user.getId(), hoursSinceLastActive, lastActive);
            
            try {
                triggerSafetyAlert(user, lastActive);
            } catch (Exception e) {
                // 外部通報例外妥善補捉，即使簡訊服務斷線，也不會影響其他使用者的檢查
                log.error("❌ 發送使用者 {} (ID: {}) 警報通報時發生異常: {}", user.getNickname(), user.getId(), e.getMessage(), e);
            }
        }
        
        log.info("單身人士安全活躍度檢測執行完畢。");
    }

    /**
     * 超過 12 小時未登入的警報觸發邏輯 (通報緊急聯絡人)
     * 最佳實踐：此方法在 DB 交易之外執行，以 print log 完整模擬第三方簡訊閘道發送，
     * 即使未來串接真實簡訊/推播 API 耗時或網路拋出例外，也不會造成資料庫行鎖卡死或交易 Rollback。
     */
    private void triggerSafetyAlert(User user, LocalDateTime lastLoginTime) {
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
            // 📱 最佳實踐：以 Print Log 完整模擬簡訊發送
            String note = isShutdown ? "【系統附註：該受保護裝置最後紀錄為手機低電量關機，可能僅為手機斷電，請先嘗試電話聯繫確認】" : "請儘速確認其人身安全！";
            String smsContent = String.format("【IMSA 緊急通報】您關注的親友 [%s] (電話: %s) 已超過 12 小時未打卡回報平安，最後在線時間為 %s。%s",
                    user.getNickname(), user.getPhone(), lastLoginTime, note);

            log.info("📱 ------------------------------------------------------------");
            log.info("📱 [SMS 簡訊發送模擬] 正在發送緊急簡訊至第三方電信閘道...");
            log.info("📱 [SMS 簡訊發送模擬] 收件人電話（緊急聯絡人）: {}", user.getEmergencyContactPhone());
            log.info("📱 [SMS 簡訊發送模擬] 簡訊內容: \"{}\"", smsContent);
            log.info("✅ [SMS 簡訊發送模擬] 電信閘道回傳：簡訊已成功排程投遞！(Status: 200 OK, MessageId: MSG-SIM-{})", UUID.randomUUID().toString().substring(0, 8));
            log.info("📱 ------------------------------------------------------------");
        } else {
            log.warn("⚠️ 該使用者尚未設定緊急聯絡電話，無法發送緊急簡訊。");
        }
        
        log.warn("=== 🚨 [12 小時緊急通報結束] ===");
    }
}
