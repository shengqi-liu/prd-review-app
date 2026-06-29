package com.prdreview.ai.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmCacheKeys 单元测试")
class LlmCacheKeysTest {

    @Test
    @DisplayName("compute 相同输入产生相同 key,长度固定 64")
    void compute_deterministic() {
        String k1 = LlmCacheKeys.compute("openai-compatible", "deepseek-chat", "hello world");
        String k2 = LlmCacheKeys.compute("openai-compatible", "deepseek-chat", "hello world");
        assertThat(k1).isEqualTo(k2).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("compute 不同 provider 产生不同 key")
    void compute_providerChangesKey() {
        String k1 = LlmCacheKeys.compute("openai-compatible", "deepseek-chat", "x");
        String k2 = LlmCacheKeys.compute("anthropic", "deepseek-chat", "x");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("compute 不同 model 产生不同 key")
    void compute_modelChangesKey() {
        String k1 = LlmCacheKeys.compute("p", "model-A", "x");
        String k2 = LlmCacheKeys.compute("p", "model-B", "x");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("compute 不同 rawText 产生不同 key")
    void compute_rawTextChangesKey() {
        String k1 = LlmCacheKeys.compute("p", "m", "hello");
        String k2 = LlmCacheKeys.compute("p", "m", "world");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("compute null 入参视为空串,不抛异常")
    void compute_nullSafe() {
        String k = LlmCacheKeys.compute(null, null, null);
        assertThat(k).hasSize(64);
    }

    @Test
    @DisplayName("preview 截断到 200 字符,换行转空格")
    void preview_truncatesAndNormalizesWhitespace() {
        String raw = "line1\nline2\tindent  multi-space\n" + "x".repeat(300);
        String p = LlmCacheKeys.preview(raw);
        assertThat(p).hasSize(200);
        assertThat(p).doesNotContain("\n").doesNotContain("\t");
        // 多个连续空白被压缩
        assertThat(p).contains("line1 line2 indent multi-space");
    }

    @Test
    @DisplayName("preview 空/null 返回空串")
    void preview_emptyInput() {
        assertThat(LlmCacheKeys.preview(null)).isEmpty();
        assertThat(LlmCacheKeys.preview("")).isEmpty();
    }
}
