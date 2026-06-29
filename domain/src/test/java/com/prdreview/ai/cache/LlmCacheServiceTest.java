package com.prdreview.ai.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCacheService 单元测试")
class LlmCacheServiceTest {

    @Mock LlmCacheRepository repository;

    private LlmCacheService newService(boolean enabled) {
        LlmCacheConfigSwitch sw = () -> enabled;
        return new LlmCacheService(repository, sw);
    }

    private LlmCacheEntry sampleEntry() {
        LocalDateTime now = LocalDateTime.now();
        return new LlmCacheEntry(
            "k".repeat(64), "{\"title\":\"t\",\"content\":\"c\"}",
            "openai-compatible", "deepseek-chat", "preview", 100,
            5, now.minusDays(1), now.minusMinutes(10)
        );
    }

    @Test
    @DisplayName("enabled=false 时 get 不查 repo,直接返回 empty")
    void get_disabled_noLookup() {
        LlmCacheService s = newService(false);
        Optional<String> r = s.get("anykey");
        assertThat(r).isEmpty();
        verify(repository, never()).findByKey(anyString());
    }

    @Test
    @DisplayName("enabled=false 时 put 不写 repo")
    void put_disabled_noWrite() {
        LlmCacheService s = newService(false);
        s.put("k", "json", "p", "m", "preview", 10);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("get 命中 → 返回 response + 触发 incrementHit")
    void get_hit_returnsAndIncrements() {
        LlmCacheEntry entry = sampleEntry();
        when(repository.findByKey(entry.cacheKey())).thenReturn(Optional.of(entry));

        LlmCacheService s = newService(true);
        Optional<String> r = s.get(entry.cacheKey());

        assertThat(r).contains(entry.response());
        verify(repository).incrementHit(entry.cacheKey());
    }

    @Test
    @DisplayName("get miss → 返回 empty,不调 incrementHit")
    void get_miss_empty() {
        when(repository.findByKey("missing")).thenReturn(Optional.empty());
        LlmCacheService s = newService(true);

        assertThat(s.get("missing")).isEmpty();
        verify(repository, never()).incrementHit(anyString());
    }

    @Test
    @DisplayName("get 查询抛异常 → 降级返回 empty,不传播")
    void get_repositoryException_downgrades() {
        when(repository.findByKey(anyString())).thenThrow(new RuntimeException("DB down"));
        LlmCacheService s = newService(true);

        assertThat(s.get("k")).isEmpty();
    }

    @Test
    @DisplayName("put 成功保存 entry,字段透传正确")
    void put_savesEntry() {
        LlmCacheService s = newService(true);

        s.put("hash64", "{\"a\":1}", "openai-compatible", "deepseek-chat", "preview text", 250);

        ArgumentCaptor<LlmCacheEntry> captor = ArgumentCaptor.forClass(LlmCacheEntry.class);
        verify(repository).save(captor.capture());
        LlmCacheEntry e = captor.getValue();
        assertThat(e.cacheKey()).isEqualTo("hash64");
        assertThat(e.response()).isEqualTo("{\"a\":1}");
        assertThat(e.provider()).isEqualTo("openai-compatible");
        assertThat(e.model()).isEqualTo("deepseek-chat");
        assertThat(e.promptPreview()).isEqualTo("preview text");
        assertThat(e.tokenCountEstimate()).isEqualTo(250);
        assertThat(e.hitCount()).isZero();
        assertThat(e.createdAt()).isNotNull();
        assertThat(e.lastHitAt()).isNotNull();
    }

    @Test
    @DisplayName("put 抛 DB 异常时降级,不向上传播")
    void put_repositoryException_downgrades() {
        org.mockito.Mockito.doThrow(new RuntimeException("PK conflict"))
            .when(repository).save(any());
        LlmCacheService s = newService(true);
        s.put("k", "r", "p", "m", "x", 1); // 不抛
        verify(repository).save(any());
    }

    @Test
    @DisplayName("null cache_key 不查不写")
    void nullKey_noOp() {
        LlmCacheService s = newService(true);
        assertThat(s.get(null)).isEmpty();
        s.put(null, "x", "p", "m", "y", 1);
        verify(repository, never()).findByKey(any());
        verify(repository, never()).save(any());
    }
}
