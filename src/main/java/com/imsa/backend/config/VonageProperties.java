package com.imsa.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vonage")
public class VonageProperties {

    /**
     * Vonage (Nexmo) API Key (ex: c6c8a722)
     */
    private String apiKey = "c6c8a722";

    /**
     * Vonage API Secret
     */
    private String apiSecret;

    /**
     * 發送端名稱或門號 (ex: Vonage APIs)
     */
    private String from = "Vonage APIs";

    /**
     * 是否為 Dry-Run 模擬模式（預設 true，保護試用額度，不發出真實 HTTP 請求）
     */
    private boolean dryRun = true;
}
