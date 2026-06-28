package com.prdreview.knowledgebase.git.service;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.CreateKbRepositoryCommand;
import com.prdreview.knowledgebase.git.KbRepositoryDTO;
import com.prdreview.knowledgebase.git.UpdateKbRepositoryCommand;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.GitWatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

/**
 * 知识库仓库应用服务 — CRUD + 触发同步用例。
 *
 * <p>领域规则封装在 {@link KbRepository} 内（状态机、字段更新）；
 * 本服务负责单仓库约束、本地路径计算、权限角色 mask、Repository 编排、
 * 把"触发同步"委派给 {@link KbSyncTaskService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbRepositoryApplicationService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final KbRepositoryRepository repository;
    private final GitWatcher gitWatcher;
    private final KbSyncTaskService syncTaskService;

    @Value("${kb.git.clone-base-dir:./kb-data}")
    private String cloneBaseDir;

    // ── 创建 ─────────────────────────────────────────────────────────

    @Transactional
    public KbRepositoryDTO create(CreateKbRepositoryCommand cmd) {
        if (!StringUtils.hasText(cmd.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (!StringUtils.hasText(cmd.remoteUrl())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "remoteUrl 不能为空");
        }
        if (repository.existsActive()) {
            throw new BizException(ErrorCode.KB_REPO_ALREADY_CONFIGURED);
        }
        // 先用占位 localPath 保存以获取 id，再补充最终路径
        KbRepository repo = KbRepository.create(
            cmd.name(), cmd.remoteUrl(), cmd.branch(),
            cmd.authType(), cmd.authSecret(), cmd.pollIntervalMs(),
            placeholderPath()
        );
        KbRepository saved = repository.save(repo);
        // 补 localPath 并再次更新
        String finalPath = resolveLocalPath(saved.getId());
        KbRepository withPath = KbRepository.reconstruct(
            saved.getId(), saved.getName(), saved.getRemoteUrl(), saved.getBranch(),
            finalPath, saved.getAuthType(), saved.getAuthSecret(),
            saved.getPollIntervalMs(), saved.getSyncStatus(),
            saved.getLastSyncedCommit(), saved.getLastSyncedAt(), saved.getLastErrorMessage(),
            saved.getVersion(), saved.getDeleted(),
            saved.getCreatedAt(), saved.getUpdatedAt()
        );
        repository.update(withPath);
        log.info("[KB-Repo] created id={} name={} remoteUrl={}", saved.getId(), saved.getName(), saved.getRemoteUrl());
        // 异步触发首次同步：必须在事务提交后才发起，否则 @Async 抢跑会找不到刚 save 的记录
        // （fix-kb-sync-correctness Bug A 修复）
        Long savedId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncTaskService.executeAsync(savedId);
            }
        });
        return toDTO(repository.findById(saved.getId()), true);
    }

    // ── 更新 ─────────────────────────────────────────────────────────

    @Transactional
    public KbRepositoryDTO update(UpdateKbRepositoryCommand cmd) {
        KbRepository existing = requireRepo(cmd.repositoryId());
        boolean needsReclone = existing.needsReclone(cmd.remoteUrl(), cmd.branch());
        // 重建为携带前端 version 的领域对象，触发乐观锁 WHERE version=?
        KbRepository toUpdate = KbRepository.reconstruct(
            existing.getId(), cmd.name(), cmd.remoteUrl(),
            (cmd.branch() != null && !cmd.branch().isBlank()) ? cmd.branch() : "main",
            existing.getLocalPath(),
            cmd.authType() != null ? cmd.authType() : AuthType.NONE,
            cmd.authSecret(),
            (cmd.pollIntervalMs() != null && cmd.pollIntervalMs() > 0)
                ? cmd.pollIntervalMs() : KbRepository.DEFAULT_POLL_INTERVAL_MS,
            existing.getSyncStatus(),
            existing.getLastSyncedCommit(),
            existing.getLastSyncedAt(),
            existing.getLastErrorMessage(),
            cmd.version(),
            existing.getDeleted(),
            existing.getCreatedAt(),
            existing.getUpdatedAt()
        );
        if (needsReclone) {
            // remoteUrl/branch 变更：清理本地目录、重置 commit，让下次同步走 clone
            gitWatcher.deleteWorkspace(existing.getLocalPath());
            KbRepository reset = KbRepository.reconstruct(
                toUpdate.getId(), toUpdate.getName(), toUpdate.getRemoteUrl(), toUpdate.getBranch(),
                toUpdate.getLocalPath(), toUpdate.getAuthType(), toUpdate.getAuthSecret(),
                toUpdate.getPollIntervalMs(), SyncStatus.HEALTHY,
                null, null, null,
                toUpdate.getVersion(), toUpdate.getDeleted(),
                toUpdate.getCreatedAt(), toUpdate.getUpdatedAt()
            );
            repository.update(reset);
            log.info("[KB-Repo] updated id={} (re-clone scheduled)", cmd.repositoryId());
        } else {
            repository.update(toUpdate);
            log.info("[KB-Repo] updated id={}", cmd.repositoryId());
        }
        return toDTO(repository.findById(cmd.repositoryId()), true);
    }

    // ── 删除 ─────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long repositoryId) {
        KbRepository repo = requireRepo(repositoryId);
        repo.markDeleted();
        repository.softDelete(repositoryId);
        gitWatcher.deleteWorkspace(repo.getLocalPath());
        log.info("[KB-Repo] deleted id={} (workspace cleaned)", repositoryId);
    }

    // ── 查询 ─────────────────────────────────────────────────────────

    public KbRepositoryDTO getById(Long repositoryId, String currentUserRole) {
        return toDTO(requireRepo(repositoryId), ROLE_ADMIN.equals(currentUserRole));
    }

    public List<KbRepositoryDTO> listRepositories(String currentUserRole) {
        boolean admin = ROLE_ADMIN.equals(currentUserRole);
        return repository.findAll().stream().map(r -> toDTO(r, admin)).toList();
    }

    // ── 触发立即同步 ──────────────────────────────────────────────────

    /**
     * 触发立即同步。若仓库已在 SYNCING 直接返回当前状态，不重复触发。
     */
    public KbRepositoryDTO triggerSync(Long repositoryId, String currentUserRole) {
        KbRepository repo = requireRepo(repositoryId);
        if (repo.getSyncStatus() == SyncStatus.SYNCING) {
            log.info("[KB-Repo] triggerSync skipped (already SYNCING) id={}", repositoryId);
            return toDTO(repo, ROLE_ADMIN.equals(currentUserRole));
        }
        syncTaskService.executeAsync(repositoryId);
        log.info("[KB-Repo] triggerSync dispatched id={}", repositoryId);
        return toDTO(repo, ROLE_ADMIN.equals(currentUserRole));
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private KbRepository requireRepo(Long id) {
        KbRepository r = repository.findById(id);
        if (r == null) {
            throw new BizException(ErrorCode.KB_GIT_REPO_NOT_FOUND);
        }
        return r;
    }

    /** 占位本地路径（保存后会被替换为真实路径，再 update 一次） */
    private String placeholderPath() {
        return new File(cloneBaseDir, ".pending").getAbsolutePath();
    }

    /** 解析最终本地路径：{base-dir}/repo-{id} */
    private String resolveLocalPath(Long id) {
        return new File(cloneBaseDir, "repo-" + id).getAbsolutePath();
    }

    private KbRepositoryDTO toDTO(KbRepository r, boolean asAdmin) {
        String maskedSecret = null;
        if (asAdmin) {
            maskedSecret = r.authSecretMasked();
        }
        return new KbRepositoryDTO(
            r.getId(),
            r.getName(),
            r.getRemoteUrl(),
            r.getBranch(),
            r.getLocalPath(),
            r.getAuthType(),
            maskedSecret,
            r.getPollIntervalMs(),
            r.getSyncStatus(),
            r.getLastSyncedCommit(),
            r.getLastSyncedAt(),
            r.getLastErrorMessage(),
            r.getVersion(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
