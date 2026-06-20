package com.prdreview.reviewer;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.model.Reviewer;
import com.prdreview.reviewer.repository.ReviewerRepository;
import com.prdreview.reviewer.service.ReviewerApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewerApplicationService 单元测试（Mockito，无 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewerApplicationService 单元测试")
class ReviewerApplicationServiceTest {

    @Mock
    private ReviewerRepository reviewerRepository;

    @InjectMocks
    private ReviewerApplicationService reviewerService;

    // ── 辅助 ──────────────────────────────────────────────────────────

    private Reviewer existing(Long id, String name) {
        return Reviewer.reconstruct(id, name, "🤖", "描述", "{{prd_title}}",
            true, 0, 1, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    // ── 7.2 create ────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 成功保存并返回 DTO")
    void create_success() {
        when(reviewerRepository.existsByName("评审员 A", null)).thenReturn(false);
        when(reviewerRepository.save(any(Reviewer.class)))
            .thenAnswer(inv -> {
                Reviewer in = inv.getArgument(0);
                return Reviewer.reconstruct(100L, in.getName(), in.getIcon(), in.getDescription(),
                    in.getPromptTemplate(), in.getEnabled(), in.getSortOrder(),
                    in.getVersion(), 0, LocalDateTime.now(), LocalDateTime.now());
            });

        ReviewerDTO dto = reviewerService.create(
            new CreateReviewerCommand("评审员 A", "🎯", "描述", "{{prd_title}}"));

        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.name()).isEqualTo("评审员 A");
        assertThat(dto.enabled()).isTrue();
        verify(reviewerRepository).save(any(Reviewer.class));
    }

    @Test
    @DisplayName("create — 名称重复抛 DATA_CONFLICT")
    void create_duplicateName() {
        when(reviewerRepository.existsByName("已存在", null)).thenReturn(true);

        assertThatThrownBy(() -> reviewerService.create(
            new CreateReviewerCommand("已存在", "🤖", "描述", "{{prd_title}}")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATA_CONFLICT);

        verify(reviewerRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — 空白 Prompt 模板抛 PARAM_INVALID")
    void create_invalidPromptTemplate() {
        assertThatThrownBy(() -> reviewerService.create(
            new CreateReviewerCommand("评审员 B", "🤖", "描述", "   ")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);

        verify(reviewerRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — name 为空抛 PARAM_INVALID")
    void create_emptyName() {
        assertThatThrownBy(() -> reviewerService.create(
            new CreateReviewerCommand("  ", "🤖", "描述", "{{prd_title}}")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
    }

    // ── 7.3 update ────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 成功更新")
    void update_success() {
        Reviewer existing = existing(1L, "旧名称");
        when(reviewerRepository.findById(1L)).thenReturn(existing).thenReturn(
            Reviewer.reconstruct(1L, "新名称", "🆕", "新描述", "{{prd_content}}",
                false, 5, 2, 0, LocalDateTime.now(), LocalDateTime.now())
        );
        when(reviewerRepository.existsByName("新名称", 1L)).thenReturn(false);

        ReviewerDTO dto = reviewerService.update(new UpdateReviewerCommand(
            1L, "新名称", "🆕", "新描述", "{{prd_content}}", false, 5, 1));

        assertThat(dto.name()).isEqualTo("新名称");
        assertThat(dto.enabled()).isFalse();
        assertThat(dto.sortOrder()).isEqualTo(5);
        verify(reviewerRepository).update(any(Reviewer.class));
    }

    @Test
    @DisplayName("update — 名称冲突（排除自身后仍存在）抛 DATA_CONFLICT")
    void update_nameConflict() {
        when(reviewerRepository.findById(1L)).thenReturn(existing(1L, "原名称"));
        when(reviewerRepository.existsByName("冲突", 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewerService.update(new UpdateReviewerCommand(
            1L, "冲突", "🤖", "描述", "{{prd_title}}", true, 0, 1)))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATA_CONFLICT);

        verify(reviewerRepository, never()).update(any());
    }

    @Test
    @DisplayName("update — 名称未变（同名仅自身）允许更新")
    void update_sameNameAllowed() {
        Reviewer existing = existing(1L, "同名");
        when(reviewerRepository.findById(1L)).thenReturn(existing).thenReturn(existing);
        when(reviewerRepository.existsByName("同名", 1L)).thenReturn(false);

        reviewerService.update(new UpdateReviewerCommand(
            1L, "同名", "🤖", "新描述", "{{prd_title}}", true, 0, 1));

        verify(reviewerRepository).update(any(Reviewer.class));
    }

    @Test
    @DisplayName("update — 不存在抛 REVIEWER_NOT_FOUND")
    void update_notFound() {
        when(reviewerRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewerService.update(new UpdateReviewerCommand(
            999L, "n", "🤖", "d", "{{prd_title}}", true, 0, 1)))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_NOT_FOUND);
    }

    // ── 7.4 delete ────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — 成功执行软删除")
    void delete_success() {
        when(reviewerRepository.findById(1L)).thenReturn(existing(1L, "评审员"));

        reviewerService.delete(1L);

        verify(reviewerRepository).softDelete(1L);
    }

    @Test
    @DisplayName("delete — 不存在抛 REVIEWER_NOT_FOUND")
    void delete_notFound() {
        when(reviewerRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewerService.delete(999L))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_NOT_FOUND);

        verify(reviewerRepository, never()).softDelete(anyLong());
    }

    // ── 7.5 getById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getById — 存在则返回 DTO")
    void getById_found() {
        when(reviewerRepository.findById(1L)).thenReturn(existing(1L, "评审员"));

        ReviewerDTO dto = reviewerService.getById(1L);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("评审员");
    }

    @Test
    @DisplayName("getById — 不存在抛 REVIEWER_NOT_FOUND")
    void getById_notFound() {
        when(reviewerRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewerService.getById(999L))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_NOT_FOUND);
    }

    // ── 7.6 listReviewers ─────────────────────────────────────────────

    @Test
    @DisplayName("listReviewers — ADMIN 不传 enabled 时查全部")
    void list_adminNoFilter() {
        when(reviewerRepository.findPageByCondition(eq(1), eq(20), isNull()))
            .thenReturn(new ReviewerRepository.ReviewerPage(2,
                List.of(existing(1L, "A"), existing(2L, "B"))));

        ReviewerPageResult result = reviewerService.listReviewers(
            new ReviewerQueryCommand(1, 20, null, "ADMIN"));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        verify(reviewerRepository).findPageByCondition(1, 20, null);
    }

    @Test
    @DisplayName("listReviewers — ADMIN 传 enabled=false 时按入参过滤")
    void list_adminWithFilter() {
        when(reviewerRepository.findPageByCondition(eq(1), eq(20), eq(Boolean.FALSE)))
            .thenReturn(new ReviewerRepository.ReviewerPage(0, List.of()));

        reviewerService.listReviewers(
            new ReviewerQueryCommand(1, 20, false, "ADMIN"));

        verify(reviewerRepository).findPageByCondition(1, 20, Boolean.FALSE);
    }

    @Test
    @DisplayName("listReviewers — SUBMITTER 强制 enabled=true，忽略入参")
    void list_submitterForcedEnabled() {
        when(reviewerRepository.findPageByCondition(eq(1), eq(20), eq(Boolean.TRUE)))
            .thenReturn(new ReviewerRepository.ReviewerPage(1, List.of(existing(1L, "A"))));

        // SUBMITTER 即使传 enabled=false，也会被覆盖为 true
        reviewerService.listReviewers(
            new ReviewerQueryCommand(1, 20, false, "SUBMITTER"));

        verify(reviewerRepository).findPageByCondition(1, 20, Boolean.TRUE);
    }

    @Test
    @DisplayName("listReviewers — TEAM_MEMBER 强制 enabled=true")
    void list_teamMemberForcedEnabled() {
        when(reviewerRepository.findPageByCondition(eq(1), eq(20), eq(Boolean.TRUE)))
            .thenReturn(new ReviewerRepository.ReviewerPage(0, List.of()));

        reviewerService.listReviewers(
            new ReviewerQueryCommand(1, 20, null, "TEAM_MEMBER"));

        verify(reviewerRepository).findPageByCondition(1, 20, Boolean.TRUE);
    }

    // ── #9 renderTestPrompt ───────────────────────────────────────────

    @Test
    @DisplayName("renderTestPrompt — system=评审员模板原样，user=格式化后的 PRD")
    void renderTestPrompt_success() {
        Reviewer r = Reviewer.reconstruct(1L, "产品顾问", "🧑‍💼", "desc",
            "你是资深产品经理，请评审 PRD。",
            true, 10, 1, 0, LocalDateTime.now(), LocalDateTime.now());
        when(reviewerRepository.findById(1L)).thenReturn(r);

        RenderedTestPrompt result = reviewerService.renderTestPrompt(
            new TestReviewerCommand(1L, "会员订阅", "支持月付年付"));

        // system 是评审员模板原文，不含 PRD
        assertThat(result.system()).isEqualTo("你是资深产品经理，请评审 PRD。");
        // user 是格式化后的 PRD，含标题与内容
        assertThat(result.user())
            .contains("会员订阅")
            .contains("支持月付年付")
            .doesNotContain("{{prd_title}}");
    }

    @Test
    @DisplayName("renderTestPrompt — 评审员不存在抛 REVIEWER_NOT_FOUND")
    void renderTestPrompt_reviewerNotFound() {
        when(reviewerRepository.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewerService.renderTestPrompt(
            new TestReviewerCommand(999L, "t", "c")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEWER_NOT_FOUND);

        verify(reviewerRepository, never()).save(any());
    }

    @Test
    @DisplayName("renderTestPrompt — prdTitle 为空抛 PARAM_INVALID")
    void renderTestPrompt_emptyTitle() {
        assertThatThrownBy(() -> reviewerService.renderTestPrompt(
            new TestReviewerCommand(1L, "  ", "content")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);

        verify(reviewerRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("renderTestPrompt — prdContent 为空抛 PARAM_INVALID")
    void renderTestPrompt_emptyContent() {
        assertThatThrownBy(() -> reviewerService.renderTestPrompt(
            new TestReviewerCommand(1L, "title", "")))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);

        verify(reviewerRepository, never()).findById(anyLong());
    }
}
