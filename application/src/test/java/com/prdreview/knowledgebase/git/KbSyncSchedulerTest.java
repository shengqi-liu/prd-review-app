package com.prdreview.knowledgebase.git;

import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.KbSyncScheduler;
import com.prdreview.knowledgebase.git.service.KbSyncTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KbSyncScheduler 单元测试")
class KbSyncSchedulerTest {

    @Mock KbRepositoryRepository repository;
    @Mock KbSyncTaskService syncTaskService;

    @InjectMocks KbSyncScheduler scheduler;

    @Test
    @DisplayName("schedule — 无仓库时空跑")
    void schedule_noRepoSkips() {
        when(repository.findActive()).thenReturn(null);
        scheduler.schedule();
        verify(syncTaskService, never()).executeAsync(any());
    }

    @Test
    @DisplayName("schedule — HEALTHY 仓库时触发 executeAsync")
    void schedule_dispatchesForHealthy() {
        KbRepository r = KbRepository.reconstruct(7L, "kb", "url", "main", "/tmp",
            AuthType.NONE, null, 3600000L, SyncStatus.HEALTHY,
            null, null, null, 1, 0, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findActive()).thenReturn(r);
        scheduler.schedule();
        verify(syncTaskService).executeAsync(7L);
    }

    @Test
    @DisplayName("schedule — SYNCING 仓库时跳过")
    void schedule_skipsWhenSyncing() {
        KbRepository r = KbRepository.reconstruct(7L, "kb", "url", "main", "/tmp",
            AuthType.NONE, null, 3600000L, SyncStatus.SYNCING,
            null, null, null, 1, 0, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findActive()).thenReturn(r);
        scheduler.schedule();
        verify(syncTaskService, never()).executeAsync(any());
    }
}
