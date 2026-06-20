package com.prdreview.knowledgebase.git.model;

/**
 * 知识库仓库同步状态机。
 *
 * <pre>
 *   HEALTHY  ──pull start──▶  SYNCING  ──ok──▶  HEALTHY
 *                                   └──err──▶  ERROR
 *   ERROR    ──pull start──▶  SYNCING
 * </pre>
 */
public enum SyncStatus {
    HEALTHY,
    SYNCING,
    ERROR
}
