package com.prdreview.ai.cache.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.ai.cache.po.LlmCachePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * llm_cache 表 Mapper。BaseMapper 提供 selectById / insert / deleteById,
 * 自定义 SQL 仅用于聚合统计与 LRU 子查询。
 */
@Mapper
public interface LlmCacheMapper extends BaseMapper<LlmCachePO> {

    /** 聚合统计:总条目 / 累计 hit / 最老最新条目时间。 */
    @Select("SELECT COUNT(*) AS totalEntries, " +
            "       COALESCE(SUM(hit_count), 0) AS totalHits, " +
            "       MIN(created_at) AS oldestCreatedAt, " +
            "       MAX(created_at) AS newestCreatedAt " +
            "  FROM llm_cache")
    LlmCacheStatsRow statsRow();

    /**
     * LRU 淘汰子查询:删除 last_hit_at 不在最近 keepCount 条内的所有条目。
     * 注意 MySQL 不允许在 DELETE 的子查询中引用同表,故包一层 SELECT * FROM (...).
     */
    @org.apache.ibatis.annotations.Delete(
        "DELETE FROM llm_cache " +
        " WHERE cache_key NOT IN ( " +
        "   SELECT cache_key FROM ( " +
        "     SELECT cache_key FROM llm_cache " +
        "      ORDER BY last_hit_at DESC, created_at DESC " +
        "      LIMIT #{keepCount} " +
        "   ) AS keep " +
        " )"
    )
    int deleteExceptTopByLastHit(int keepCount);

    /** 把命中计数 +1 并刷新 last_hit_at,单条 UPDATE。 */
    @org.apache.ibatis.annotations.Update(
        "UPDATE llm_cache SET hit_count = hit_count + 1, last_hit_at = NOW() WHERE cache_key = #{key}"
    )
    int incrementHit(@org.apache.ibatis.annotations.Param("key") String key);

    /** stats 查询返回的扁平行(包私有,只供 Repository 转换)。 */
    class LlmCacheStatsRow {
        public long totalEntries;
        public long totalHits;
        public LocalDateTime oldestCreatedAt;
        public LocalDateTime newestCreatedAt;
    }
}
