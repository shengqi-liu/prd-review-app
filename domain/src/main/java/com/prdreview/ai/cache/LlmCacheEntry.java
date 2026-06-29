package com.prdreview.ai.cache;

import java.time.LocalDateTime;

/**
 * LLM 响应缓存条目(domain 值对象)。
 *
 * <p>由 {@link com.prdreview.ai.service.AiService} 在缓存 miss + LLM 调用成功后写入,
 * hit 时由 Repository 返回。
 *
 * @param cacheKey           SHA-256 hex of "provider:model:rawText",64 字符,作为主键
 * @param response           SummarizeResult 的 JSON 序列化
 * @param provider           LLM provider 标识
 * @param model              LLM model id
 * @param promptPreview      前 200 字符预览,运维查询用,不含敏感全文
 * @param tokenCountEstimate rawText.length / 4 粗略估算
 * @param hitCount           历史命中次数
 * @param createdAt          首次写入时间(用于 TTL 清理)
 * @param lastHitAt          最近命中时间(用于 LRU 淘汰)
 */
public record LlmCacheEntry(
    String cacheKey,
    String response,
    String provider,
    String model,
    String promptPreview,
    int tokenCountEstimate,
    int hitCount,
    LocalDateTime createdAt,
    LocalDateTime lastHitAt
) {
}
