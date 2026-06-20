package com.prdreview.reviewer.controller;

import com.prdreview.ai.service.AiService;
import com.prdreview.auth.model.UserRole;
import com.prdreview.common.security.CurrentUser;
import com.prdreview.common.security.RequireRole;
import com.prdreview.common.sse.SseEventEmitter;
import com.prdreview.reviewer.CreateReviewerCommand;
import com.prdreview.reviewer.ReviewerDTO;
import com.prdreview.reviewer.ReviewerPageResult;
import com.prdreview.reviewer.RenderedTestPrompt;
import com.prdreview.reviewer.ReviewerQueryCommand;
import com.prdreview.reviewer.TestReviewerCommand;
import com.prdreview.reviewer.UpdateReviewerCommand;
import com.prdreview.reviewer.dto.CreateReviewerRequest;
import com.prdreview.reviewer.dto.ReviewerResponse;
import com.prdreview.reviewer.dto.TestReviewerRequest;
import com.prdreview.reviewer.dto.UpdateReviewerRequest;
import com.prdreview.reviewer.service.ReviewerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;

/**
 * AI 评审员资源 Controller。
 *
 * <p>所有接口需登录（JWT 拦截器保证）；写操作仅 ADMIN，读操作所有登录用户。
 */
@Slf4j
@Tag(name = "Reviewer", description = "AI 评审员管理")
@RestController
@RequestMapping("/api/v1/reviewers")
@RequiredArgsConstructor
public class ReviewerController {

    private static final long TEST_SSE_TIMEOUT_MS = 180_000L; // 3 分钟，覆盖长 prompt + 长输出

    private final ReviewerApplicationService reviewerService;
    private final AiService aiService;

    // ── 5.3 创建（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "创建 AI 评审员（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping
    public ReviewerResponse create(@Valid @RequestBody CreateReviewerRequest req) {
        ReviewerDTO dto = reviewerService.create(
            new CreateReviewerCommand(req.name(), req.icon(), req.description(), req.promptTemplate())
        );
        return ReviewerResponse.from(dto);
    }

    // ── 5.4 更新（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "更新 AI 评审员（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PutMapping("/{id}")
    public ReviewerResponse update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateReviewerRequest req) {
        ReviewerDTO dto = reviewerService.update(
            new UpdateReviewerCommand(
                id, req.name(), req.icon(), req.description(), req.promptTemplate(),
                req.enabled(), req.sortOrder(), req.version()
            )
        );
        return ReviewerResponse.from(dto);
    }

    // ── 5.5 删除（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "删除 AI 评审员（逻辑删除，仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reviewerService.delete(id);
    }

    // ── 5.6 详情（所有登录用户） ─────────────────────────────────────

    @Operation(summary = "查看 AI 评审员详情")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping("/{id}")
    public ReviewerResponse getById(@PathVariable Long id) {
        return ReviewerResponse.from(reviewerService.getById(id));
    }

    // ── 5.7 分页列表（所有登录用户） ────────────────────────────────

    @Operation(summary = "分页查询 AI 评审员列表（非 ADMIN 仅可见 enabled=true）")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping
    public PageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Boolean enabled
    ) {
        String role = CurrentUser.getCurrentUserRole();
        ReviewerPageResult result = reviewerService.listReviewers(
            new ReviewerQueryCommand(page, size, enabled, role)
        );
        List<ReviewerResponse> items = result.items().stream()
            .map(ReviewerResponse::from)
            .toList();
        return new PageResponse(result.total(), items);
    }

    // ── 5.8 试跑评审员 Prompt（ADMIN，SSE 流式） #9 ──────────────────

    @Operation(summary = "试跑评审员 Prompt（仅 ADMIN，SSE 流式返回 AI 输出）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/{id}/test")
    public SseEmitter test(@PathVariable Long id, @Valid @RequestBody TestReviewerRequest req) {
        // 同步阶段：渲染 system / user 两段消息（评审员不存在 / 参数非法在此抛 BizException，由全局拦截器处理）
        RenderedTestPrompt rendered = reviewerService.renderTestPrompt(
            new TestReviewerCommand(id, req.prdTitle(), req.prdContent())
        );

        SseEventEmitter sseEmitter = new SseEventEmitter(TEST_SSE_TIMEOUT_MS);
        SseEmitter emitter = sseEmitter.getEmitter();

        // 异步阶段：订阅 Flux<String>，逐 token 推送 SSE（system=评审员角色，user=被评审的 PRD）
        Disposable subscription = aiService.streamCompletion(rendered.system(), rendered.user())
            .subscribe(
                sseEmitter::sendToken,
                ex -> {
                    log.warn("试跑评审员 id={} 流式调用失败: {}", id, ex.getMessage());
                    sseEmitter.sendError(ex.getMessage());
                },
                () -> {
                    sseEmitter.sendDone(null);
                    sseEmitter.complete();
                }
            );

        // 客户端断开 / 超时 → 取消 Flux 订阅释放资源
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(e -> subscription.dispose());

        return emitter;
    }

    // ── 内部响应类 ────────────────────────────────────────────────────

    public record PageResponse(long total, List<ReviewerResponse> items) {}
}
