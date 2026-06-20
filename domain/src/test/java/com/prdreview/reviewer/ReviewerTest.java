package com.prdreview.reviewer;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.model.Reviewer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reviewer 聚合根纯领域单元测试（无 Spring 上下文）。
 */
@DisplayName("Reviewer 聚合根单元测试")
class ReviewerTest {

    // ── 工厂方法 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 默认 enabled=true、sortOrder=0、version=1、deleted=0")
    void create_defaultValues() {
        Reviewer r = Reviewer.create("产品顾问", "🧑‍💼", "产品视角", "请评审 {{prd_title}}");
        assertThat(r.getId()).isNull();
        assertThat(r.getName()).isEqualTo("产品顾问");
        assertThat(r.getIcon()).isEqualTo("🧑‍💼");
        assertThat(r.getDescription()).isEqualTo("产品视角");
        assertThat(r.getEnabled()).isTrue();
        assertThat(r.getSortOrder()).isZero();
        assertThat(r.getVersion()).isEqualTo(1);
        assertThat(r.getDeleted()).isZero();
    }

    @Test
    @DisplayName("create — icon 允许为空")
    void create_nullIcon() {
        Reviewer r = Reviewer.create("评审员", null, "描述", "{{prd_title}}");
        assertThat(r.getIcon()).isNull();
    }

    // ── Prompt 模板校验（仅非空，不再校验占位符） ────────────────────

    @Test
    @DisplayName("validatePromptTemplate — 纯角色定义模板合法")
    void validate_roleDefinition() {
        Reviewer r = Reviewer.create("名称", "🤖", "描述",
            "你是一名资深产品经理，请从用户视角评审 PRD。");
        assertThat(r.getPromptTemplate()).contains("资深产品经理");
    }

    @Test
    @DisplayName("validatePromptTemplate — 含花括号文本不再被当作非法占位符")
    void validate_braceTextAllowed() {
        // 占位符校验已移除，模板里出现 {{xxx}} 也不会被拒绝
        Reviewer r = Reviewer.create("名称", "🤖", "描述",
            "评审标准 {{anything}} 都允许");
        assertThat(r.getPromptTemplate()).contains("{{anything}}");
    }

    @Test
    @DisplayName("validatePromptTemplate — 空 promptTemplate 抛异常")
    void validate_emptyTemplate() {
        assertThatThrownBy(() ->
            Reviewer.reconstruct(1L, "n", "🤖", "d", "  ",
                true, 0, 1, 0, null, null).validatePromptTemplate())
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_PROMPT_INVALID);
    }

    // ── update 行为 ──────────────────────────────────────────────────

    @Test
    @DisplayName("update — 空模板触发校验异常")
    void update_triggersValidation() {
        Reviewer r = Reviewer.create("名称", "🤖", "描述", "你是产品评审专家");
        assertThatThrownBy(() ->
            r.update("新名称", "🆕", "新描述", "   ", true, 5))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_PROMPT_INVALID);
    }

    @Test
    @DisplayName("update — 字段全部更新（含 icon）")
    void update_allFields() {
        Reviewer r = Reviewer.create("名称", "🤖", "描述", "你是产品评审专家");
        r.update("新名称", "🆕", "新描述", "你是技术评审专家", false, 99);
        assertThat(r.getName()).isEqualTo("新名称");
        assertThat(r.getIcon()).isEqualTo("🆕");
        assertThat(r.getDescription()).isEqualTo("新描述");
        assertThat(r.getPromptTemplate()).isEqualTo("你是技术评审专家");
        assertThat(r.getEnabled()).isFalse();
        assertThat(r.getSortOrder()).isEqualTo(99);
    }

    // ── markDeleted ──────────────────────────────────────────────────

    @Test
    @DisplayName("markDeleted — 设置 deleted=1")
    void markDeleted_setsFlag() {
        Reviewer r = Reviewer.create("名称", "🤖", "描述", "你是产品评审专家");
        assertThat(r.getDeleted()).isZero();
        r.markDeleted();
        assertThat(r.getDeleted()).isEqualTo(1);
    }
}
