package com.prdreview.knowledgebase.git.jgit;

import com.prdreview.knowledgebase.git.model.AuthType;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * 根据 {@link AuthType} 为 JGit 操作注入凭据。
 *
 * <ul>
 *   <li>{@link AuthType#NONE} — 不注入</li>
 *   <li>{@link AuthType#HTTPS_TOKEN} — UsernamePasswordCredentialsProvider("token", secret)</li>
 *   <li>{@link AuthType#SSH_KEY_PATH} — 依赖 JGit 默认 SshSessionFactory 读取系统 ~/.ssh 配置；
 *       MVP 阶段不显式设置自定义私钥路径，由运维把私钥放在系统默认位置</li>
 * </ul>
 *
 * <p>本类永远不打印 {@code secret} 真实内容。
 */
public final class CredentialFactory {

    private CredentialFactory() {}

    /** 给 TransportCommand（clone/fetch/push 等）注入凭据。 */
    public static <C extends TransportCommand<?, ?>> C configure(C cmd, AuthType authType, String authSecret) {
        if (authType == null || authType == AuthType.NONE) {
            return cmd;
        }
        CredentialsProvider provider = buildProvider(authType, authSecret);
        if (provider != null) {
            cmd.setCredentialsProvider(provider);
        }
        // SSH 走默认 SshSessionFactory，无需额外配置
        return cmd;
    }

    private static CredentialsProvider buildProvider(AuthType authType, String authSecret) {
        if (authType == AuthType.HTTPS_TOKEN) {
            // GitHub / GitLab / 多数 Git 服务的 PAT 习惯：用户名任意 / token 作为密码
            return new UsernamePasswordCredentialsProvider("token", authSecret != null ? authSecret : "");
        }
        return null;
    }
}
