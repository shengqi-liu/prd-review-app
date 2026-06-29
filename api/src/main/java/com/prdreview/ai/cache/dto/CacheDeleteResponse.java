package com.prdreview.ai.cache.dto;

/**
 * 缓存删除响应:批量删返回 deletedCount,单条删返回 deleted true/false。
 */
public record CacheDeleteResponse(
    Integer deletedCount,
    Boolean deleted
) {
    public static CacheDeleteResponse batch(int count) {
        return new CacheDeleteResponse(count, null);
    }
    public static CacheDeleteResponse single(boolean ok) {
        return new CacheDeleteResponse(null, ok);
    }
}
