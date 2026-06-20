package com.prdreview.prd;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.prd.model.Prd;
import com.prdreview.prd.model.PrdStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prd 聚合根纯领域单元测试（无 Spring 上下文）。
 */
@DisplayName("Prd 聚合根单元测试")
class PrdTest {

    // ── 工厂方法 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("createFromManual — 初始状态为 DRAFT，version=1")
    void createFromManual_initialState() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.getStatus()).isEqualTo(PrdStatus.DRAFT);
        assertThat(prd.getVersion()).isEqualTo(1);
        assertThat(prd.getTitle()).isEqualTo("标题");
        assertThat(prd.getContent()).isEqualTo("内容");
        assertThat(prd.getAuthorId()).isEqualTo(1L);
        assertThat(prd.getId()).isNull();
    }

    @Test
    @DisplayName("createFromUrl — 初始状态为 INITIALIZING，version=1，title/content 为 null")
    void createFromUrl_initialState() {
        Prd prd = Prd.createFromUrl("https://example.com/prd", 2L);
        assertThat(prd.getStatus()).isEqualTo(PrdStatus.INITIALIZING);
        assertThat(prd.getVersion()).isEqualTo(1);
        assertThat(prd.getSourceUrl()).isEqualTo("https://example.com/prd");
        assertThat(prd.getTitle()).isNull();
        assertThat(prd.getContent()).isNull();
    }

    // ── submit() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("submit — DRAFT 状态下成功转为 SUBMITTED")
    void submit_fromDraft_success() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        prd.submit();
        assertThat(prd.getStatus()).isEqualTo(PrdStatus.SUBMITTED);
    }

    @Test
    @DisplayName("submit — INITIALIZING 状态下抛 PRD_OPERATION_NOT_ALLOWED")
    void submit_fromInitializing_throws() {
        Prd prd = Prd.createFromUrl("https://example.com/prd", 1L);
        assertThatThrownBy(prd::submit)
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("submit — SUBMITTED 状态下再次提交抛异常")
    void submit_fromSubmitted_throws() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        prd.submit();
        assertThatThrownBy(prd::submit)
            .isInstanceOf(BizException.class);
    }

    // ── completeInitialization() ─────────────────────────────────────

    @Test
    @DisplayName("completeInitialization — INITIALIZING 成功转为 DRAFT，填充 title/content")
    void completeInitialization_success() {
        Prd prd = Prd.createFromUrl("https://example.com/prd", 1L);
        prd.completeInitialization("AI 标题", "AI 内容摘要");
        assertThat(prd.getStatus()).isEqualTo(PrdStatus.DRAFT);
        assertThat(prd.getTitle()).isEqualTo("AI 标题");
        assertThat(prd.getContent()).isEqualTo("AI 内容摘要");
    }

    @Test
    @DisplayName("completeInitialization — DRAFT 状态下调用抛 PRD_OPERATION_NOT_ALLOWED")
    void completeInitialization_fromDraft_throws() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThatThrownBy(() -> prd.completeInitialization("新标题", "新内容"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    // ── isVisibleTo() ────────────────────────────────────────────────

    @Test
    @DisplayName("isVisibleTo — ADMIN 可见所有 PRD（含 INITIALIZING）")
    void isVisibleTo_admin_canSeeAll() {
        Prd draftPrd = Prd.createFromManual("标题", "内容", 1L);
        Prd initPrd = Prd.createFromUrl("https://example.com/prd", 1L);
        assertThat(draftPrd.isVisibleTo(999L, "ADMIN")).isTrue();
        assertThat(initPrd.isVisibleTo(999L, "ADMIN")).isTrue();
    }

    @Test
    @DisplayName("isVisibleTo — TEAM_MEMBER 可见所有 PRD（含他人的）")
    void isVisibleTo_teamMember_canSeeAll() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isVisibleTo(999L, "TEAM_MEMBER")).isTrue();
    }

    @Test
    @DisplayName("isVisibleTo — SUBMITTER 可见自己的 DRAFT")
    void isVisibleTo_submitter_ownDraft_visible() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isVisibleTo(1L, "SUBMITTER")).isTrue();
    }

    @Test
    @DisplayName("isVisibleTo — SUBMITTER 不可见他人的 DRAFT")
    void isVisibleTo_submitter_othersDraft_notVisible() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isVisibleTo(2L, "SUBMITTER")).isFalse();
    }

    @Test
    @DisplayName("isVisibleTo — SUBMITTER 不可见自己的 INITIALIZING")
    void isVisibleTo_submitter_ownInitializing_notVisible() {
        Prd prd = Prd.createFromUrl("https://example.com/prd", 1L);
        assertThat(prd.isVisibleTo(1L, "SUBMITTER")).isFalse();
    }

    // ── isDeletableBy() ──────────────────────────────────────────────

    @Test
    @DisplayName("isDeletableBy — DRAFT 状态本人可删")
    void isDeletableBy_draftOwner_deletable() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isDeletableBy(1L, "SUBMITTER")).isTrue();
    }

    @Test
    @DisplayName("isDeletableBy — INITIALIZING 状态本人可删")
    void isDeletableBy_initializingOwner_deletable() {
        Prd prd = Prd.createFromUrl("https://example.com/prd", 1L);
        assertThat(prd.isDeletableBy(1L, "SUBMITTER")).isTrue();
    }

    @Test
    @DisplayName("isDeletableBy — SUBMITTED 状态不可删")
    void isDeletableBy_submitted_notDeletable() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        prd.submit();
        assertThat(prd.isDeletableBy(1L, "SUBMITTER")).isFalse();
    }

    @Test
    @DisplayName("isDeletableBy — ADMIN 可删他人的 DRAFT")
    void isDeletableBy_admin_othersDraft_deletable() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isDeletableBy(999L, "ADMIN")).isTrue();
    }

    @Test
    @DisplayName("isDeletableBy — 非本人非 ADMIN 不可删")
    void isDeletableBy_nonOwner_nonAdmin_notDeletable() {
        Prd prd = Prd.createFromManual("标题", "内容", 1L);
        assertThat(prd.isDeletableBy(2L, "SUBMITTER")).isFalse();
    }
}
