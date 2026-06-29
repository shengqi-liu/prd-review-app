package com.prdreview.ai.cache.dto;

import java.time.LocalDateTime;

/**
 * LLM 缓存统计响应。
 *
 * @param totalEntries    当前缓存条目数
 * @param totalHits       累计命中次数(所有条目的 hit_count 之和)
 * @param hitRate         粗略命中率 = totalHits / (totalHits + totalEntries),无写入时为 0.0
 * @param oldestCreatedAt 最老条目写入时间
 * @param newestCreatedAt 最新条目写入时间
 */
public record CacheStatsResponse(
    long totalEntries,
    long totalHits,
    double hitRate,
    LocalDateTime oldestCreatedAt,
    LocalDateTime newestCreatedAt
) {}
