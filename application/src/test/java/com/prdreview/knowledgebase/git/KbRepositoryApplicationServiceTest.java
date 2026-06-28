package com.prdreview.knowledgebase.git;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.GitWatcher;
import com.prdreview.knowledgebase.git.service.KbRepositoryApplicationService;
import com.prdreview.knowledgebase.git.service.KbSyncTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KbRepositoryApplicationService 单元测试")
class KbRepositoryApplicationServiceTest {

    @Mock KbRepositoryRepository repository;
    @Mock GitWatcher gitWatcher;
    @Mock KbSyncTaskService syncTaskService;

    @InjectMocks KbRepositoryApplicationService service;

    @BeforeEach
    void initBaseDir() {
        ReflectionTestUtils.setField(service, "cloneBaseDir", "./kb-data");
        // fix-kb-sync-correctness：repository.update 现在返回 KbRepository。让 mock 直传入参当作"自增后"对象。
        lenient().when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        // fix-kb-sync-correctness Bug A：create() 调 TransactionSynchronizationManager.registerSynchronization，
        // 需要先 init 让其处于"事务激活"状态，否则会抛 IllegalStateException
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private KbRepository saved(Long id, String name, SyncStatus status, boolean withSecret) {
        return KbRepository.reconstruct(
            id, name, "https://x/y.git", "main", "/tmp/repo-" + id,
            withSecret ? AuthType.HTTPS_TOKEN : AuthType.NONE,
            withSecret ? "tok" : null,
            3600000L, status, null, null, null,
            1, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("create — 已存在仓库时抛 KB_REPO_ALREADY_CONFIGURED")
    void create_alreadyConfigured() {
        when(repository.existsActive()).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateKbRepositoryCommand(
            "kb", "url", "main", AuthType.NONE, null, null)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.KB_REPO_ALREADY_CONFIGURED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create — name/remoteUrl 为空抛 PARAM_INVALID")
    void create_blankRejected() {
        assertThatThrownBy(() -> service.create(new CreateKbRepositoryCommand(
            "", "url", "main", AuthType.NONE, null, null)))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new CreateKbRepositoryCommand(
            "kb", "", "main", AuthType.NONE, null, null)))
            .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create — 成功保存 + 异步触发首次同步 + DTO mask 凭据")
    void create_success() {
        when(repository.existsActive()).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            KbRepository in = inv.getArgument(0);
            return KbRepository.reconstruct(100L, in.getName(), in.getRemoteUrl(), in.getBranch(),
                in.getLocalPath(), in.getAuthType(), in.getAuthSecret(),
                in.getPollIntervalMs(), in.getSyncStatus(),
                null, null, null, 1, 0, LocalDateTime.now(), LocalDateTime.now());
        });
        when(repository.findById(100L)).thenReturn(saved(100L, "kb", SyncStatus.HEALTHY, true));

        KbRepositoryDTO dto = service.create(new CreateKbRepositoryCommand(
            "kb", "https://x/y.git", "main", AuthType.HTTPS_TOKEN, "tok", null));

        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.authSecretMasked()).isEqualTo("***"); // ADMIN 视角

        // fix-kb-sync-correctness Bug A：executeAsync 必须在事务提交后才调用，
        // 创建瞬间不应调用，需手动触发 afterCommit 才会发起
        verify(syncTaskService, never()).executeAsync(any());

        // 验证注册了一个 TransactionSynchronization，且 afterCommit 会触发 executeAsync
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs)
            .as("create 必须注册一个事务同步回调用于在 afterCommit 触发首次同步")
            .hasSize(1);
        syncs.get(0).afterCommit();
        verify(syncTaskService).executeAsync(100L);

        verify(repository, times(1)).update(any()); // 一次 update 补 localPath
    }

    @Test
    @DisplayName("update — 不存在抛 KB_GIT_REPO_NOT_FOUND")
    void update_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(new UpdateKbRepositoryCommand(
            99L, "n", "u", "main", AuthType.NONE, null, null, 1)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.KB_GIT_REPO_NOT_FOUND);
    }

    @Test
    @DisplayName("update — remoteUrl 变更触发清理本地 + 重置 commit")
    void update_remoteUrlChange_reclone() {
        when(repository.findById(1L))
            .thenReturn(saved(1L, "kb", SyncStatus.HEALTHY, false), saved(1L, "kb2", SyncStatus.HEALTHY, false));

        service.update(new UpdateKbRepositoryCommand(
            1L, "kb2", "https://NEW/y.git", "main", AuthType.NONE, null, null, 1));

        verify(gitWatcher).deleteWorkspace(any());
        verify(repository).update(any());
    }

    @Test
    @DisplayName("delete — 不存在抛 KB_GIT_REPO_NOT_FOUND")
    void delete_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete — 成功：软删 + 清理本地目录")
    void delete_success() {
        when(repository.findById(1L)).thenReturn(saved(1L, "kb", SyncStatus.HEALTHY, false));
        service.delete(1L);
        verify(repository).softDelete(1L);
        verify(gitWatcher).deleteWorkspace("/tmp/repo-1");
    }

    @Test
    @DisplayName("getById — 非 ADMIN 看不到 authSecretMasked")
    void getById_nonAdminMasked() {
        when(repository.findById(1L)).thenReturn(saved(1L, "kb", SyncStatus.HEALTHY, true));
        KbRepositoryDTO dto = service.getById(1L, "SUBMITTER");
        assertThat(dto.authSecretMasked()).isNull();
    }

    @Test
    @DisplayName("getById — ADMIN 看到 ***")
    void getById_adminSeesMasked() {
        when(repository.findById(1L)).thenReturn(saved(1L, "kb", SyncStatus.HEALTHY, true));
        KbRepositoryDTO dto = service.getById(1L, "ADMIN");
        assertThat(dto.authSecretMasked()).isEqualTo("***");
    }

    @Test
    @DisplayName("triggerSync — HEALTHY 时调用异步任务")
    void triggerSync_dispatchesWhenHealthy() {
        when(repository.findById(1L)).thenReturn(saved(1L, "kb", SyncStatus.HEALTHY, false));
        service.triggerSync(1L, "ADMIN");
        verify(syncTaskService).executeAsync(1L);
    }

    @Test
    @DisplayName("triggerSync — 已 SYNCING 时跳过，不再触发")
    void triggerSync_skipsWhenSyncing() {
        when(repository.findById(1L)).thenReturn(saved(1L, "kb", SyncStatus.SYNCING, false));
        service.triggerSync(1L, "ADMIN");
        verify(syncTaskService, never()).executeAsync(any());
    }
}
