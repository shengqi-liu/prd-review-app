package com.prdreview.knowledgebase.git;

import com.prdreview.knowledgebase.git.model.AuthType;

/**
 * 更新知识库仓库命令。
 */
public record UpdateKbRepositoryCommand(
    Long repositoryId,
    String name,
    String remoteUrl,
    String branch,
    AuthType authType,
    String authSecret,
    Long pollIntervalMs,
    Integer version
) {
}
