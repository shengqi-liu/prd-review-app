package com.prdreview.ai.cache.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prdreview.ai.cache.LlmCacheEntry;
import com.prdreview.ai.cache.LlmCacheRepository;
import com.prdreview.ai.cache.assembler.LlmCacheAssembler;
import com.prdreview.ai.cache.mapper.LlmCacheMapper;
import com.prdreview.ai.cache.po.LlmCachePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LlmCacheRepositoryImpl implements LlmCacheRepository {

    private final LlmCacheMapper mapper;

    @Override
    public Optional<LlmCacheEntry> findByKey(String cacheKey) {
        if (cacheKey == null) return Optional.empty();
        LlmCachePO po = mapper.selectById(cacheKey);
        return Optional.ofNullable(LlmCacheAssembler.toDomain(po));
    }

    @Override
    public void save(LlmCacheEntry entry) {
        // 主键 cache_key 几乎不会碰撞;若已存在,insert 抛 DuplicateKeyException,
        // 上层 LlmCacheService 会捕获并降级,无需在此处理。
        mapper.insert(LlmCacheAssembler.toPO(entry));
    }

    @Override
    public void incrementHit(String cacheKey) {
        mapper.incrementHit(cacheKey);
    }

    @Override
    public int deleteByKey(String cacheKey) {
        if (cacheKey == null) return 0;
        return mapper.deleteById(cacheKey);
    }

    @Override
    public int deleteAll() {
        return mapper.delete(new LambdaQueryWrapper<>());
    }

    @Override
    public int deleteByCreatedAtBefore(LocalDateTime cutoff) {
        if (cutoff == null) return 0;
        return mapper.delete(new LambdaQueryWrapper<LlmCachePO>().lt(LlmCachePO::getCreatedAt, cutoff));
    }

    @Override
    public int deleteLeastRecentlyHit(int keepCount) {
        if (keepCount <= 0) return deleteAll();
        return mapper.deleteExceptTopByLastHit(keepCount);
    }

    @Override
    public long count() {
        return mapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public CacheStats stats() {
        LlmCacheMapper.LlmCacheStatsRow row = mapper.statsRow();
        if (row == null) return new CacheStats(0L, 0L, null, null);
        return new CacheStats(row.totalEntries, row.totalHits, row.oldestCreatedAt, row.newestCreatedAt);
    }
}
