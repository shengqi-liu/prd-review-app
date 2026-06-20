package com.prdreview.reviewer.style.service;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.style.CreateReviewStyleCommand;
import com.prdreview.reviewer.style.ReviewStyleDTO;
import com.prdreview.reviewer.style.ReviewStylePageResult;
import com.prdreview.reviewer.style.ReviewStyleQueryCommand;
import com.prdreview.reviewer.style.StyleRuleDTO;
import com.prdreview.reviewer.style.UpdateReviewStyleCommand;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.model.StyleRule;
import com.prdreview.reviewer.style.repository.ReviewStyleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 评审风格应用服务 — 编排评审风格用例。
 *
 * <p>领域规则封装在 {@link ReviewStyle} 聚合根内（规则数量、默认风格保护）；
 * 本服务负责名称唯一性、权限决策、Repository 编排、setDefault 事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewStyleApplicationService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ReviewStyleRepository repository;

    // ── 4.3 创建 ───────────────────────────────────────────────────────

    @Transactional
    public ReviewStyleDTO create(CreateReviewStyleCommand cmd) {
        if (!StringUtils.hasText(cmd.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (repository.existsByName(cmd.name(), null)) {
            throw new BizException(ErrorCode.DATA_CONFLICT, "评审风格名称已存在");
        }
        // create 内部强制 isDefault=false 并触发 validateRules()
        ReviewStyle style = ReviewStyle.create(
            cmd.name(), cmd.icon(), cmd.scenario(), cmd.rules(), cmd.sortOrder()
        );
        ReviewStyle saved = repository.save(style);
        log.info("创建评审风格 id={} name={}", saved.getId(), cmd.name());
        return toDTO(saved);
    }

    // ── 4.4 更新 ───────────────────────────────────────────────────────

    @Transactional
    public ReviewStyleDTO update(UpdateReviewStyleCommand cmd) {
        ReviewStyle style = requireStyle(cmd.styleId());
        if (repository.existsByName(cmd.name(), cmd.styleId())) {
            throw new BizException(ErrorCode.DATA_CONFLICT, "评审风格名称已存在");
        }
        // 重建为携带前端 version 的领域对象，触发乐观锁 WHERE version=?；isDefault 保持原值
        ReviewStyle toUpdate = ReviewStyle.reconstruct(
            style.getId(),
            cmd.name(),
            cmd.icon(),
            cmd.scenario(),
            cmd.rules(),
            cmd.enabled() != null ? cmd.enabled() : Boolean.TRUE,
            style.getIsDefault(),
            cmd.sortOrder() != null ? cmd.sortOrder() : 0,
            cmd.version(),
            style.getDeleted(),
            style.getCreatedAt(),
            style.getUpdatedAt()
        );
        // 默认风格不可禁用 + 规则校验
        if (Boolean.TRUE.equals(toUpdate.getIsDefault()) && Boolean.FALSE.equals(toUpdate.getEnabled())) {
            throw new BizException(ErrorCode.STYLE_DEFAULT_NOT_DISABLABLE);
        }
        toUpdate.validateRules();
        repository.update(toUpdate);
        log.info("更新评审风格 id={} name={}", cmd.styleId(), cmd.name());
        return toDTO(requireStyle(cmd.styleId()));
    }

    // ── 4.5 删除 ───────────────────────────────────────────────────────

    @Transactional
    public void delete(Long styleId) {
        ReviewStyle style = requireStyle(styleId);
        style.markDeleted(); // 默认风格抛 STYLE_DEFAULT_NOT_DELETABLE
        repository.softDelete(styleId);
        log.info("逻辑删除评审风格 id={}", styleId);
    }

    // ── 4.6 详情 ───────────────────────────────────────────────────────

    public ReviewStyleDTO getById(Long styleId) {
        return toDTO(requireStyle(styleId));
    }

    // ── 4.7 分页列表 ──────────────────────────────────────────────────

    public ReviewStylePageResult listStyles(ReviewStyleQueryCommand cmd) {
        Boolean enabledFilter;
        if (ROLE_ADMIN.equals(cmd.currentUserRole())) {
            enabledFilter = cmd.enabled();
        } else {
            enabledFilter = Boolean.TRUE;
        }
        ReviewStyleRepository.ReviewStylePage page = repository
            .findPageByCondition(cmd.page(), cmd.size(), enabledFilter);
        List<ReviewStyleDTO> items = page.items().stream().map(this::toDTO).toList();
        return new ReviewStylePageResult(page.total(), items);
    }

    // ── 4.8 切换默认风格（原子） ───────────────────────────────────────

    @Transactional
    public ReviewStyleDTO setDefault(Long styleId) {
        ReviewStyle target = requireStyle(styleId);
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "禁用的风格不能设为默认");
        }
        // 1. 清空所有默认标记
        repository.clearAllDefaultFlags();
        // 2. 将目标标记为默认（重建聚合根以携带当前 version，避免冲突）
        target.markAsDefault();
        repository.update(target);
        log.info("切换默认评审风格 id={} name={}", styleId, target.getName());
        return toDTO(requireStyle(styleId));
    }

    // ── 内部辅助 ──────────────────────────────────────────────────────

    private ReviewStyle requireStyle(Long id) {
        ReviewStyle s = repository.findById(id);
        if (s == null) {
            throw new BizException(ErrorCode.STYLE_NOT_FOUND);
        }
        return s;
    }

    private ReviewStyleDTO toDTO(ReviewStyle s) {
        List<StyleRuleDTO> rules = s.getRules() == null
            ? List.of()
            : s.getRules().stream().map(this::toRuleDTO).toList();
        return new ReviewStyleDTO(
            s.getId(),
            s.getName(),
            s.getIcon(),
            s.getScenario(),
            rules,
            s.getEnabled(),
            s.getIsDefault(),
            s.getSortOrder(),
            s.getVersion(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }

    private StyleRuleDTO toRuleDTO(StyleRule r) {
        return new StyleRuleDTO(r.label(), r.content());
    }
}
