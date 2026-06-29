package com.prdreview.ai.cache;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LLM 响应缓存持久化接口(领域层定义,基础设施层实现)。
 *
 * <p>所有方法应被 application 层 {@code LlmCacheService} 用 try-catch 包裹,
 * 任何异常都不应阻塞主 LLM 调用流程(缓存是优化层,故障降级到直接走 LLM)。
 */
public interface LlmCacheRepository {

    /** 按 cache_key 查询。 */
    Optional<LlmCacheEntry> findByKey(String cacheKey);

    /** 插入新条目(已存在不更新)。 */
    void save(LlmCacheEntry entry);

    /**
     * 命中后原子更新:{@code hit_count = hit_count + 1, last_hit_at = NOW()}。
     * 并发命中两个事务都 +1 但只一个生效可接受(统计 ±1 误差不影响功能)。
     */
    void incrementHit(String cacheKey);

    /** 按 key 删,返回影响行数(0 或 1)。 */
    int deleteByKey(String cacheKey);

    /** 清空全部,返回行数。 */
    int deleteAll();

    /** TTL 清:删除 created_at < cutoff 的条目,返回行数。 */
    int deleteByCreatedAtBefore(LocalDateTime cutoff);

    /**
     * LRU 淘汰:仅保留 last_hit_at 最近的 keepCount 条,删除其余。
     * keepCount &lt;= 0 时等价于 deleteAll。返回删除行数。
     */
    int deleteLeastRecentlyHit(int keepCount);

    /** 当前条目数。 */
    long count();

    /** 聚合统计:总条目 / 累计 hit 数 / 最老最新条目时间。 */
    CacheStats stats();

    /** 缓存聚合统计 DTO(domain 层内嵌)。 */
    record CacheStats(
        long totalEntries,
        long totalHits,
        LocalDateTime oldestCreatedAt,
        LocalDateTime newestCreatedAt
    ) {}
}
