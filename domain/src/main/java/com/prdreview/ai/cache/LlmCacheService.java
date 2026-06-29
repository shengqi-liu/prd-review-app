package com.prdreview.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LLM 响应缓存应用服务。
 *
 * <p>对外暴露给 {@code AiServiceImpl} 用的 get / put,所有方法都对异常做降级:
 * 任何缓存层故障 SHALL NOT 阻塞 LLM 主流程,降级为 WARN 日志 + 直接走 LLM。
 *
 * <p>{@code llm.cache.enabled=false} 时所有方法 no-op。
 */
@Slf4j
@Service
public class LlmCacheService {

    /** 命中 key 在日志里截前 N 字符,便于排查不影响 SHA-256 唯一性。 */
    private static final int KEY_LOG_PREFIX_LEN = 8;

    private final LlmCacheRepository repository;
    private final boolean enabled;

    @Autowired
    public LlmCacheService(LlmCacheRepository repository,
                           com.prdreview.ai.cache.LlmCacheConfigSwitch configSwitch) {
        this.repository = repository;
        this.enabled = configSwitch.isEnabled();
        log.info("[LLM-Cache] 初始化完成 enabled={}", enabled);
    }

    /**
     * 查询缓存。命中时同步触发 incrementHit(原子 SQL,毫秒级),命中条目原样返回。
     * 任何异常降级为 empty。
     */
    public Optional<String> get(String cacheKey) {
        if (!enabled || cacheKey == null) return Optional.empty();
        try {
            Optional<LlmCacheEntry> entry = repository.findByKey(cacheKey);
            if (entry.isPresent()) {
                try {
                    repository.incrementHit(cacheKey);
                } catch (Exception ex) {
                    // 计数失败不影响主路径
                    log.warn("[LLM-Cache] incrementHit 失败 key={} err={}", shortKey(cacheKey), ex.getMessage());
                }
                log.info("[LLM-Cache] hit key={} hits={}", shortKey(cacheKey), entry.get().hitCount() + 1);
                return Optional.of(entry.get().response());
            }
            log.debug("[LLM-Cache] miss key={}", shortKey(cacheKey));
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[LLM-Cache] 查询失败,跳过缓存 key={} err={}", shortKey(cacheKey), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写缓存。已存在 key 的重复 put 直接吞掉(主键冲突视为已缓存)。
     * 任何异常降级为 WARN,不向上传播。
     */
    public void put(String cacheKey, String response, String provider, String model,
                    String promptPreview, int tokenCountEstimate) {
        if (!enabled || cacheKey == null || response == null) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            LlmCacheEntry entry = new LlmCacheEntry(
                cacheKey, response, provider, model, promptPreview, tokenCountEstimate,
                0, now, now
            );
            repository.save(entry);
            log.info("[LLM-Cache] put key={} model={} tokens~{}", shortKey(cacheKey), model, tokenCountEstimate);
        } catch (Exception ex) {
            // 主键冲突(并发 put)或 DB 故障都降级
            log.warn("[LLM-Cache] 写入失败,不影响响应 key={} err={}", shortKey(cacheKey), ex.getMessage());
        }
    }

    public LlmCacheRepository.CacheStats stats() {
        return repository.stats();
    }

    public int clearAll() {
        return repository.deleteAll();
    }

    public boolean clearOne(String cacheKey) {
        return repository.deleteByKey(cacheKey) > 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String shortKey(String key) {
        return key == null ? "null"
            : (key.length() > KEY_LOG_PREFIX_LEN ? key.substring(0, KEY_LOG_PREFIX_LEN) : key);
    }
}
