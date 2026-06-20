package com.prdreview.knowledgebase.git.jgit;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.ChangeType;
import com.prdreview.knowledgebase.git.model.MarkdownChange;
import com.prdreview.knowledgebase.git.service.GitWatcher;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * JGit 封装：clone / fetch+reset / diff / 删除工作区。
 *
 * <p>日志中永不打印 {@code authSecret}。所有失败按场景映射为 BizException：
 * <ul>
 *   <li>凭据失败 → {@link ErrorCode#KB_GIT_AUTH_FAILED}</li>
 *   <li>clone 失败 → {@link ErrorCode#KB_GIT_CLONE_FAILED}</li>
 *   <li>fetch/reset/diff 失败 → {@link ErrorCode#KB_GIT_PULL_FAILED}</li>
 * </ul>
 */
@Slf4j
@Component
public class GitOperations implements GitWatcher {

    private static final String MD_SUFFIX = ".md";

    /**
     * 首次 clone 仓库到本地路径，返回 HEAD commit hash。
     */
    @Override
    public String cloneRepository(String remoteUrl, String branch, String localPath,
                                  AuthType authType, String authSecret) {
        File dir = new File(localPath);
        log.info("[KB-Git] clone start url={} branch={} path={}", remoteUrl, branch, localPath);
        try {
            // 若目录已存在但不是有效 git 仓库，先清理
            if (dir.exists()) {
                deleteWorkspace(localPath);
            }
            try (Git git = CredentialFactory.configure(
                Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setBranch(branch)
                    .setDirectory(dir),
                authType, authSecret).call()) {
                ObjectId head = git.getRepository().resolve("HEAD");
                if (head == null) {
                    throw new BizException(ErrorCode.KB_GIT_CLONE_FAILED, "clone 后无法解析 HEAD");
                }
                log.info("[KB-Git] clone done head={}", head.getName());
                return head.getName();
            }
        } catch (TransportException ex) {
            log.warn("[KB-Git] clone auth/transport failed: {}", ex.getMessage());
            throw new BizException(ErrorCode.KB_GIT_AUTH_FAILED, "Git 凭据或传输失败：" + ex.getMessage());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[KB-Git] clone failed: {}", ex.getMessage());
            throw new BizException(ErrorCode.KB_GIT_CLONE_FAILED, "Git clone 失败：" + ex.getMessage());
        }
    }

    /**
     * fetch + reset --hard origin/branch，返回新的 HEAD commit hash。
     */
    @Override
    public String fetchAndReset(String localPath, String branch,
                                AuthType authType, String authSecret) {
        File dir = new File(localPath);
        if (!dir.exists() || !new File(dir, ".git").exists()) {
            throw new BizException(ErrorCode.KB_GIT_REPO_NOT_FOUND,
                "本地仓库不存在：" + localPath);
        }
        log.info("[KB-Git] fetch+reset start path={} branch={}", localPath, branch);
        try (Git git = Git.open(dir)) {
            CredentialFactory.configure(git.fetch().setRemoveDeletedRefs(true), authType, authSecret).call();
            // reset --hard origin/<branch>
            git.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef("origin/" + branch)
                .call();
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new BizException(ErrorCode.KB_GIT_PULL_FAILED, "fetch+reset 后无法解析 HEAD");
            }
            log.info("[KB-Git] fetch+reset done head={}", head.getName());
            return head.getName();
        } catch (TransportException ex) {
            log.warn("[KB-Git] fetch auth/transport failed: {}", ex.getMessage());
            throw new BizException(ErrorCode.KB_GIT_AUTH_FAILED, "Git 凭据或传输失败：" + ex.getMessage());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[KB-Git] fetch+reset failed: {}", ex.getMessage());
            throw new BizException(ErrorCode.KB_GIT_PULL_FAILED, "Git pull 失败：" + ex.getMessage());
        }
    }

    /**
     * 计算 {@code oldCommit} → {@code newCommit} 之间所有 .md 文件的变更。
     *
     * <p>{@code oldCommit} 为 null 时返回 newCommit HEAD 下所有 .md 文件，视为 ADDED。
     * <p>RENAMED 类型拆分为 (DELETED oldPath, ADDED newPath)，让下游索引器统一处理。
     */
    @Override
    public List<MarkdownChange> diffMarkdownChanges(String localPath, String oldCommit, String newCommit) {
        File dir = new File(localPath);
        try (Git git = Git.open(dir)) {
            Repository repo = git.getRepository();
            if (oldCommit == null) {
                return listAllMarkdown(repo, newCommit);
            }
            return diffTwoCommits(repo, oldCommit, newCommit);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[KB-Git] diff failed: {}", ex.getMessage());
            throw new BizException(ErrorCode.KB_GIT_PULL_FAILED, "Git diff 失败：" + ex.getMessage());
        }
    }

    /** 递归删除工作区目录（包含 .git 子目录）。 */
    @Override
    public void deleteWorkspace(String localPath) {
        File dir = new File(localPath);
        if (!dir.exists()) return;
        try (var stream = Files.walk(dir.toPath())) {
            stream.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            log.info("[KB-Git] deleted workspace path={}", localPath);
        } catch (IOException ex) {
            log.warn("[KB-Git] delete workspace failed: {}", ex.getMessage());
            // 删除失败不抛 BizException，仅 warn——重 clone 时会再覆盖
        }
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private List<MarkdownChange> listAllMarkdown(Repository repo, String commit) throws IOException {
        List<MarkdownChange> result = new ArrayList<>();
        ObjectId commitId = repo.resolve(commit);
        if (commitId == null) {
            throw new BizException(ErrorCode.KB_GIT_PULL_FAILED, "无法解析 commit: " + commit);
        }
        try (RevWalk rw = new RevWalk(repo)) {
            RevCommit rc = rw.parseCommit(commitId);
            try (TreeWalk tw = new TreeWalk(repo)) {
                tw.addTree(rc.getTree());
                tw.setRecursive(true);
                tw.setFilter(PathSuffixFilter.create(MD_SUFFIX));
                while (tw.next()) {
                    result.add(new MarkdownChange(tw.getPathString(), ChangeType.ADDED));
                }
            }
        }
        return result;
    }

    private List<MarkdownChange> diffTwoCommits(Repository repo, String oldCommit, String newCommit) throws IOException {
        List<MarkdownChange> result = new ArrayList<>();
        ObjectId oldId = repo.resolve(oldCommit);
        ObjectId newId = repo.resolve(newCommit);
        if (oldId == null || newId == null) {
            throw new BizException(ErrorCode.KB_GIT_PULL_FAILED,
                "无法解析 commit: old=" + oldCommit + " new=" + newCommit);
        }
        try (RevWalk rw = new RevWalk(repo);
             ObjectReader reader = repo.newObjectReader();
             DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE)) {

            RevCommit oldRev = rw.parseCommit(oldId);
            RevCommit newRev = rw.parseCommit(newId);

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldRev.getTree().getId());
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newRev.getTree().getId());

            df.setRepository(repo);
            df.setDetectRenames(true);
            List<DiffEntry> entries = df.scan(oldTree, newTree);
            // 开启重命名检测
            RenameDetector rd = new RenameDetector(repo);
            rd.addAll(entries);
            List<DiffEntry> resolved = rd.compute();

            for (DiffEntry e : resolved) {
                switch (e.getChangeType()) {
                    case ADD -> maybeAdd(result, e.getNewPath(), ChangeType.ADDED);
                    case MODIFY -> maybeAdd(result, e.getNewPath(), ChangeType.MODIFIED);
                    case DELETE -> maybeAdd(result, e.getOldPath(), ChangeType.DELETED);
                    case RENAME, COPY -> {
                        // 拆成 DELETED + ADDED
                        maybeAdd(result, e.getOldPath(), ChangeType.DELETED);
                        maybeAdd(result, e.getNewPath(), ChangeType.ADDED);
                    }
                }
            }
        }
        return result;
    }

    private static void maybeAdd(List<MarkdownChange> out, String path, ChangeType type) {
        if (path != null && path.endsWith(MD_SUFFIX)) {
            out.add(new MarkdownChange(path, type));
        }
    }
}
