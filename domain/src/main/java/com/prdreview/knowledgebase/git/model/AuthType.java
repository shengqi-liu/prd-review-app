package com.prdreview.knowledgebase.git.model;

/**
 * Git 仓库凭据类型。
 *
 * <ul>
 *   <li>{@link #NONE} — 公开仓库，无需凭据</li>
 *   <li>{@link #HTTPS_TOKEN} — HTTPS + Personal Access Token（推荐）</li>
 *   <li>{@link #SSH_KEY_PATH} — SSH + 服务器本地私钥绝对路径</li>
 * </ul>
 */
public enum AuthType {
    NONE,
    HTTPS_TOKEN,
    SSH_KEY_PATH
}
