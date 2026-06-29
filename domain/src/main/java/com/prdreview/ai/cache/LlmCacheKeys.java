package com.prdreview.ai.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * LLM 缓存 key 计算工具(纯静态,无状态)。
 *
 * <p>Key = SHA-256(provider + ':' + model + ':' + (rawText ?? ''))
 * → 固定 64 字符十六进制,作为 llm_cache 表主键。
 */
public final class LlmCacheKeys {

    /** 预览片段最长字符数,塞进 llm_cache.prompt_preview 字段。 */
    public static final int PREVIEW_MAX = 200;

    private LlmCacheKeys() {}

    /** 计算 cache key。任意入参 null 视为空串。 */
    public static String compute(String provider, String model, String rawText) {
        String composite = (provider == null ? "" : provider)
            + ":" + (model == null ? "" : model)
            + ":" + (rawText == null ? "" : rawText);
        return sha256Hex(composite);
    }

    /** rawText 的前 200 字符预览,换行/制表符替换为空格,便于运维一行展示。 */
    public static String preview(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "";
        String single = rawText.replaceAll("\\s+", " ").trim();
        return single.length() > PREVIEW_MAX ? single.substring(0, PREVIEW_MAX) : single;
    }

    /** SHA-256 hex(JDK 自带,无外部依赖)。 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256,几乎不可能触发
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
