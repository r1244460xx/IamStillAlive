package com.imsa.backend.scheduler;

import com.imsa.backend.service.UserSafetyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSafetyMonitorScheduler {

    private final UserSafetyService userSafetyService;

    /**
     * 定時執行使用者健在與安全狀態檢查
     * 
     * 開發與測試期設定：每分鐘執行一次 (fixedRate = 60000，即 60 秒)
     * 正式上線建議：
     * - 改用 cron 表示式，例如每天中午 12:00 執行一次：@Scheduled(cron = "0 0 12 * * ?")
     * - 或者是每小時執行一次：@Scheduled(cron = "0 0 * * * ?")
     */
    @Scheduled(fixedRate = 60000)
    public void runSafetyCheck() {
        log.info("排程器啟動：定時檢查安全狀態...");
        try {
            userSafetyService.checkActiveUsersSafety();
        } catch (Exception e) {
            log.error("執行安全狀態檢查時發生錯誤: ", e);
        }
    }
}
