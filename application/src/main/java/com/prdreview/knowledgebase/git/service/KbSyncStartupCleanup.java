package com.prdreview.knowledgebase.git.service;

import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时清理上次进程残留的 SYNCING 状态。
 *
 * <p>背景：{@code KbSyncTaskService.execute()} 在 {@code markSyncing} 之后、{@code markHealthy/markError}
 * 之前如果 JVM crash / kill -9 / 容器重启，DB 会停在 {@code sync_status='SYNCING'}。后续 {@code @Scheduled}
 * 调度因状态机保护"SYNCING 时跳过本轮"全部跳过，需要手工 SQL 才能恢复。
 *
 * <p>本类通过 {@link ApplicationRunner}（在 Spring 上下文完全就绪后被调用）把所有残留 SYNCING 一次性
 * 标为 ERROR，让下次调度能正常重试。
 *
 * <p>设计来源：{@code fix-kb-sync-stuck-recovery} change（替代过度设计的 watchdog 方案）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbSyncStartupCleanup implements ApplicationRunner {

    private static final String CLEANUP_MESSAGE =
        "startup cleanup: stale SYNCING from previous shutdown/crash";

    private final KbRepositoryRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<KbRepository> stuck = repository.findAllSyncing();
        for (KbRepository repo : stuck) {
            repo.markError(CLEANUP_MESSAGE);
            repository.update(repo);
            log.warn("[KB-Startup] cleared stale SYNCING id={} name={}",
                repo.getId(), repo.getName());
        }
        if (!stuck.isEmpty()) {
            log.info("[KB-Startup] cleaned {} stale SYNCING repository(s)", stuck.size());
        }
    }
}
