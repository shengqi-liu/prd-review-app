package com.prdreview.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 基础设施通用配置（与 provider 无关部分）。
 * <pre>
 * ai:
 *   fetch:
 *     timeout-connect-ms: 5000
 *     timeout-read-ms: 15000
 *     user-agent: "PrdReview-Bot/1.0"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiProperties {

    private Fetch fetch = new Fetch();

    @Getter
    @Setter
    public static class Fetch {
        /** WebClient 连接超时（毫秒），默认 5000 */
        private long timeoutConnectMs = 5000;
        /** WebClient 响应超时（毫秒），默认 15000 */
        private long timeoutReadMs = 15000;
        /** HTTP User-Agent，默认 PrdReview-Bot/1.0 */
        private String userAgent = "PrdReview-Bot/1.0";
    }
}
