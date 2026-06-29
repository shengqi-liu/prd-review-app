package com.prdreview.ai.cache;

/**
 * 缓存总开关抽象 — application 层定义,infrastructure 层注入实现。
 *
 * <p>避免 LlmCacheService 直接依赖 infrastructure 层 LlmCacheProperties。
 */
public interface LlmCacheConfigSwitch {
    boolean isEnabled();
}
