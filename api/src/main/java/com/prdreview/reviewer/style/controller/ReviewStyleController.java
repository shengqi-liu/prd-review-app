package com.prdreview.reviewer.style.controller;

import com.prdreview.auth.model.UserRole;
import com.prdreview.common.security.CurrentUser;
import com.prdreview.common.security.RequireRole;
import com.prdreview.reviewer.style.CreateReviewStyleCommand;
import com.prdreview.reviewer.style.ReviewStyleDTO;
import com.prdreview.reviewer.style.ReviewStylePageResult;
import com.prdreview.reviewer.style.ReviewStyleQueryCommand;
import com.prdreview.reviewer.style.UpdateReviewStyleCommand;
import com.prdreview.reviewer.style.dto.CreateReviewStyleRequest;
import com.prdreview.reviewer.style.dto.ReviewStyleResponse;
import com.prdreview.reviewer.style.dto.StyleRuleRequest;
import com.prdreview.reviewer.style.dto.UpdateReviewStyleRequest;
import com.prdreview.reviewer.style.model.StyleRule;
import com.prdreview.reviewer.style.service.ReviewStyleApplicationService;
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

import java.util.List;

/**
 * 评审风格资源 Controller。
 *
 * <p>所有接口需登录（JWT 拦截器保证）；写操作仅 ADMIN，读操作所有登录用户。
 */
@Slf4j
@Tag(name = "ReviewStyle", description = "评审风格管理")
@RestController
@RequestMapping("/api/v1/review-styles")
@RequiredArgsConstructor
public class ReviewStyleController {

    private final ReviewStyleApplicationService styleService;

    // ── 5.3 创建（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "创建评审风格（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping
    public ReviewStyleResponse create(@Valid @RequestBody CreateReviewStyleRequest req) {
        ReviewStyleDTO dto = styleService.create(new CreateReviewStyleCommand(
            req.name(), req.icon(), req.scenario(), toDomainRules(req.rules()), req.sortOrder()
        ));
        return ReviewStyleResponse.from(dto);
    }

    // ── 5.4 更新（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "更新评审风格（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PutMapping("/{id}")
    public ReviewStyleResponse update(@PathVariable Long id,
                                      @Valid @RequestBody UpdateReviewStyleRequest req) {
        ReviewStyleDTO dto = styleService.update(new UpdateReviewStyleCommand(
            id, req.name(), req.icon(), req.scenario(), toDomainRules(req.rules()),
            req.enabled(), req.sortOrder(), req.version()
        ));
        return ReviewStyleResponse.from(dto);
    }

    // ── 5.5 删除（ADMIN） ─────────────────────────────────────────────

    @Operation(summary = "删除评审风格（逻辑删除，仅 ADMIN，默认风格受保护）")
    @RequireRole(UserRole.ADMIN)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        styleService.delete(id);
    }

    // ── 5.6 设为默认（ADMIN） ─────────────────────────────────────────

    @Operation(summary = "原子切换默认评审风格（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/{id}/set-default")
    public ReviewStyleResponse setDefault(@PathVariable Long id) {
        return ReviewStyleResponse.from(styleService.setDefault(id));
    }

    // ── 5.7 详情（所有登录用户） ─────────────────────────────────────

    @Operation(summary = "查看评审风格详情")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping("/{id}")
    public ReviewStyleResponse getById(@PathVariable Long id) {
        return ReviewStyleResponse.from(styleService.getById(id));
    }

    // ── 5.8 分页列表（所有登录用户） ────────────────────────────────

    @Operation(summary = "分页查询评审风格列表（非 ADMIN 仅可见 enabled=true）")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping
    public PageResponse list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Boolean enabled
    ) {
        String role = CurrentUser.getCurrentUserRole();
        ReviewStylePageResult result = styleService.listStyles(
            new ReviewStyleQueryCommand(page, size, enabled, role)
        );
        List<ReviewStyleResponse> items = result.items().stream()
            .map(ReviewStyleResponse::from)
            .toList();
        return new PageResponse(result.total(), items);
    }

    // ── 内部辅助 ────────────────────────────────────────────────────

    private List<StyleRule> toDomainRules(List<StyleRuleRequest> requests) {
        if (requests == null) return List.of();
        return requests.stream()
            .map(r -> new StyleRule(r.label(), r.content()))
            .toList();
    }

    public record PageResponse(long total, List<ReviewStyleResponse> items) {}
}
