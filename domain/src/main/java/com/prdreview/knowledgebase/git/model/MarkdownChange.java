package com.prdreview.knowledgebase.git.model;

/**
 * Markdown 文件变更条目（GitWatcher.diffMarkdownChanges 返回类型）。
 *
 * @param path       仓库内相对路径
 * @param changeType ADDED / MODIFIED / DELETED（RENAMED 已在 watcher 实现层拆分）
 */
public record MarkdownChange(String path, ChangeType changeType) {
}
