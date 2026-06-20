package com.prdreview.knowledgebase.git;

import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.SyncStatus;

import java.time.LocalDateTime;

/**
 * 知识库仓库 Application 层输出 DTO。
 *
 * <p>{@link #authSecretMasked} 字段：ADMIN 视角下若已配置返回 {@code "***"}，否则 null；
 * 非 ADMIN 视角下永远为 null（在 ApplicationService 层处理）。
 * 真实 {@code authSecret} 字段绝不出现在 DTO 中。
 */
public record KbRepositoryDTO(
    Long id,
    String name,
    String remoteUrl,
    String branch,
    String localPath,
    AuthType authType,
    String authSecretMasked,
    Long pollIntervalMs,
    SyncStatus syncStatus,
    String lastSyncedCommit,
    LocalDateTime lastSyncedAt,
    String lastErrorMessage,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
