package com.prdreview.ai.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCacheCleanupScheduler 单元测试")
class LlmCacheCleanupSchedulerTest {

    @Mock LlmCacheRepository repository;

    private LlmCacheProperties props;
    private LlmCacheCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        props = new LlmCacheProperties();
        props.setEnabled(true);
        props.setTtlDays(30);
        props.setMaxEntries(10000);
        scheduler = new LlmCacheCleanupScheduler(repository, props);
    }

    @Test
    @DisplayName("enabled=false 时不调任何 repo 方法")
    void disabled_noOp() {
        props.setEnabled(false);
        scheduler.cleanup();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("TTL 清理:用 NOW - ttlDays 作为 cutoff")
    void cleanup_ttlUsesCutoff() {
        when(repository.count()).thenReturn(0L);

        scheduler.cleanup();

        ArgumentCaptor<LocalDateTime> cap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cap.capture());
        // cutoff 应在 now 之前约 30 天
        LocalDateTime cutoff = cap.getValue();
        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        long diffSec = Math.abs(java.time.Duration.between(cutoff, expected).getSeconds());
        assertThat(diffSec).isLessThan(5); // 容忍 5 秒
    }

    @Test
    @DisplayName("count <= maxEntries 时不调 LRU")
    void cleanup_underCapacity_noLru() {
        when(repository.count()).thenReturn(5000L);

        scheduler.cleanup();

        verify(repository, never()).deleteLeastRecentlyHit(anyInt());
    }

    @Test
    @DisplayName("count > maxEntries 时调 LRU,保留 maxEntries 条")
    void cleanup_overCapacity_lru() {
        when(repository.count()).thenReturn(10001L).thenReturn(10000L); // 第二次给 final log

        scheduler.cleanup();

        verify(repository).deleteLeastRecentlyHit(10000);
    }

    @Test
    @DisplayName("repository 抛异常时 cleanup 不传播")
    void cleanup_repositoryException_swallowed() {
        when(repository.deleteByCreatedAtBefore(any())).thenThrow(new RuntimeException("DB down"));
        scheduler.cleanup(); // 不抛
    }
}
