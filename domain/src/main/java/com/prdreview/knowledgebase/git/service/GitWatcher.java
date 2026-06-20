package com.prdreview.knowledgebase.git.service;

import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.MarkdownChange;

import java.util.List;

/**
 * Git 操作端口（domain 定义，infrastructure 实现）。
 *
 * <p>所有方法的实现 MUST 在失败时抛出 {@link com.prdreview.common.exception.BizException}：
 * <ul>
 *   <li>凭据失败 → {@code KB_GIT_AUTH_FAILED}</li>
 *   <li>clone 失败 → {@code KB_GIT_CLONE_FAILED}</li>
 *   <li>fetch/pull/diff 失败 → {@code KB_GIT_PULL_FAILED}</li>
 *   <li>本地仓库目录不存在 → {@code KB_GIT_REPO_NOT_FOUND}</li>
 * </ul>
 */
public interface GitWatcher {

    /** 首次 clone 仓库到本地路径，返回 HEAD commit hash。 */
    String cloneRepository(String remoteUrl, String branch, String localPath,
                           AuthType authType, String authSecret);

    /** fetch + reset --hard origin/branch，返回新的 HEAD commit hash。 */
    String fetchAndReset(String localPath, String branch,
                         AuthType authType, String authSecret);

    /**
     * 计算 oldCommit → newCommit 之间所有 .md 文件的变更。
     *
     * @param oldCommit 旧 commit hash；为 null 时返回 newCommit HEAD 下所有 .md 文件视为 ADDED
     */
    List<MarkdownChange> diffMarkdownChanges(String localPath, String oldCommit, String newCommit);

    /** 递归删除本地工作区目录。 */
    void deleteWorkspace(String localPath);
}
