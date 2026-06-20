package com.prdreview.knowledgebase.git.event;

import com.prdreview.knowledgebase.git.model.ChangeType;

/**
 * 知识库文档变更事件。
 *
 * <p>由 {@code KbSyncTaskService} 在每次同步成功后针对每个 .md 变更发布；
 * 由后续 change #12 索引器作为 {@code @EventListener} 消费。
 *
 * @param repositoryId 仓库 id
 * @param path         相对仓库根的 markdown 文件路径
 * @param changeType   {@link ChangeType#ADDED} / {@link ChangeType#MODIFIED} / {@link ChangeType#DELETED}
 * @param commitHash   触发本次事件的新 HEAD commit hash
 */
public record KbDocumentChangedEvent(
    Long repositoryId,
    String path,
    ChangeType changeType,
    String commitHash
) {
}
