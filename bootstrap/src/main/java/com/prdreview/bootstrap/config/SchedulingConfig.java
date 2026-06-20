package com.prdreview.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 定时任务 + 异步任务全局配置。
 *
 * <ul>
 *   <li>{@code @EnableScheduling} — 启用 {@code @Scheduled}（KbSyncScheduler 依赖）</li>
 *   <li>{@code @EnableAsync} — 启用 {@code @Async}（KbSyncTaskService.executeAsync 依赖）</li>
 *   <li>{@code kbSyncExecutor} — 专用线程池，避免 KB 同步与其他 @Async 任务相互影响</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    @Bean("kbSyncExecutor")
    public TaskExecutor kbSyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("kb-sync-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}
