package com.prdreview.knowledgebase.git.jgit;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.KbGitProperties;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.ChangeType;
import com.prdreview.knowledgebase.git.model.MarkdownChange;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基于本地临时 git 仓库的 GitOperations 集成测试。不依赖外部网络。
 *
 * <p>测试拓扑：
 * <pre>
 *   remoteDir (bare repo)  ◀──push──  seedDir (working repo) ──写入 .md/.txt
 *        ▲
 *        │ clone / fetch
 *        │
 *   workDir (被测仓库)
 * </pre>
 */
@DisplayName("GitOperations 集成测试")
class GitOperationsTest {

    private final KbGitProperties properties = new KbGitProperties();
    private final GitOperations git = new GitOperations(properties);

    @TempDir
    Path tmp;

    private Path remoteDir;
    private Path seedDir;
    private Path workDir;
    private Git seedGit;

    @BeforeEach
    void setUp() throws Exception {
        // 1) 创建 bare remote 仓库（默认分支 master，避免 init.defaultBranch 不一致）
        remoteDir = tmp.resolve("remote.git");
        Git.init().setBare(true).setDirectory(remoteDir.toFile()).call().close();

        // 2) 创建 seed 工作仓库，初始 commit + 推送
        seedDir = tmp.resolve("seed");
        seedGit = Git.cloneRepository()
            .setURI(remoteDir.toUri().toString())
            .setDirectory(seedDir.toFile())
            .call();
        writeFile(seedDir, "a.md", "# A");
        writeFile(seedDir, "b.md", "# B");
        writeFile(seedDir, "ignore.txt", "ignore me");
        commitAndPush("init");

        workDir = tmp.resolve("work");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (seedGit != null) seedGit.close();
        // TempDir 会自动清理；显式删 work 防止符号链接残留
        if (workDir != null && Files.exists(workDir)) {
            try (var s = Files.walk(workDir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Test
    @DisplayName("cloneRepository — 拉取后返回 HEAD commit，本地 .git 存在")
    void clone_works() {
        String head = git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);
        assertThat(head).hasSize(40);
        assertThat(workDir.resolve(".git").toFile()).exists();
        assertThat(workDir.resolve("a.md").toFile()).exists();
    }

    @Test
    @DisplayName("diffMarkdownChanges — 首次（oldCommit=null）返回全量 .md 为 ADDED，忽略 .txt")
    void diff_initialFull() {
        String head = git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);
        List<MarkdownChange> changes = git.diffMarkdownChanges(workDir.toString(), null, head);
        assertThat(changes).extracting(MarkdownChange::path)
            .containsExactlyInAnyOrder("a.md", "b.md");
        assertThat(changes).extracting(MarkdownChange::changeType)
            .containsOnly(ChangeType.ADDED);
    }

    @Test
    @DisplayName("fetchAndReset + diff — 区分 ADDED / MODIFIED / DELETED")
    void fetchAndReset_diffTypes() throws Exception {
        String firstHead = git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);

        // 在 seed 仓库中：修改 a.md、删除 b.md、新增 c.md
        writeFile(seedDir, "a.md", "# A v2");
        Files.delete(seedDir.resolve("b.md"));
        writeFile(seedDir, "c.md", "# C");
        commitAndPush("second");

        String newHead = git.fetchAndReset(workDir.toString(), branch(), AuthType.NONE, null);
        assertThat(newHead).isNotEqualTo(firstHead);

        List<MarkdownChange> changes = git.diffMarkdownChanges(workDir.toString(), firstHead, newHead);
        assertThat(changes).extracting(c -> c.path() + ":" + c.changeType().name())
            .containsExactlyInAnyOrder("a.md:MODIFIED", "b.md:DELETED", "c.md:ADDED");
    }

    @Test
    @DisplayName("diffMarkdownChanges — RENAMED 拆分为 DELETED + ADDED")
    void diff_renamedSplit() throws Exception {
        String firstHead = git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);

        // 在 seed 仓库中：把 a.md 改名为 renamed.md（内容不变）
        Files.move(seedDir.resolve("a.md"), seedDir.resolve("renamed.md"));
        commitAndPush("rename");

        String newHead = git.fetchAndReset(workDir.toString(), branch(), AuthType.NONE, null);
        List<MarkdownChange> changes = git.diffMarkdownChanges(workDir.toString(), firstHead, newHead);
        assertThat(changes).extracting(c -> c.path() + ":" + c.changeType().name())
            .contains("a.md:DELETED", "renamed.md:ADDED");
    }

    @Test
    @DisplayName("非 markdown 文件变更被忽略")
    void diff_ignoresNonMarkdown() throws Exception {
        String firstHead = git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);

        writeFile(seedDir, "notes.txt", "plain");
        writeFile(seedDir, "image.png", "fakepng");
        commitAndPush("non-md");

