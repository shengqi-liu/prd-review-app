package com.prdreview.knowledgebase.git;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KbRepository 聚合根单元测试")
class KbRepositoryTest {

    @Test
    @DisplayName("create — 默认值")
    void create_defaults() {
        KbRepository r = KbRepository.create("kb", "https://x/y.git", null,
            null, null, null, "/tmp/kb");
        assertThat(r.getBranch()).isEqualTo("main");
        assertThat(r.getAuthType()).isEqualTo(AuthType.NONE);
        assertThat(r.getPollIntervalMs()).isEqualTo(KbRepository.DEFAULT_POLL_INTERVAL_MS);
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.HEALTHY);
        assertThat(r.getVersion()).isEqualTo(1);
        assertThat(r.getDeleted()).isZero();
        assertThat(r.getLastSyncedCommit()).isNull();
    }

    @Test
    @DisplayName("create — pollIntervalMs 为 0 或负数时回退默认")
    void create_invalidPollFallsBack() {
        KbRepository r = KbRepository.create("kb", "url", "dev",
            AuthType.HTTPS_TOKEN, "tok", -1L, "/tmp/kb");
        assertThat(r.getPollIntervalMs()).isEqualTo(KbRepository.DEFAULT_POLL_INTERVAL_MS);
    }

    @Test
    @DisplayName("markSyncing — HEALTHY → SYNCING")
    void markSyncing_fromHealthy() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.SYNCING);
    }

    @Test
    @DisplayName("markSyncing — 重复调用抛 OPERATION_NOT_ALLOWED")
    void markSyncing_alreadySyncingRejected() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        assertThatThrownBy(r::markSyncing)
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("markHealthy — 写入 commit 与时间，清空错误")
    void markHealthy_writesCommit() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        r.markError("boom");
        r.markHealthy("abc123");
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.HEALTHY);
        assertThat(r.getLastSyncedCommit()).isEqualTo("abc123");
        assertThat(r.getLastSyncedAt()).isNotNull();
        assertThat(r.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("markError — 写入错误信息，状态置 ERROR")
    void markError_writesMessage() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        r.markError("network unreachable");
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.ERROR);
        assertThat(r.getLastErrorMessage()).contains("network unreachable");
    }

    @Test
    @DisplayName("markError — 超长消息被截断为 1000 字符")
    void markError_truncates() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        String long2k = "x".repeat(2000);
        r.markError(long2k);
        assertThat(r.getLastErrorMessage()).hasSize(1000);
    }

    @Test
    @DisplayName("needsReclone — remoteUrl 或 branch 任一变更返回 true")
    void needsReclone_logic() {
        KbRepository r = KbRepository.create("kb", "https://a/b.git", "main", null, null, null, "/tmp/kb");
        assertThat(r.needsReclone("https://a/b.git", "main")).isFalse();
        assertThat(r.needsReclone("https://a/b.git", "dev")).isTrue();
        assertThat(r.needsReclone("https://other/x.git", "main")).isTrue();
        // branch 为 null/blank 视为 "main"
        assertThat(r.needsReclone("https://a/b.git", null)).isFalse();
        assertThat(r.needsReclone("https://a/b.git", "")).isFalse();
    }

    @Test
    @DisplayName("authSecretMasked — 已配置返回 ***；未配置返回 null")
    void authSecretMasked() {
        KbRepository configured = KbRepository.create("kb", "url", "main",
            AuthType.HTTPS_TOKEN, "ghp_xxx", null, "/tmp/kb");
        assertThat(configured.authSecretMasked()).isEqualTo("***");

        KbRepository none = KbRepository.create("kb2", "url", "main",
            AuthType.NONE, null, null, "/tmp/kb2");
        assertThat(none.authSecretMasked()).isNull();
    }

    @Test
    @DisplayName("markDeleted — 设置 deleted=1")
    void markDeleted() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markDeleted();
        assertThat(r.getDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("update — 更新字段，syncStatus 保持不变")
    void update_keepsStatus() {
        KbRepository r = KbRepository.create("kb", "url", "main", null, null, null, "/tmp/kb");
        r.markSyncing();
        r.update("new-name", "new-url", "dev", AuthType.HTTPS_TOKEN, "tok", 7200000L);
        assertThat(r.getName()).isEqualTo("new-name");
        assertThat(r.getRemoteUrl()).isEqualTo("new-url");
        assertThat(r.getBranch()).isEqualTo("dev");
        assertThat(r.getAuthType()).isEqualTo(AuthType.HTTPS_TOKEN);
        assertThat(r.getPollIntervalMs()).isEqualTo(7200000L);
        assertThat(r.getSyncStatus()).isEqualTo(SyncStatus.SYNCING); // 未变
    }
}
