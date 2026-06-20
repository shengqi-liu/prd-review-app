package com.prdreview.knowledgebase.git.model;

/**
 * Markdown 文档变更类型。
 *
 * <p>JGit DiffEntry 的 RENAMED 在本上下文中拆分为 DELETED + ADDED 两条事件，
 * 让下游索引器无需感知重命名复杂度。
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED
}
