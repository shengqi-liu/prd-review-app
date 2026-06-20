package com.prdreview.reviewer.style;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.model.StyleRule;
import com.prdreview.reviewer.style.repository.ReviewStyleRepository;
import com.prdreview.reviewer.style.service.ReviewStyleApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewStyleApplicationService 单元测试（Mockito，无 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewStyleApplicationService 单元测试")
class ReviewStyleApplicationServiceTest {

    @Mock
    private ReviewStyleRepository repository;

    @InjectMocks
    private ReviewStyleApplicationService service;

    // ── 辅助 ──────────────────────────────────────────────────────────

    private static List<StyleRule> rules(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new StyleRule("L" + i, "C" + i))
            .toList();
    }

    private ReviewStyle existing(Long id, String name, boolean enabled, boolean isDefault) {
        return ReviewStyle.reconstruct(id, name, "📋", "scn", rules(4),
            enabled, isDefault, 0, 1, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    // ── 6.2 create ────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 成功保存并返回 DTO（isDefault 强制为 false）")
    void create_success() {
        when(repository.existsByName("风格 A", null)).thenReturn(false);
        when(repository.save(any(ReviewStyle.class)))
            .thenAnswer(inv -> {
                ReviewStyle in = inv.getArgument(0);
                return ReviewStyle.reconstruct(100L, in.getName(), in.getIcon(), in.getScenario(),
                    in.getRules(), in.getEnabled(), in.getIsDefault(), in.getSortOrder(),
                    in.getVersion(), 0, LocalDateTime.now(), LocalDateTime.now());
            });

        ReviewStyleDTO dto = service.create(new CreateReviewStyleCommand(
            "风格 A", "🔧", "适用场景", rules(4), 10
        ));
        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.isDefault()).isFalse();
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.rules()).hasSize(4);
    }

    @Test
    @DisplayName("create — name 为空抛 PARAM_INVALID")
    void create_blankName() {
        assertThatThrownBy(() -> service.create(new CreateReviewStyleCommand(
            "", "🔧", "scn", rules(4), 0)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.PARAM_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create — 名称重复抛 DATA_CONFLICT")
    void create_duplicateName() {
        when(repository.existsByName("dup", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateReviewStyleCommand(
            "dup", "🔧", "scn", rules(4), 0)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.DATA_CONFLICT);
    }

    @Test
    @DisplayName("create — 规则非法抛 STYLE_RULE_INVALID")
    void create_invalidRules() {
        when(repository.existsByName(any(), isNull())).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CreateReviewStyleCommand(
            "X", "🔧", "scn", rules(3), 0)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_RULE_INVALID);
    }

    // ── 6.3 update ────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 成功更新，isDefault 保持原值不变")
    void update_success() {
        ReviewStyle current = existing(1L, "old", true, true);
        when(repository.findById(1L)).thenReturn(current);
        when(repository.existsByName("new", 1L)).thenReturn(false);

        service.update(new UpdateReviewStyleCommand(
            1L, "new", "📋", "newScn", rules(5), true, 5, 1
        ));
        verify(repository).update(any(ReviewStyle.class));
    }

    @Test
    @DisplayName("update — 不存在抛 STYLE_NOT_FOUND")
    void update_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(new UpdateReviewStyleCommand(
            99L, "n", "📋", "s", rules(4), true, 0, 1)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_NOT_FOUND);
    }

    @Test
    @DisplayName("update — 名称冲突（排除自身）抛 DATA_CONFLICT")
    void update_nameConflict() {
        when(repository.findById(1L)).thenReturn(existing(1L, "old", true, false));
        when(repository.existsByName("dup", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(new UpdateReviewStyleCommand(
            1L, "dup", "📋", "s", rules(4), true, 0, 1)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.DATA_CONFLICT);
    }

    @Test
    @DisplayName("update — 默认风格尝试禁用抛 STYLE_DEFAULT_NOT_DISABLABLE")
    void update_defaultCannotDisable() {
        when(repository.findById(1L)).thenReturn(existing(1L, "标准", true, true));
        when(repository.existsByName("标准", 1L)).thenReturn(false);
        assertThatThrownBy(() -> service.update(new UpdateReviewStyleCommand(
            1L, "标准", "📋", "s", rules(4), false, 0, 1)))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_DEFAULT_NOT_DISABLABLE);
    }

    // ── 6.4 delete ────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — 非默认风格成功")
    void delete_success() {
        when(repository.findById(1L)).thenReturn(existing(1L, "X", true, false));
        service.delete(1L);
        verify(repository).softDelete(1L);
    }

    @Test
    @DisplayName("delete — 默认风格抛 STYLE_DEFAULT_NOT_DELETABLE")
    void delete_defaultRejected() {
        when(repository.findById(1L)).thenReturn(existing(1L, "标准", true, true));
        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_DEFAULT_NOT_DELETABLE);
        verify(repository, never()).softDelete(any());
    }

    @Test
    @DisplayName("delete — 不存在抛 STYLE_NOT_FOUND")
    void delete_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_NOT_FOUND);
    }

    // ── 6.5 getById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getById — 存在则返回 DTO")
    void getById_success() {
        when(repository.findById(1L)).thenReturn(existing(1L, "标准", true, true));
        ReviewStyleDTO dto = service.getById(1L);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.isDefault()).isTrue();
    }

    @Test
    @DisplayName("getById — 不存在抛 STYLE_NOT_FOUND")
    void getById_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_NOT_FOUND);
    }

    // ── 6.6 listStyles ────────────────────────────────────────────────

    @Test
    @DisplayName("listStyles — ADMIN 透传 enabled 筛选")
    void list_admin() {
        when(repository.findPageByCondition(1, 20, null))
            .thenReturn(new ReviewStyleRepository.ReviewStylePage(0L, List.of()));
        service.listStyles(new ReviewStyleQueryCommand(1, 20, null, "ADMIN"));
        verify(repository).findPageByCondition(1, 20, null);
    }

    @Test
    @DisplayName("listStyles — 非 ADMIN 强制 enabled=true")
    void list_submitter() {
        when(repository.findPageByCondition(1, 20, Boolean.TRUE))
            .thenReturn(new ReviewStyleRepository.ReviewStylePage(0L, List.of()));
        service.listStyles(new ReviewStyleQueryCommand(1, 20, null, "SUBMITTER"));
        verify(repository).findPageByCondition(1, 20, Boolean.TRUE);
    }

    // ── 6.7 setDefault ────────────────────────────────────────────────

    @Test
    @DisplayName("setDefault — 切换成功（清空 + 标记目标）")
    void setDefault_success() {
        ReviewStyle target = existing(2L, "严谨", true, false);
        when(repository.findById(2L)).thenReturn(target);
        service.setDefault(2L);
        verify(repository).clearAllDefaultFlags();
        verify(repository).update(any(ReviewStyle.class));
        assertThat(target.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("setDefault — 目标禁用时抛 PARAM_INVALID")
    void setDefault_disabledTarget() {
        when(repository.findById(2L)).thenReturn(existing(2L, "禁用", false, false));
        assertThatThrownBy(() -> service.setDefault(2L))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.PARAM_INVALID);
        verify(repository, never()).clearAllDefaultFlags();
    }

    @Test
    @DisplayName("setDefault — 目标不存在抛 STYLE_NOT_FOUND")
    void setDefault_notFound() {
        when(repository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.setDefault(99L))
            .isInstanceOf(BizException.class)
            .matches(e -> ((BizException) e).getErrorCode() == ErrorCode.STYLE_NOT_FOUND);
    }
}
