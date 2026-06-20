package com.prdreview.knowledgebase.git.service;

import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时拉取知识库仓库（默认 1 小时一次）。
 *
 * <p>调度本身是单线程触发，实际同步通过 {@link KbSyncTaskService#executeAsync} 异步执行；
 * 任务自身在状态机层防并发（SYNCING 时跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbSyncScheduler {

    private final KbRepositoryRepository repository;
    private final KbSyncTaskService syncTaskService;

    @Scheduled(fixedDelayString = "${kb.git.poll-interval-ms:3600000}",
               initialDelayString = "${kb.git.poll-initial-delay-ms:60000}")
    public void schedule() {
        KbRepository repo = repository.findActive();
        if (repo == null) {
            log.debug("[KB-Sync] no repository configured, skip scheduled run");
            return;
        }
        if (repo.getSyncStatus() == SyncStatus.SYNCING) {
            log.debug("[KB-Sync] repository id={} is SYNCING, skip scheduled run", repo.getId());
            return;
        }
        log.info("[KB-Sync] scheduled run dispatched for id={}", repo.getId());
        syncTaskService.executeAsync(repo.getId());
    }
}
