package com.prdreview.knowledgebase.git;

import com.prdreview.knowledgebase.git.model.AuthType;

/**
 * 创建知识库仓库命令。
 */
public record CreateKbRepositoryCommand(
    String name,
    String remoteUrl,
    String branch,
    AuthType authType,
    String authSecret,
    Long pollIntervalMs
) {
}
