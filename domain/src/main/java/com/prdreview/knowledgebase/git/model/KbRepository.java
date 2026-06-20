package com.prdreview.knowledgebase.git.model;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 知识库 Git 仓库聚合根。
 *
 * <p>纯 Java 对象（无 MyBatis 注解）。封装同步状态机与凭据处理，禁止外部直接修改字段。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>{@code syncStatus} 仅通过 {@link #markSyncing()} / {@link #markHealthy(String)}
 *       / {@link #markError(String)} 改变</li>
 *   <li>系统至多 1 个未删除的实例（约束在 Application 层校验）</li>
 * </ul>
 */
@Getter
public class KbRepository {

    /** 默认轮询间隔：1 小时 */
    public static final long DEFAULT_POLL_INTERVAL_MS = 3_600_000L;

    private Long id;
    private String name;
    private String remoteUrl;
    private String branch;
    private String localPath;
    private AuthType authType;
    /** 凭据明文（HTTPS token / SSH 私钥路径），日志永远 mask */
    private String authSecret;
    private Long pollIntervalMs;
    private SyncStatus syncStatus;
    private String lastSyncedCommit;
    private LocalDateTime lastSyncedAt;
    private String lastErrorMessage;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── 静态工厂 ─────────────────────────────────────────────────────

    /**
     * 创建新仓库：默认 syncStatus=HEALTHY、version=1、deleted=0；
     * branch 为空时默认 {@code main}；pollIntervalMs 为空时使用默认值。
     */
    public static KbRepository create(String name, String remoteUrl, String branch,
                                       AuthType authType, String authSecret,
                                       Long pollIntervalMs, String localPath) {
        KbRepository r = new KbRepository();
        r.name = name;
        r.remoteUrl = remoteUrl;
        r.branch = (branch != null && !branch.isBlank()) ? branch : "main";
        r.authType = authType != null ? authType : AuthType.NONE;
        r.authSecret = authSecret;
        r.pollIntervalMs = (pollIntervalMs != null && pollIntervalMs > 0)
            ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
        r.localPath = localPath;
        r.syncStatus = SyncStatus.HEALTHY;
        r.lastSyncedCommit = null;
        r.lastSyncedAt = null;
        r.lastErrorMessage = null;
        r.version = 1;
        r.deleted = 0;
        return r;
    }

    /** 从持久化对象重建（供 Assembler 使用）。 */
    public static KbRepository reconstruct(Long id, String name, String remoteUrl, String branch,
                                            String localPath, AuthType authType, String authSecret,
                                            Long pollIntervalMs, SyncStatus syncStatus,
                                            String lastSyncedCommit, LocalDateTime lastSyncedAt,
                                            String lastErrorMessage, Integer version, Integer deleted,
                                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        KbRepository r = new KbRepository();
        r.id = id;
        r.name = name;
        r.remoteUrl = remoteUrl;
        r.branch = branch;
        r.localPath = localPath;
        r.authType = authType;
        r.authSecret = authSecret;
        r.pollIntervalMs = pollIntervalMs;
        r.syncStatus = syncStatus;
        r.lastSyncedCommit = lastSyncedCommit;
        r.lastSyncedAt = lastSyncedAt;
        r.lastErrorMessage = lastErrorMessage;
        r.version = version;
        r.deleted = deleted;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    // ── 状态机 ────────────────────────────────────────────────────────

    /**
     * 进入 SYNCING 状态（必须从 HEALTHY 或 ERROR 转移过来）。
     */
    public void markSyncing() {
        if (this.syncStatus == SyncStatus.SYNCING) {
            throw new BizException(ErrorCode.OPERATION_NOT_ALLOWED, "仓库已在同步中");
        }
        this.syncStatus = SyncStatus.SYNCING;
    }

    /**
     * 标记同步成功，记录新 commit，清空错误信息。
     */
    public void markHealthy(String commitHash) {
        this.syncStatus = SyncStatus.HEALTHY;
        this.lastSyncedCommit = commitHash;
        this.lastSyncedAt = LocalDateTime.now();
        this.lastErrorMessage = null;
    }

    /**
     * 标记同步失败，保留上一次 lastSyncedCommit，写错误消息。
     */
    public void markError(String message) {
        this.syncStatus = SyncStatus.ERROR;
        // 截断防止超过 1000 字段长度
        this.lastErrorMessage = message != null && message.length() > 1000
            ? message.substring(0, 1000) : message;
    }

    // ── 配置更新 ──────────────────────────────────────────────────────

    /**
     * 更新可变配置字段（不涉及同步状态）。
     */
    public void update(String name, String remoteUrl, String branch,
                       AuthType authType, String authSecret, Long pollIntervalMs) {
        this.name = name;
        this.remoteUrl = remoteUrl;
        this.branch = (branch != null && !branch.isBlank()) ? branch : "main";
        this.authType = authType != null ? authType : AuthType.NONE;
        this.authSecret = authSecret;
        this.pollIntervalMs = (pollIntervalMs != null && pollIntervalMs > 0)
            ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
    }

    /**
     * 判断 remoteUrl 或 branch 是否变更——变更时下次同步需要清理本地目录重 clone。
     */
    public boolean needsReclone(String newRemoteUrl, String newBranch) {
        String normalizedNewBranch = (newBranch != null && !newBranch.isBlank()) ? newBranch : "main";
        return !java.util.Objects.equals(this.remoteUrl, newRemoteUrl)
            || !java.util.Objects.equals(this.branch, normalizedNewBranch);
    }

    public void markDeleted() {
        this.deleted = 1;
    }

    /** 凭据 mask 显示（永不返回真实值）。 */
    public String authSecretMasked() {
        if (this.authSecret == null || this.authSecret.isBlank()) return null;
        return "***";
    }
}
