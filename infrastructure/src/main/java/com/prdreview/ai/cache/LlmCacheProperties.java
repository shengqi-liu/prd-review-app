package com.prdreview.ai.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 响应缓存相关配置({@code llm.cache.*})。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm.cache")
public class LlmCacheProperties implements com.prdreview.ai.cache.LlmCacheConfigSwitch {

    /** 缓存总开关;false 时 LlmCacheService 直接降级,不查不写。 */
    private boolean enabled = true;

    /** TTL,超过该天数的条目被定时清理。 */
    private int ttlDays = 30;

    /** 缓存条目上限;超过按 last_hit_at LRU 淘汰。 */
    private int maxEntries = 10000;

    /** 清理调度间隔(ms),默认 1 小时。 */
    private long cleanupIntervalMs = 3_600_000L;
}
