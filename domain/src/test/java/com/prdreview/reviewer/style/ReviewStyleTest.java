package com.prdreview.reviewer.style;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.model.StyleRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReviewStyle 聚合根纯领域单元测试（无 Spring 上下文）。
 */
@DisplayName("ReviewStyle 聚合根单元测试")
class ReviewStyleTest {

    private static List<StyleRule> rulesOf(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new StyleRule("L" + i, "C" + i))
            .toList();
    }

    // ── 工厂方法 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 默认 enabled=true, isDefault=false, version=1, deleted=0")
    void create_defaultValues() {
        ReviewStyle s = ReviewStyle.create("标准", "📋", "常规", rulesOf(4), 10);
        assertThat(s.getId()).isNull();
        assertThat(s.getEnabled()).isTrue();
        assertThat(s.getIsDefault()).isFalse();
        assertThat(s.getSortOrder()).isEqualTo(10);
        assertThat(s.getVersion()).isEqualTo(1);
        assertThat(s.getDeleted()).isZero();
        assertThat(s.getRules()).hasSize(4);
    }

    @Test
    @DisplayName("create — sortOrder 为空时默认 0")
    void create_nullSortOrder() {
        ReviewStyle s = ReviewStyle.create("标准", null, null, rulesOf(4), null);
        assertThat(s.getSortOrder()).isZero();
    }

    // ── validateRules 边界 ────────────────────────────────────────────

    @Test
    @DisplayName("validateRules — 3 条规则非法")
    void validateRules_tooFew() {
        assertThatThrownBy(() -> ReviewStyle.create("X", "🔧", "scn", rulesOf(3), 0))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    @Test
    @DisplayName("validateRules — 9 条规则非法")
    void validateRules_tooMany() {
        assertThatThrownBy(() -> ReviewStyle.create("X", "🔧", "scn", rulesOf(9), 0))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    @Test
    @DisplayName("validateRules — 4 条和 8 条均合法")
    void validateRules_boundaries() {
        ReviewStyle.create("X", null, null, rulesOf(4), 0);
        ReviewStyle.create("Y", null, null, rulesOf(8), 0);
    }

    @Test
    @DisplayName("validateRules — label 为空非法")
    void validateRules_blankLabel() {
        List<StyleRule> rules = new ArrayList<>(rulesOf(4));
        rules.set(0, new StyleRule("", "content"));
        assertThatThrownBy(() -> ReviewStyle.create("X", null, null, rules, 0))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    @Test
    @DisplayName("validateRules — content 为空非法")
    void validateRules_blankContent() {
        List<StyleRule> rules = new ArrayList<>(rulesOf(4));
        rules.set(1, new StyleRule("label", "   "));
        assertThatThrownBy(() -> ReviewStyle.create("X", null, null, rules, 0))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    // ── update ────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 触发规则校验")
    void update_invokesValidation() {
        ReviewStyle s = ReviewStyle.create("标准", "📋", "常规", rulesOf(4), 10);
        assertThatThrownBy(() -> s.update("新名称", "🔧", "新场景", rulesOf(3), true, 20))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    @Test
    @DisplayName("update — 默认风格不可禁用")
    void update_defaultCannotDisable() {
        ReviewStyle s = ReviewStyle.reconstruct(1L, "标准", "📋", "scn", rulesOf(4),
            true, true, 0, 1, 0, null, null);
        assertThatThrownBy(() -> s.update("标准", "📋", "scn", rulesOf(4), false, 0))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_DEFAULT_NOT_DISABLABLE);
    }

    @Test
    @DisplayName("update — 默认风格保持启用可成功")
    void update_defaultStaysEnabled() {
        ReviewStyle s = ReviewStyle.reconstruct(1L, "标准", "📋", "scn", rulesOf(4),
            true, true, 0, 1, 0, null, null);
        s.update("标准 v2", "📋", "scn2", rulesOf(5), true, 5);
        assertThat(s.getName()).isEqualTo("标准 v2");
        assertThat(s.getRules()).hasSize(5);
        assertThat(s.getIsDefault()).isTrue();
    }

    // ── markDeleted ───────────────────────────────────────────────────

    @Test
    @DisplayName("markDeleted — 非默认风格成功")
    void markDeleted_nonDefault() {
        ReviewStyle s = ReviewStyle.create("X", null, null, rulesOf(4), 0);
        s.markDeleted();
        assertThat(s.getDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("markDeleted — 默认风格抛 STYLE_DEFAULT_NOT_DELETABLE")
    void markDeleted_defaultRejected() {
        ReviewStyle s = ReviewStyle.reconstruct(1L, "标准", "📋", "scn", rulesOf(4),
            true, true, 0, 1, 0, null, null);
        assertThatThrownBy(s::markDeleted)
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_DEFAULT_NOT_DELETABLE);
    }

    // ── markAsDefault / unmarkDefault ─────────────────────────────────

    @Test
    @DisplayName("markAsDefault / unmarkDefault — 切换 isDefault 标记")
    void defaultFlagToggle() {
        ReviewStyle s = ReviewStyle.create("X", null, null, rulesOf(4), 0);
        assertThat(s.getIsDefault()).isFalse();
        s.markAsDefault();
        assertThat(s.getIsDefault()).isTrue();
        s.unmarkDefault();
        assertThat(s.getIsDefault()).isFalse();
    }
}
