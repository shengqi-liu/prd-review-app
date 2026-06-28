package com.prdreview.knowledgebase.git.service;

import com.prdreview.common.exception.BizException;
import com.prdreview.knowledgebase.git.event.KbDocumentChangedEvent;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.MarkdownChange;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import com.prdreview.knowledgebase.git.service.GitWatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库 Git 同步任务服务。
 *
 * <p>核心用例：clone 或 fetch+reset → diff → 发布 {@link KbDocumentChangedEvent}。
 * 同步任务通过 {@link #executeAsync} 异步触发，避免阻塞 API 或调度线程。
 *
 * <p>状态机：执行前 markSyncing 并立即持久化（让前端能看到 SYNCING）；
 * 成功 → markHealthy(newCommit)；失败 → markError(message)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbSyncTaskService {

    private final KbRepositoryRepository repository;
    private final GitWatcher gitWatcher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 异步触发同步。调度器与立即触发 API 都通过此方法进入。
     */
    @Async("kbSyncExecutor")
    public void executeAsync(Long repositoryId) {
        try {
            execute(repositoryId);
        } catch (Exception ex) {
            // execute 已保证捕获并 markError；这里兜底 log，不再 rethrow
            log.error("[KB-Sync] uncaught exception in async execute id={} {}", repositoryId, ex.getMessage(), ex);
        }
    }

    /**
     * 执行一次同步（同步方法，供测试与同步触发使用）。
     * 内部不抛业务异常——所有错误已转为 markError + 持久化。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long repositoryId) {
        KbRepository repo = repository.findById(repositoryId);
        if (repo == null) {
            log.warn("[KB-Sync] repository not found id={}", repositoryId);
            return;
        }
        if (repo.getSyncStatus() == SyncStatus.SYNCING) {
            log.debug("[KB-Sync] already SYNCING, skip id={}", repositoryId);
            return;
        }
        // 标记 SYNCING 并立即持久化（让前端能看到状态）
        // 注意：update 返回带新 version 的 domain，必须接收，否则后续 markError/markHealthy 的 update 会因乐观锁冲突 0 行静默失败
        repo.markSyncing();
        repo = repository.update(repo);

        String oldCommit = repo.getLastSyncedCommit();
        try {
            String newCommit;
            // 首次同步（lastSyncedCommit 为 null）或本地目录不存在 → clone
            boolean needsClone = (oldCommit == null) || !new java.io.File(repo.getLocalPath(), ".git").exists();
            if (needsClone) {
                newCommit = gitWatcher.cloneRepository(
                    repo.getRemoteUrl(), repo.getBranch(), repo.getLocalPath(),
                    repo.getAuthType(), repo.getAuthSecret());
            } else {
                newCommit = gitWatcher.fetchAndReset(
                    repo.getLocalPath(), repo.getBranch(),
                    repo.getAuthType(), repo.getAuthSecret());
            }
            // 计算变更 + 发布事件
            // 首次 clone 时 oldCommit=null，diffMarkdownChanges 返回全量 ADDED
            List<MarkdownChange> changes = gitWatcher.diffMarkdownChanges(
                repo.getLocalPath(), needsClone ? null : oldCommit, newCommit);
            for (MarkdownChange c : changes) {
                eventPublisher.publishEvent(new KbDocumentChangedEvent(
                    repositoryId, c.path(), c.changeType(), newCommit));
            }
            repo.markHealthy(newCommit);
            repository.update(repo);
            log.info("[KB-Sync] done id={} oldCommit={} newCommit={} changes={}",
                repositoryId, oldCommit, newCommit, changes.size());
        } catch (BizException ex) {
            // 已知业务异常（凭据/clone/pull/diff/repo not found）
            log.warn("[KB-Sync] failed id={} code={} msg={}", repositoryId, ex.getCode(), ex.getMessage());
            repo.markError(ex.getCode() + ": " + ex.getMessage());
            repository.update(repo);
        } catch (Exception ex) {
            log.error("[KB-Sync] unexpected error id={} {}", repositoryId, ex.getMessage(), ex);
            repo.markError("UNEXPECTED: " + ex.getMessage());
            repository.update(repo);
        }
    }
}
