package com.prdreview.ai.cache.assembler;

import com.prdreview.ai.cache.LlmCacheEntry;
import com.prdreview.ai.cache.po.LlmCachePO;

/**
 * LlmCacheEntry ↔ LlmCachePO 双向转换器。
 */
public final class LlmCacheAssembler {

    private LlmCacheAssembler() {}

    public static LlmCacheEntry toDomain(LlmCachePO po) {
        if (po == null) return null;
        return new LlmCacheEntry(
            po.getCacheKey(),
            po.getResponse(),
            po.getProvider(),
            po.getModel(),
            po.getPromptPreview(),
            po.getTokenCountEstimate() == null ? 0 : po.getTokenCountEstimate(),
            po.getHitCount() == null ? 0 : po.getHitCount(),
            po.getCreatedAt(),
            po.getLastHitAt()
        );
    }

    public static LlmCachePO toPO(LlmCacheEntry entry) {
        if (entry == null) return null;
        LlmCachePO po = new LlmCachePO();
        po.setCacheKey(entry.cacheKey());
        po.setResponse(entry.response());
        po.setProvider(entry.provider());
        po.setModel(entry.model());
        po.setPromptPreview(entry.promptPreview());
        po.setTokenCountEstimate(entry.tokenCountEstimate());
        po.setHitCount(entry.hitCount());
        po.setCreatedAt(entry.createdAt());
        po.setLastHitAt(entry.lastHitAt());
        return po;
    }
}
