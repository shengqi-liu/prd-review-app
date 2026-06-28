package com.prdreview.knowledgebase.git;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.event.KbDocumentChangedEvent;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.ChangeType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.MarkdownChange;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.GitWatcher;
import com.prdreview.knowledgebase.git.service.KbSyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KbSyncTaskService 单元测试")
class KbSyncTaskServiceTest {

    @Mock KbRepositoryRepository repository;
    @Mock GitWatcher gitWatcher;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks KbSyncTaskService service;

    @BeforeEach
    void setupUpdateMockToReturnArg() {
        // fix-kb-sync-correctness：repository.update 现在返回 KbRepository（带新 version）。
        // 测试用入参直传当作"已自增"的替身，符合修复后的契约。
        org.mockito.Mockito.lenient().when(repository.update(any()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private KbRepository repo(SyncStatus status, String lastCommit) {
        return KbRepository.reconstruct(
            1L, "kb", "https://x/y.git", "main", "/tmp/repo-1",
            AuthType.HTTPS_TOKEN, "tok", 3600000L, status,
            lastCommit, lastCommit != null ? LocalDateTime.now().minusHours(1) : null, null,
            1, 0, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("首次同步 — clone + 全量 ADDED 事件 → HEALTHY")
    void firstSync_cloneAndAddAll() {
        KbRepository r = repo(SyncStatus.HEALTHY, null);
        when(repository.findById(1L)).thenReturn(r);
        when(gitWatcher.cloneRepository(any(), any(), any(), any(), any())).thenReturn("HEAD1");
        when(gitWatcher.diffMarkdownChanges(any(), isNull(), eq("HEAD1")))
            .thenReturn(List.of(
                new MarkdownChange("a.md", ChangeType.ADDED),
                new MarkdownChange("b.md", ChangeType.ADDED)
            ));

        service.execute(1L);

        verify(gitWatcher).cloneRepository(eq("https://x/y.git"), eq("main"), eq("/tmp/repo-1"),
            eq(AuthType.HTTPS_TOKEN), eq("tok"));
        ArgumentCaptor<KbDocumentChangedEvent> events = ArgumentCaptor.forClass(KbDocumentChangedEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).hasSize(2);
        assertThat(events.getAllValues()).extracting(KbDocumentChangedEvent::changeType)
            .containsOnly(ChangeType.ADDED);
        // markSyncing + markHealthy 各触发一次 update
        verify(repository, atLeastOnce()).update(any());
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.HEALTHY);
        assertThat(r.getLastSyncedCommit()).isEqualTo("HEAD1");
    }

    @Test
    @DisplayName("增量同步 — fetchAndReset + diff 事件混合分发")
    void incrementalSync_mixedChanges() {
        // 让 localPath 存在 .git 目录（用一个不会自动创建的临时路径但带 .git 子目录）
        java.io.File tempRepo = new java.io.File(System.getProperty("java.io.tmpdir"),
            "kb-sync-test-" + System.nanoTime());
        new java.io.File(tempRepo, ".git").mkdirs();

        KbRepository r = KbRepository.reconstruct(
            1L, "kb", "https://x/y.git", "main", tempRepo.getAbsolutePath(),
            AuthType.NONE, null, 3600000L, SyncStatus.HEALTHY,
            "OLD", LocalDateTime.now(), null,
            1, 0, LocalDateTime.now(), LocalDateTime.now());

        try {
            when(repository.findById(1L)).thenReturn(r);
            when(gitWatcher.fetchAndReset(any(), any(), any(), any())).thenReturn("NEW");
            when(gitWatcher.diffMarkdownChanges(eq(tempRepo.getAbsolutePath()), eq("OLD"), eq("NEW")))
                .thenReturn(List.of(
                    new MarkdownChange("a.md", ChangeType.MODIFIED),
                    new MarkdownChange("b.md", ChangeType.DELETED),
                    new MarkdownChange("c.md", ChangeType.ADDED)
                ));

            service.execute(1L);

            verify(gitWatcher).fetchAndReset(any(), any(), any(), any());
            verify(gitWatcher, never()).cloneRepository(any(), any(), any(), any(), any());
            verify(publisher, atLeastOnce()).publishEvent(any(KbDocumentChangedEvent.class));
            assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.HEALTHY);
            assertThat(r.getLastSyncedCommit()).isEqualTo("NEW");
        } finally {
            new java.io.File(tempRepo, ".git").delete();
            tempRepo.delete();
        }
    }

    @Test
    @DisplayName("凭据失败 — 捕获 KB_GIT_AUTH_FAILED → markError，事件不发布")
    void authFailure_marksError() {
        KbRepository r = repo(SyncStatus.HEALTHY, null);
        when(repository.findById(1L)).thenReturn(r);
        when(gitWatcher.cloneRepository(any(), any(), any(), any(), any()))
            .thenThrow(new BizException(ErrorCode.KB_GIT_AUTH_FAILED, "bad token"));

        service.execute(1L);

        verify(publisher, never()).publishEvent(any());
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.ERROR);
        assertThat(r.getLastErrorMessage()).contains("bad token");
    }

    @Test
    @DisplayName("SYNCING 状态 — 跳过本轮，不发起 git 操作")
    void skipWhenAlreadySyncing() {
        KbRepository r = repo(SyncStatus.SYNCING, "OLD");
        when(repository.findById(1L)).thenReturn(r);

        service.execute(1L);

        verify(gitWatcher, never()).cloneRepository(any(), any(), any(), any(), any());
        verify(gitWatcher, never()).fetchAndReset(any(), any(), any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("仓库不存在 — 安静返回，不抛异常")
    void notFound_silent() {
        when(repository.findById(99L)).thenReturn(null);
        service.execute(99L);
        verify(gitWatcher, never()).cloneRepository(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fix-kb-sync-correctness Bug B：连续 markSyncing+markError 时 update 用刷新后 version，不静默失败")
    void markSyncingThenMarkError_usesRefreshedVersion() {
        KbRepository initial = repo(SyncStatus.HEALTHY, null);
        when(repository.findById(1L)).thenReturn(initial);
        // 模拟 MyBatis-Plus 乐观锁行为：每次 update 都返回带新 version 的对象
        when(repository.update(any())).thenAnswer(inv -> {
            KbRepository in = inv.getArgument(0);
            return KbRepository.reconstruct(
                in.getId(), in.getName(), in.getRemoteUrl(), in.getBranch(), in.getLocalPath(),
                in.getAuthType(), in.getAuthSecret(), in.getPollIntervalMs(),
                in.getSyncStatus(), in.getLastSyncedCommit(), in.getLastSyncedAt(), in.getLastErrorMessage(),
                in.getVersion() + 1, in.getDeleted(),  // ← version 自增
                in.getCreatedAt(), LocalDateTime.now()
            );
        });
        when(gitWatcher.cloneRepository(any(), any(), any(), any(), any()))
            .thenThrow(new BizException(ErrorCode.KB_GIT_CLONE_FAILED, "clone failed"));

        service.execute(1L);

        // 验证 update 被调用至少 2 次（markSyncing + markError），且每次入参的 sync_status 正确
        ArgumentCaptor<KbRepository> captor = ArgumentCaptor.forClass(KbRepository.class);
        verify(repository, atLeast(2)).update(captor.capture());
        List<KbRepository> updates = captor.getAllValues();
        assertThat(updates).extracting(KbRepository::getSyncStatus)
            .containsSubsequence(SyncStatus.SYNCING, SyncStatus.ERROR);
        // 第二次（markError）入参的 version 必须是初始 +1（来自第一次 update 的返回值），不是初始值
        KbRepository markErrorUpdate = updates.stream()
            .filter(r -> r.getSyncStatus() == SyncStatus.ERROR).findFirst().orElseThrow();
        assertThat(markErrorUpdate.getVersion())
            .as("markError 的 update 入参 version 必须用 markSyncing 返回值的 version，否则会乐观锁失败")
            .isEqualTo(initial.getVersion() + 1);
        assertThat(markErrorUpdate.getLastErrorMessage()).contains("clone failed");
    }
}