        String newHead = git.fetchAndReset(workDir.toString(), branch(), AuthType.NONE, null);
        List<MarkdownChange> changes = git.diffMarkdownChanges(workDir.toString(), firstHead, newHead);
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("fetchAndReset — 本地仓库不存在时抛 KB_GIT_REPO_NOT_FOUND")
    void fetch_noLocalRepoRejected() {
        assertThatThrownBy(() -> git.fetchAndReset(tmp.resolve("nope").toString(), branch(),
            AuthType.NONE, null))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.KB_GIT_REPO_NOT_FOUND);
    }

    @Test
    @DisplayName("cloneRepository — 无效 URL 抛 KB_GIT_CLONE_FAILED 或 KB_GIT_AUTH_FAILED")
    void clone_invalidUrlFails() {
        Path bad = tmp.resolve("bad");
        assertThatThrownBy(() -> git.cloneRepository("file:///path/does/not/exist.git",
            "main", bad.toString(), AuthType.NONE, null))
            .isInstanceOf(BizException.class)
            .matches(e -> {
                ErrorCode code = ((BizException) e).getErrorCode();
                return code == ErrorCode.KB_GIT_CLONE_FAILED || code == ErrorCode.KB_GIT_AUTH_FAILED;
            });
    }

    @Test
    @DisplayName("deleteWorkspace — 递归删除目录")
    void deleteWorkspace_recursive() throws Exception {
        git.cloneRepository(remoteDir.toUri().toString(), branch(),
            workDir.toString(), AuthType.NONE, null);
        assertThat(workDir.toFile()).exists();
        git.deleteWorkspace(workDir.toString());
        assertThat(workDir.toFile()).doesNotExist();
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** seed 仓库的当前分支（init 默认可能是 master 或 main，运行时取实际值）。 */
    private String branch() {
        try {
            return seedGit.getRepository().getBranch();
        } catch (IOException e) {
            return "master";
        }
    }

    private void writeFile(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    // ──────────────────────────────────────────────────────────
    // fix-kb-sync-stuck-recovery：JGit timeout 集成测试
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("clone 远端不响应触发 setTimeout，必须在 timeout × 5 内抛 BizException")
    void clone_hangServer_timeoutTriggered() throws Exception {
        // 启 ServerSocket 占住端口但不 accept loop —— TCP backlog 收下连接，
        // JGit 客户端 send HTTP 请求后等响应直到 read timeout
        try (ServerSocket noResponse = new ServerSocket(0, 50)) {
            int port = noResponse.getLocalPort();
            properties.setCloneTimeoutMs(2000L); // 2 秒

            File target = tmp.resolve("hangclone").toFile();
            long start = System.currentTimeMillis();
            assertThatThrownBy(() ->
                git.cloneRepository("http://127.0.0.1:" + port + "/r.git",
                    "master", target.getAbsolutePath(), AuthType.NONE, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    // setTimeout 触发 → 包装为 CLONE_FAILED 或 AUTH_FAILED（TransportException）
                    assertThat(be.getErrorCode()).isIn(
                        ErrorCode.KB_GIT_CLONE_FAILED, ErrorCode.KB_GIT_AUTH_FAILED);
                });
            long elapsed = System.currentTimeMillis() - start;

            // 2s timeout × 5 = 10s 余量（JGit 内部包装异常可能再加几秒）
            assertThat(elapsed)
                .as("clone 应在 timeout 范围内返回，否则说明 setTimeout 未生效")
                .isLessThan(10_000L);
        }
    }

    @Test
    @DisplayName("fetch 远端不响应触发 setTimeout，必须在 timeout × 5 内抛 BizException")
    void fetch_hangServer_timeoutTriggered() throws Exception {
        // 先正常 clone 一份，本地有 .git 目录
        File local = tmp.resolve("hangwork").toFile();
        git.cloneRepository("file://" + remoteDir.toAbsolutePath(),
            branch(), local.getAbsolutePath(), AuthType.NONE, null);

        // 改 origin 指向不响应的本地端口
        try (ServerSocket noResponse = new ServerSocket(0, 50)) {
            int port = noResponse.getLocalPort();
            try (Git localGit = Git.open(local)) {
                var cfg = localGit.getRepository().getConfig();
                cfg.setString("remote", "origin", "url",
                    "http://127.0.0.1:" + port + "/r.git");
                cfg.save();
            }

            properties.setFetchTimeoutMs(2000L); // 2 秒
            long start = System.currentTimeMillis();
            assertThatThrownBy(() ->
                git.fetchAndReset(local.getAbsolutePath(), branch(),
                    AuthType.NONE, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isIn(
                        ErrorCode.KB_GIT_PULL_FAILED, ErrorCode.KB_GIT_AUTH_FAILED);
                });
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                .as("fetch 应在 timeout 范围内返回，否则说明 setTimeout 未生效")
                .isLessThan(10_000L);
        }
    }

    private RevCommit commitAndPush(String msg) throws Exception {
        seedGit.add().addFilepattern(".").call();
        // JGit add 不包含删除——单独 update -A
        seedGit.add().addFilepattern(".").setUpdate(true).call();
        PersonIdent who = new PersonIdent("test", "test@example.com");
        RevCommit c = seedGit.commit().setAuthor(who).setCommitter(who).setMessage(msg).call();
        seedGit.push().call();
        return c;
    }
}
