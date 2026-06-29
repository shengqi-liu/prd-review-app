package com.prdreview.ai.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * LLM 缓存定时清理:TTL + LRU。
 *
 * <p>默认 1 小时跑一次({@code llm.cache.cleanup-interval-ms})。
 *
 * <p>顺序:先 TTL 删过期(减少存量),再判 LRU(超容量才动)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCacheCleanupScheduler {

    private final LlmCacheRepository repository;
    private final LlmCacheProperties properties;

    @Scheduled(fixedDelayString = "${llm.cache.cleanup-interval-ms:3600000}",
               initialDelayString = "${llm.cache.cleanup-interval-ms:3600000}")
    public void cleanup() {
        if (!properties.isEnabled()) return;
        try {
            // 1) TTL 清
            LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getTtlDays());
            int ttlDeleted = repository.deleteByCreatedAtBefore(cutoff);
            // 2) LRU 淘汰(只在超容量时触发)
            long total = repository.count();
            int lruDeleted = 0;
            if (total > properties.getMaxEntries()) {
                lruDeleted = repository.deleteLeastRecentlyHit(properties.getMaxEntries());
            }
            if (ttlDeleted > 0 || lruDeleted > 0) {
                log.info("[LLM-Cache-Cleanup] ttlDeleted={} lruDeleted={} remaining={}",
                    ttlDeleted, lruDeleted, repository.count());
            }
        } catch (Exception ex) {
            log.warn("[LLM-Cache-Cleanup] 清理失败,本轮跳过: {}", ex.getMessage());
        }
    }
}
