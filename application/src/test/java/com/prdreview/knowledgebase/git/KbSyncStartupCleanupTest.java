package com.prdreview.knowledgebase.git;

import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.KbSyncStartupCleanup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证启动清理（fix-kb-sync-stuck-recovery）把残留 SYNCING 状态打回 ERROR。
 */
@DisplayName("KbSyncStartupCleanup 单元测试")
@ExtendWith(MockitoExtension.class)
class KbSyncStartupCleanupTest {

    @Mock
    private KbRepositoryRepository repository;

    @InjectMocks
    private KbSyncStartupCleanup cleanup;

    private KbRepository stuckRepo(Long id, String name) {
        return KbRepository.reconstruct(
            id, name, "https://example.com/r.git", "main",
            "/tmp/kb/" + id, AuthType.NONE, null,
            3_600_000L, SyncStatus.SYNCING,
            "abc123", LocalDateTime.now().minusMinutes(30),
            null, 1, 0,
            LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(30)
        );
    }

    @Test
    @DisplayName("findAllSyncing 返回 2 个仓库时，每个都被 markError + update")
    void cleanup_marksErrorOnAllStuckRepositories() {
        KbRepository r1 = stuckRepo(1L, "kb-1");
        KbRepository r2 = stuckRepo(2L, "kb-2");
        when(repository.findAllSyncing()).thenReturn(List.of(r1, r2));

        cleanup.run(null); // ApplicationArguments 在本场景未使用，传 null

        // 两个仓库都应被标 ERROR
        assertThat(r1.getSyncStatus()).isEqualTo(SyncStatus.ERROR);
        assertThat(r1.getLastErrorMessage()).contains("startup cleanup");
        assertThat(r2.getSyncStatus()).isEqualTo(SyncStatus.ERROR);
        assertThat(r2.getLastErrorMessage()).contains("startup cleanup");

        // repository.update 被各调用一次
        ArgumentCaptor<KbRepository> captor = ArgumentCaptor.forClass(KbRepository.class);
        verify(repository, times(2)).update(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(KbRepository::getId)
            .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("findAllSyncing 返回空列表时，不发生任何 update 调用")
    void cleanup_noOpWhenNothingStuck() {
        when(repository.findAllSyncing()).thenReturn(List.of());

        cleanup.run(null);

        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("markError 传入的消息包含 startup cleanup 关键字")
    void cleanup_errorMessageContainsStartupCleanupKeyword() {
        KbRepository r = stuckRepo(1L, "kb-1");
        when(repository.findAllSyncing()).thenReturn(List.of(r));

        cleanup.run(null);

        assertThat(r.getLastErrorMessage())
            .containsIgnoringCase("startup cleanup")
            .contains("stale SYNCING");
    }
}
