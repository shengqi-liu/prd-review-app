package com.prdreview.prd.service;

import com.prdreview.ai.service.AiService;
import com.prdreview.ai.dto.SummarizeResult;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.prd.CreatePrdCommand;
import com.prdreview.prd.CreatePrdFromUrlCommand;
import com.prdreview.prd.PrdDTO;
import com.prdreview.prd.PrdPageResult;
import com.prdreview.prd.PrdQueryCommand;
import com.prdreview.prd.UpdatePrdCommand;
import com.prdreview.prd.model.Prd;
import com.prdreview.prd.model.PrdVersion;
import com.prdreview.prd.repository.PrdRepository;
import com.prdreview.prd.repository.PrdVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * PRD 应用服务 — 编排 PRD 用例，不包含领域规则（规则封装在 Prd 聚合根内）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrdApplicationService {

    private final PrdRepository prdRepository;
    private final PrdVersionRepository prdVersionRepository;
    private final AiService aiService;

    // ── 4.3 手动创建草稿 ─────────────────────────────────────────────

    @Transactional
    public PrdDTO createManual(CreatePrdCommand cmd) {
        if (!StringUtils.hasText(cmd.title())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "title 不能为空");
        }
        if (!StringUtils.hasText(cmd.content())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "content 不能为空");
        }
        Prd prd = Prd.createFromManual(cmd.title(), cmd.content(), cmd.authorId());
        Prd saved = prdRepository.save(prd);
        log.info("手动创建 PRD id={} authorId={}", saved.getId(), cmd.authorId());
        return toDTO(saved);
    }

    // ── 4.4 从 URL 创建（INITIALIZING 占位） ─────────────────────────

    @Transactional
    public Long createFromUrl(CreatePrdFromUrlCommand cmd) {
        Prd prd = Prd.createFromUrl(cmd.sourceUrl(), cmd.authorId());
        Prd saved = prdRepository.save(prd);
        log.info("创建 URL 路径 PRD id={} url={}", saved.getId(), cmd.sourceUrl());
        return saved.getId();
    }

    // ── 4.5 完成 AI 初始化（直接提供 title/content） ─────────────────

    @Transactional
    public PrdDTO completeInitialization(Long prdId, String title, String content) {
        Prd prd = requirePrd(prdId);
        prd.completeInitialization(title, content);
        prdRepository.update(prd);
        log.info("PRD id={} 初始化完成 status=DRAFT", prdId);
        return toDTO(prd);
    }

    // ── 4.5b 从 URL 完成 AI 初始化（供 SSE 端点调用） ────────────────

    @Transactional
    public PrdDTO completeInitializationFromUrl(Long prdId, String sourceUrl) {
        SummarizeResult result = aiService.summarizeFromUrl(sourceUrl);
        return completeInitialization(prdId, result.title(), result.content());
    }

    // ── 4.6 按 ID 查询 ────────────────────────────────────────────────

    public PrdDTO getById(Long prdId, Long currentUserId, String currentUserRole) {
        Prd prd = requirePrd(prdId);
        if (!prd.isVisibleTo(currentUserId, currentUserRole)) {
            throw new BizException(ErrorCode.PRD_NOT_FOUND);
        }
        return toDTO(prd);
    }

    // ── 4.7 分页列表 ──────────────────────────────────────────────────

    public PrdPageResult listPrds(PrdQueryCommand cmd) {
        // SUBMITTER 只能看自己的；ADMIN/TEAM_MEMBER 看全部
        Long authorIdFilter = isSubmitter(cmd.currentUserRole()) ? cmd.currentUserId() : null;

        PrdRepository.PrdPage page = prdRepository.findPageByCondition(
            cmd.page(), cmd.size(), authorIdFilter, true
        );

        List<PrdDTO> items = page.items().stream().map(this::toDTO).toList();
        return new PrdPageResult(page.total(), items);
    }

    // ── 4.8 更新草稿 ──────────────────────────────────────────────────

    @Transactional
    public PrdDTO updateDraft(UpdatePrdCommand cmd) {
        Prd prd = requirePrd(cmd.prdId());
        if (!prd.isOwnedBy(cmd.currentUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!prd.isEditable()) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        // 将前端版本号同步到领域对象，触发 MyBatis-Plus 乐观锁 WHERE version=?
        Prd toUpdate = Prd.reconstruct(
            prd.getId(), cmd.title(), cmd.content(), prd.getSourceUrl(),
            prd.getAuthorId(), prd.getStatus(), cmd.version(),
            prd.getCreatedAt(), prd.getUpdatedAt()
        );
        prdRepository.update(toUpdate);
        log.info("更新 PRD 草稿 id={}", cmd.prdId());
        return toDTO(requirePrd(cmd.prdId())); // 重新查询获取 updatedAt
    }

    // ── 4.9 软删除 ────────────────────────────────────────────────────

    @Transactional
    public void softDelete(Long prdId, Long currentUserId, String currentUserRole) {
        Prd prd = requirePrd(prdId);
        if (!prd.isDeletableBy(currentUserId, currentUserRole)) {
            throw new BizException(ErrorCode.PRD_OPERATION_NOT_ALLOWED);
        }
        prdRepository.softDelete(prdId);
        log.info("软删除 PRD id={} by userId={}", prdId, currentUserId);
    }

    // ── 4.10 提交评审 ─────────────────────────────────────────────────

    @Transactional
    public PrdDTO submitPrd(Long prdId, Long currentUserId) {
        Prd prd = requirePrd(prdId);
        if (!prd.isOwnedBy(currentUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        prd.submit(); // DRAFT → SUBMITTED（内部校验状态）
        prdRepository.update(prd);

        // 创建版本快照（含 sourceUrl）
        PrdVersion snapshot = PrdVersion.create(
            prd.getId(), prd.getVersion(),
            prd.getTitle(), prd.getContent(), prd.getSourceUrl()
        );
        prdVersionRepository.save(snapshot);
        log.info("PRD id={} 提交评审，版本快照 version={}", prdId, prd.getVersion());
        return toDTO(prd);
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private Prd requirePrd(Long prdId) {
        Prd prd = prdRepository.findById(prdId);
        if (prd == null) {
            throw new BizException(ErrorCode.PRD_NOT_FOUND);
        }
        return prd;
    }

    private boolean isSubmitter(String role) {
        return "SUBMITTER".equals(role);
    }

    private PrdDTO toDTO(Prd prd) {
        return new PrdDTO(
            prd.getId(),
            prd.getTitle(),
            prd.getContent(),
            prd.getSourceUrl(),
            prd.getAuthorId(),
            prd.getStatus().name(),
            prd.getVersion(),
            prd.getCreatedAt(),
            prd.getUpdatedAt()
        );
    }
}
