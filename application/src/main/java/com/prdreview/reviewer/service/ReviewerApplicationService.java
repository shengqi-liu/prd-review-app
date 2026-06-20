package com.prdreview.reviewer.service;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.CreateReviewerCommand;
import com.prdreview.reviewer.ReviewerDTO;
import com.prdreview.reviewer.ReviewerPageResult;
import com.prdreview.reviewer.RenderedTestPrompt;
import com.prdreview.reviewer.ReviewerQueryCommand;
import com.prdreview.reviewer.TestReviewerCommand;
import com.prdreview.reviewer.UpdateReviewerCommand;
import com.prdreview.reviewer.model.Reviewer;
import com.prdreview.reviewer.repository.ReviewerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 评审员应用服务 — 编排评审员用例。
 *
 * <p>领域规则封装在 {@link Reviewer} 聚合根内（含 Prompt 模板校验）；
 * 本服务负责名称唯一性、权限决策、Repository 编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerApplicationService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ReviewerRepository reviewerRepository;

    // ── 4.3 创建 ───────────────────────────────────────────────────────

    @Transactional
    public ReviewerDTO create(CreateReviewerCommand cmd) {
        if (!StringUtils.hasText(cmd.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (!StringUtils.hasText(cmd.promptTemplate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "promptTemplate 不能为空");
        }
        if (reviewerRepository.existsByName(cmd.name(), null)) {
            throw new BizException(ErrorCode.DATA_CONFLICT, "评审员名称已存在");
        }
        // create 内部触发 validatePromptTemplate()
        Reviewer reviewer = Reviewer.create(cmd.name(), cmd.icon(), cmd.description(), cmd.promptTemplate());
        Reviewer saved = reviewerRepository.save(reviewer);
        log.info("创建评审员 id={} name={}", saved.getId(), cmd.name());
        return toDTO(saved);
    }

    // ── 4.4 更新 ───────────────────────────────────────────────────────

    @Transactional
    public ReviewerDTO update(UpdateReviewerCommand cmd) {
        Reviewer reviewer = requireReviewer(cmd.reviewerId());
        if (reviewerRepository.existsByName(cmd.name(), cmd.reviewerId())) {
            throw new BizException(ErrorCode.DATA_CONFLICT, "评审员名称已存在");
        }
        // 重建为携带前端 version 的领域对象，触发乐观锁 WHERE version=?
        Reviewer toUpdate = Reviewer.reconstruct(
            reviewer.getId(),
            cmd.name(),
            cmd.icon(),
            cmd.description(),
            cmd.promptTemplate(),
            cmd.enabled() != null ? cmd.enabled() : Boolean.TRUE,
            cmd.sortOrder() != null ? cmd.sortOrder() : 0,
            cmd.version(),
            reviewer.getDeleted(),
            reviewer.getCreatedAt(),
            reviewer.getUpdatedAt()
        );
        toUpdate.validatePromptTemplate();
        reviewerRepository.update(toUpdate);
        log.info("更新评审员 id={} name={}", cmd.reviewerId(), cmd.name());
        return toDTO(requireReviewer(cmd.reviewerId()));
    }

    // ── 4.5 删除 ───────────────────────────────────────────────────────

    @Transactional
    public void delete(Long reviewerId) {
        requireReviewer(reviewerId); // 不存在则抛 REVIEWER_NOT_FOUND
        reviewerRepository.softDelete(reviewerId);
        log.info("逻辑删除评审员 id={}", reviewerId);
    }

    // ── 4.6 详情 ───────────────────────────────────────────────────────

    public ReviewerDTO getById(Long reviewerId) {
        return toDTO(requireReviewer(reviewerId));
    }

    // ── #9 试跑：渲染 system / user 两段消息 ───────────────────────────

    /**
     * 根据 reviewerId 和临时 PRD，渲染出 system（评审员角色定义）+ user（被评审的 PRD）两段消息。
     *
     * <p>评审员模板作为 system 原样使用，PRD 由 title + content 格式化为 user 消息。
     * 纯同步操作（查库 + 字符串拼装）。AI 流式调用由 Controller 层异步执行。
     *
     * @throws BizException PARAM_INVALID 若 prdTitle 或 prdContent 为空
     * @throws BizException REVIEWER_NOT_FOUND 若评审员不存在
     */
    public RenderedTestPrompt renderTestPrompt(TestReviewerCommand cmd) {
        if (!StringUtils.hasText(cmd.prdTitle())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "prdTitle 不能为空");
        }
        if (!StringUtils.hasText(cmd.prdContent())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "prdContent 不能为空");
        }
        Reviewer reviewer = requireReviewer(cmd.reviewerId());
        String userMessage = formatPrdAsUserMessage(cmd.prdTitle(), cmd.prdContent());
        log.info("试跑评审员 id={} name={} prdTitle={}", reviewer.getId(), reviewer.getName(), cmd.prdTitle());
        return new RenderedTestPrompt(reviewer.getPromptTemplate(), userMessage);
    }

    /**
     * 把被评审的 PRD 格式化为 user 消息。试跑（#9）与 Prompt Composer（#15）共用此格式。
     */
    public static String formatPrdAsUserMessage(String prdTitle, String prdContent) {
        return """
            请评审以下 PRD：

            # 标题
            %s

            # 内容
            %s""".formatted(prdTitle, prdContent);
    }

    // ── 4.7 分页列表 ──────────────────────────────────────────────────

    public ReviewerPageResult listReviewers(ReviewerQueryCommand cmd) {
        // 非 ADMIN 强制只看 enabled=true 的评审员
        Boolean enabledFilter;
        if (ROLE_ADMIN.equals(cmd.currentUserRole())) {
            enabledFilter = cmd.enabled();
        } else {
            enabledFilter = Boolean.TRUE;
        }

        ReviewerRepository.ReviewerPage page = reviewerRepository
            .findPageByCondition(cmd.page(), cmd.size(), enabledFilter);

        List<ReviewerDTO> items = page.items().stream().map(this::toDTO).toList();
        return new ReviewerPageResult(page.total(), items);
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private Reviewer requireReviewer(Long id) {
        Reviewer r = reviewerRepository.findById(id);
        if (r == null) {
            throw new BizException(ErrorCode.REVIEWER_NOT_FOUND);
        }
        return r;
    }

    private ReviewerDTO toDTO(Reviewer r) {
        return new ReviewerDTO(
            r.getId(),
            r.getName(),
            r.getIcon(),
            r.getDescription(),
            r.getPromptTemplate(),
            r.getEnabled(),
            r.getSortOrder(),
            r.getVersion(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
