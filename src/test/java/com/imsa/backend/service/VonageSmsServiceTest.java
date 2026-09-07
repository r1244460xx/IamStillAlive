package com.imsa.backend.service;

import com.imsa.backend.config.VonageProperties;
import com.imsa.backend.service.notification.VonageSmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VonageSmsServiceTest {

    private VonageProperties vonageProperties;
    private VonageSmsService vonageSmsService;

    @BeforeEach
    void setUp() {
        vonageProperties = new VonageProperties();
        vonageProperties.setApiKey("c6c8a722");
        vonageProperties.setApiSecret("DUMMY_SECRET");
        vonageProperties.setFrom("Vonage APIs");
        vonageProperties.setDryRun(true);

        vonageSmsService = new VonageSmsService(vonageProperties);
    }

    @Test
    @DisplayName("驗證台灣手機門號正規化為 Vonage 標準純數字格式 (8869xxxxxxxx)")
    void testFormatToVonagePhone() {
        assertEquals("886909280630", vonageSmsService.formatToVonagePhone("0909280630"));
        assertEquals("886909280630", vonageSmsService.formatToVonagePhone("0909-280-630"));
        assertEquals("886909280630", vonageSmsService.formatToVonagePhone("0909 280 630"));
        assertEquals("886909280630", vonageSmsService.formatToVonagePhone("+886909280630"));
        assertEquals("886909280630", vonageSmsService.formatToVonagePhone("886909280630"));
        assertEquals("", vonageSmsService.formatToVonagePhone(null));
        assertEquals("", vonageSmsService.formatToVonagePhone("   "));
    }

    @Test
    @DisplayName("驗證 Dry-Run 模式下絕不呼叫外部網路且返回成功（0 額度消耗保護）")
    void testDryRunProtection() {
        vonageProperties.setDryRun(true);

        boolean result = vonageSmsService.sendEmergencyAlert(
                "0909280630",
                "【測試簡訊】此為 Vonage Dry-Run 測試，不會發送真實簡訊。"
        );

        assertTrue(result, "Dry-Run 模式應安全模擬成功");
    }

    @Test
    @DisplayName("驗證電話為空時應安全略過，不發送簡訊")
    void testEmptyPhoneHandledGracefully() {
        assertFalse(vonageSmsService.sendEmergencyAlert(null, "測試內容"));
        assertFalse(vonageSmsService.sendEmergencyAlert("", "測試內容"));
        assertFalse(vonageSmsService.sendEmergencyAlert("   ", "測試內容"));
    }

    @Test
    @DisplayName("驗證當 Dry-Run 關閉但憑證不全時，安全攔截不發送請求")
    void testMissingCredentialsProtection() {
        vonageProperties.setDryRun(false);
        vonageProperties.setApiSecret(null);

        boolean result = vonageSmsService.sendEmergencyAlert(
                "0909280630",
                "測試內容"
        );

        assertFalse(result, "缺少憑證時應安全返回 false，防止拋出未處理異常");
    }
}
