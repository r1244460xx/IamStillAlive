package com.imsa.backend.service.notification;

import com.imsa.backend.config.VonageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class VonageSmsService implements NotificationService {

    private final VonageProperties vonageProperties;
    private final RestClient restClient;

    @Autowired
    public VonageSmsService(VonageProperties vonageProperties) {
        this.vonageProperties = vonageProperties;
        this.restClient = RestClient.builder().build();
    }

    // 提供給測試注入自訂 RestClient 的建構子
    public VonageSmsService(VonageProperties vonageProperties, RestClient restClient) {
        this.vonageProperties = vonageProperties;
        this.restClient = restClient;
    }

    @Override
    public boolean sendEmergencyAlert(String recipientPhone, String message) {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            log.warn("⚠️ 收件人電話為空，取消簡訊發送。");
            return false;
        }

        String formattedPhone = formatToVonagePhone(recipientPhone);

        // 🛡️ 額度保護安全開關：Dry-Run 模式
        if (vonageProperties.isDryRun()) {
            log.info("================== 📱 [Vonage SMS - Dry Run 模擬模式] ==================");
            log.info("🛡️ 額度保護機制生效中：絕不呼叫外部網路，額度 0 消耗！");
            log.info("發送端 (From)    : {}", vonageProperties.getFrom());
            log.info("收件人 (To)      : {} (原始輸入: {})", formattedPhone, recipientPhone);
            log.info("通訊通道 (Channel): sms, 類型: text");
            log.info("簡訊內容 (Text)   :\n{}", message);
            log.info("=========================================================================");
            return true;
        }

        // 實體發送模式（需使用者明確同意且關閉 dry-run）
        if (vonageProperties.getApiKey() == null || vonageProperties.getApiKey().isBlank() ||
            vonageProperties.getApiSecret() == null || vonageProperties.getApiSecret().isBlank()) {
            log.error("❌ Vonage 憑證設定不完整，無法發送真實簡訊。請檢查 application.yml 或環境變數！");
            return false;
        }

        String url = "https://api.nexmo.com/v1/messages";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("to", formattedPhone);
        requestBody.put("from", vonageProperties.getFrom());
        requestBody.put("channel", "sms");
        requestBody.put("message_type", "text");
        requestBody.put("text", message);

        try {
            log.info("🚀 [Vonage SMS] 正在透過 Messages API 發送真實簡訊至 {}...", formattedPhone);

            String response = restClient.post()
                    .uri(url)
                    .headers(headers -> {
                        headers.setBasicAuth(
                                vonageProperties.getApiKey().trim(),
                                vonageProperties.getApiSecret().trim()
                        );
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set(headers.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    })
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.info("✅ [Vonage SMS] 簡訊發送成功！電信回傳: {}", response);
            return true;
        } catch (RestClientResponseException ex) {
            log.error("❌ [Vonage SMS 發送失敗] HTTP 狀態碼: {}, 錯誤回應: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("❌ [Vonage SMS 發送異常] 網路或系統例外: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 將電話號碼正規化為 Vonage 標準純數字格式 (如 0909280630 -> 886909280630)
     */
    public String formatToVonagePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)\\+]", "").trim();
        if (cleaned.startsWith("09") && cleaned.length() == 10) {
            return "886" + cleaned.substring(1);
        }
        return cleaned;
    }
}
