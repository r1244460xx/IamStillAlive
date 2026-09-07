package com.imsa.backend.service.notification;

public interface NotificationService {

    /**
     * 發送緊急安全通報
     *
     * @param recipientPhone 收件人（緊急聯絡人）電話號碼
     * @param message        簡訊內容
     * @return true 發送成功（或 dry-run 模擬成功）；false 發送失敗
     */
    boolean sendEmergencyAlert(String recipientPhone, String message);
}
