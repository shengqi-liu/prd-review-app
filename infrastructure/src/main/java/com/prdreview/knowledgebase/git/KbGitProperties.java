package com.prdreview.knowledgebase.git;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库 Git 同步相关配置（{@code kb.git.*}）。
 *
 * <ul>
 *   <li>{@code pollIntervalMs} — 轮询间隔，默认 1 小时（{@code KbSyncScheduler.@Scheduled} 直接读 placeholder，
 *       此字段仅供其他组件读取参考）</li>
 *   <li>{@code pollInitialDelayMs} — 启动后首次调度延迟，默认 1 分钟</li>
 *   <li>{@code cloneBaseDir} — 本地 clone 根目录</li>
 *   <li>{@code cloneTimeoutMs} — 首次 clone 超时，默认 5 分钟</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.git")
public class KbGitProperties {

    private long pollIntervalMs = 3_600_000L;

    private long pollInitialDelayMs = 60_000L;

    private String cloneBaseDir = "./kb-data";

    private long cloneTimeoutMs = 300_000L;
}
