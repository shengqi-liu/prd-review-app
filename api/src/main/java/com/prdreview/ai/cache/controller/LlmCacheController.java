package com.prdreview.ai.cache.controller;

import com.prdreview.ai.cache.LlmCacheRepository;
import com.prdreview.ai.cache.LlmCacheService;
import com.prdreview.ai.cache.dto.CacheDeleteResponse;
import com.prdreview.ai.cache.dto.CacheStatsResponse;
import com.prdreview.auth.model.UserRole;
import com.prdreview.common.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 响应缓存管理 REST(仅 ADMIN)。
 */
@Slf4j
@Tag(name = "LLM Cache")
@RestController
@RequestMapping("/api/v1/cache/llm")
@RequiredArgsConstructor
public class LlmCacheController {

    private final LlmCacheService cacheService;

    @Operation(summary = "缓存统计")
    @RequireRole(UserRole.ADMIN)
    @GetMapping("/stats")
    public CacheStatsResponse stats() {
        LlmCacheRepository.CacheStats s = cacheService.stats();
        // 粗略命中率:totalHits / (totalHits + totalEntries) — 每个条目至少经过一次 miss
        double rate = (s.totalHits() + s.totalEntries()) == 0
            ? 0.0
            : (double) s.totalHits() / (s.totalHits() + s.totalEntries());
        return new CacheStatsResponse(
            s.totalEntries(), s.totalHits(),
            Math.round(rate * 10000) / 10000.0,
            s.oldestCreatedAt(), s.newestCreatedAt()
        );
    }

    @Operation(summary = "清空全部缓存")
    @RequireRole(UserRole.ADMIN)
    @DeleteMapping
    public CacheDeleteResponse clearAll() {
        int n = cacheService.clearAll();
        log.warn("[LLM-Cache] ADMIN 手动清空全部缓存,删除 {} 条", n);
        return CacheDeleteResponse.batch(n);
    }

    @Operation(summary = "删除单条缓存(按 cache_key)")
    @RequireRole(UserRole.ADMIN)
    @DeleteMapping("/{key}")
    public CacheDeleteResponse clearOne(@PathVariable("key") String key) {
        boolean ok = cacheService.clearOne(key);
        return CacheDeleteResponse.single(ok);
    }
}
