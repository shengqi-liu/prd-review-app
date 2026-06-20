package com.prdreview.knowledgebase.git.dto;

import com.prdreview.knowledgebase.git.KbRepositoryDTO;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.SyncStatus;

import java.time.LocalDateTime;

/**
 * 知识库仓库 HTTP 响应 DTO。
 *
 * <p>{@code authSecretMasked}：ADMIN 看到 "***"（如已配置）或 null；
 * 非 ADMIN 永远 null。真实凭据值不出现在响应中。
 */
public record KbRepositoryResponse(
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
    public static KbRepositoryResponse from(KbRepositoryDTO dto) {
        return new KbRepositoryResponse(
            dto.id(), dto.name(), dto.remoteUrl(), dto.branch(), dto.localPath(),
            dto.authType(), dto.authSecretMasked(), dto.pollIntervalMs(),
            dto.syncStatus(), dto.lastSyncedCommit(), dto.lastSyncedAt(), dto.lastErrorMessage(),
            dto.version(), dto.createdAt(), dto.updatedAt()
        );
    }
}
