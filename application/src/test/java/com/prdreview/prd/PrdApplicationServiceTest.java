package com.prdreview.prd;

import com.prdreview.ai.dto.SummarizeResult;
import com.prdreview.ai.service.AiService;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.prd.model.Prd;
import com.prdreview.prd.model.PrdStatus;
import com.prdreview.prd.repository.PrdRepository;
import com.prdreview.prd.repository.PrdVersionRepository;
import com.prdreview.prd.service.PrdApplicationService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrdApplicationService 单元测试（Mockito，无 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrdApplicationService 单元测试")
class PrdApplicationServiceTest {

    @Mock
    private PrdRepository prdRepository;
    @Mock
    private PrdVersionRepository prdVersionRepository;
    @Mock
    private AiService aiService;

    @InjectMocks
    private PrdApplicationService prdService;

    // ── 辅助方法 ─────────────────────────────────────────────────────

    private Prd draftPrd(Long id, Long authorId) {
        return Prd.reconstruct(id, "标题", "内容", null, authorId,
            PrdStatus.DRAFT, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    private Prd initializingPrd(Long id, Long authorId) {
        return Prd.reconstruct(id, null, null, "https://example.com/doc", authorId,
            PrdStatus.INITIALIZING, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    private Prd submittedPrd(Long id, Long authorId) {
        return Prd.reconstruct(id, "标题", "内容", null, authorId,
            PrdStatus.SUBMITTED, 2, LocalDateTime.now(), LocalDateTime.now());
    }

    // ── 7.2 createManual ─────────────────────────────────────────────

    @Test
    @DisplayName("createManual — 成功创建草稿")
    void createManual_success() {
        Prd saved = draftPrd(1L, 10L);
        when(prdRepository.save(any())).thenReturn(saved);

        PrdDTO dto = prdService.createManual(new CreatePrdCommand("标题", "内容", 10L));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.status()).isEqualTo("DRAFT");
        verify(prdRepository).save(any());
    }

    @Test
    @DisplayName("createManual — title 为空时抛 PARAM_INVALID")
    void createManual_emptyTitle_throws() {
        assertThatThrownBy(() -> prdService.createManual(new CreatePrdCommand("", "内容", 10L)))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("createManual — content 为空时抛 PARAM_INVALID")
    void createManual_emptyContent_throws() {
        assertThatThrownBy(() -> prdService.createManual(new CreatePrdCommand("标题", "", 10L)))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    // ── 7.3 createFromUrl → completeInitialization ───────────────────

    @Test
    @DisplayName("createFromUrl + completeInitializationFromUrl — 完整 URL 路径流程")
    void createFromUrl_thenCompleteInitialization_fullFlow() {
        Prd saved = initializingPrd(1L, 10L);
        when(prdRepository.save(any())).thenReturn(saved);
        when(aiService.summarizeFromUrl("https://example.com/doc"))
            .thenReturn(new SummarizeResult("AI 标题", "AI 内容"));
        when(prdRepository.findById(1L)).thenReturn(saved);

        // Step 1: createFromUrl
        Long prdId = prdService.createFromUrl(new CreatePrdFromUrlCommand("https://example.com/doc", 10L));
        assertThat(prdId).isEqualTo(1L);

        // Step 2: completeInitializationFromUrl (called by SSE async thread)
        PrdDTO dto = prdService.completeInitializationFromUrl(1L, "https://example.com/doc");
        assertThat(dto.status()).isEqualTo("DRAFT");
        verify(aiService).summarizeFromUrl("https://example.com/doc");
        verify(prdRepository).update(any());
    }

    // ── 7.4 getById 可见性 ────────────────────────────────────────────

    @Test
    @DisplayName("getById — 本人 SUBMITTER 可见自己的 DRAFT")
    void getById_ownerSubmitter_visible() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        PrdDTO dto = prdService.getById(1L, 10L, "SUBMITTER");
        assertThat(dto.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById — SUBMITTER 不可见他人 PRD，抛 PRD_NOT_FOUND")
    void getById_submitter_others_throws() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.getById(1L, 99L, "SUBMITTER"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_NOT_FOUND));
    }

    @Test
    @DisplayName("getById — ADMIN 可见所有 PRD")
    void getById_admin_visible() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        PrdDTO dto = prdService.getById(1L, 999L, "ADMIN");
        assertThat(dto.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById — SUBMITTER 不可见自己的 INITIALIZING，抛 PRD_NOT_FOUND")
    void getById_submitter_ownInitializing_throws() {
        when(prdRepository.findById(1L)).thenReturn(initializingPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.getById(1L, 10L, "SUBMITTER"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_NOT_FOUND));
    }

    // ── 7.5 updateDraft ───────────────────────────────────────────────

    @Test
    @DisplayName("updateDraft — 成功更新草稿")
    void updateDraft_success() {
        Prd draft = draftPrd(1L, 10L);
        when(prdRepository.findById(1L)).thenReturn(draft);

        prdService.updateDraft(new UpdatePrdCommand(1L, "新标题", "新内容", 1, 10L));
        verify(prdRepository).update(any());
    }

    @Test
    @DisplayName("updateDraft — 非本人操作抛 FORBIDDEN")
    void updateDraft_notOwner_throws() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.updateDraft(new UpdatePrdCommand(1L, "新标题", "新内容", 1, 99L)))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("updateDraft — 非 DRAFT 状态抛 PRD_OPERATION_NOT_ALLOWED")
    void updateDraft_notDraft_throws() {
        when(prdRepository.findById(1L)).thenReturn(submittedPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.updateDraft(new UpdatePrdCommand(1L, "新标题", "新内容", 2, 10L)))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    // ── 7.6 softDelete ────────────────────────────────────────────────

    @Test
    @DisplayName("softDelete — 本人删除 DRAFT 成功")
    void softDelete_ownerDraft_success() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        prdService.softDelete(1L, 10L, "SUBMITTER");
        verify(prdRepository).softDelete(1L);
    }

    @Test
    @DisplayName("softDelete — 本人删除 INITIALIZING 成功")
    void softDelete_ownerInitializing_success() {
        when(prdRepository.findById(1L)).thenReturn(initializingPrd(1L, 10L));
        prdService.softDelete(1L, 10L, "SUBMITTER");
        verify(prdRepository).softDelete(1L);
    }

    @Test
    @DisplayName("softDelete — SUBMITTED 状态不可删")
    void softDelete_submitted_throws() {
        when(prdRepository.findById(1L)).thenReturn(submittedPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.softDelete(1L, 10L, "SUBMITTER"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("softDelete — 非本人非 ADMIN 不可删")
    void softDelete_notOwnerNotAdmin_throws() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.softDelete(1L, 99L, "SUBMITTER"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    // ── 7.7 submitPrd ────────────────────────────────────────────────

    @Test
    @DisplayName("submitPrd — 成功提交，创建版本快照（含 sourceUrl）")
    void submitPrd_success_withSnapshot() {
        Prd prdWithUrl = Prd.reconstruct(1L, "标题", "内容", "https://example.com/doc",
            10L, PrdStatus.DRAFT, 1, LocalDateTime.now(), LocalDateTime.now());
        when(prdRepository.findById(1L)).thenReturn(prdWithUrl);

        PrdDTO dto = prdService.submitPrd(1L, 10L);
        assertThat(dto.status()).isEqualTo("SUBMITTED");
        verify(prdRepository).update(any());
        verify(prdVersionRepository).save(any()); // 快照已保存
    }

    @Test
    @DisplayName("submitPrd — INITIALIZING 状态不可提交")
    void submitPrd_initializing_throws() {
        when(prdRepository.findById(1L)).thenReturn(initializingPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.submitPrd(1L, 10L))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PRD_OPERATION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("submitPrd — 非本人操作抛 FORBIDDEN")
    void submitPrd_notOwner_throws() {
        when(prdRepository.findById(1L)).thenReturn(draftPrd(1L, 10L));
        assertThatThrownBy(() -> prdService.submitPrd(1L, 99L))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ── 7.8 listPrds ─────────────────────────────────────────────────

    @Test
    @DisplayName("listPrds — SUBMITTER 只查询自己的（authorId 过滤）")
    void listPrds_submitter_filtersOwnPrds() {
        when(prdRepository.findPageByCondition(eq(1), eq(20), eq(10L), eq(true)))
            .thenReturn(new PrdRepository.PrdPage(1L, List.of(draftPrd(1L, 10L))));

        PrdPageResult result = prdService.listPrds(new PrdQueryCommand(1, 20, 10L, "SUBMITTER"));
        assertThat(result.total()).isEqualTo(1L);
        verify(prdRepository).findPageByCondition(1, 20, 10L, true);
    }

    @Test
    @DisplayName("listPrds — ADMIN 查询全部（authorId=null，不过滤）")
    void listPrds_admin_noAuthorFilter() {
        when(prdRepository.findPageByCondition(eq(1), eq(20), isNull(), eq(true)))
            .thenReturn(new PrdRepository.PrdPage(3L, List.of(
                draftPrd(1L, 10L), draftPrd(2L, 20L), draftPrd(3L, 30L)
            )));

        PrdPageResult result = prdService.listPrds(new PrdQueryCommand(1, 20, 999L, "ADMIN"));
        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.items()).hasSize(3);
        verify(prdRepository).findPageByCondition(1, 20, null, true);
    }

    @Test
    @DisplayName("listPrds — 均排除 INITIALIZING（excludeInitializing=true）")
    void listPrds_excludesInitializing() {
        when(prdRepository.findPageByCondition(anyInt(), anyInt(), any(), eq(true)))
            .thenReturn(new PrdRepository.PrdPage(0L, List.of()));

        prdService.listPrds(new PrdQueryCommand(1, 20, 10L, "SUBMITTER"));
        // 验证 excludeInitializing=true
        verify(prdRepository).findPageByCondition(1, 20, 10L, true);
    }
}
