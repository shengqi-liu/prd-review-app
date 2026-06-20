package com.prdreview.reviewer.style.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.model.StyleRule;
import com.prdreview.reviewer.style.po.ReviewStylePO;

import java.util.List;

/**
 * 评审风格 PO ↔ 领域对象双向转换器。
 *
 * <p>rules 字段在 PO 端为 JSON 字符串，在 Domain 端为 {@code List<StyleRule>}；
 * 由静态 ObjectMapper 实例负责序列化与反序列化。
 */
public final class ReviewStyleAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<StyleRule>> RULE_LIST_TYPE = new TypeReference<>() {};

    private ReviewStyleAssembler() {}

    public static ReviewStyle toDomain(ReviewStylePO po) {
        if (po == null) return null;
        List<StyleRule> rules = parseRules(po.getRules());
        return ReviewStyle.reconstruct(
            po.getId(),
            po.getName(),
            po.getIcon(),
            po.getScenario(),
            rules,
            po.getEnabled(),
            po.getIsDefault(),
            po.getSortOrder(),
            po.getVersion(),
            po.getDeleted(),
            po.getCreatedAt(),
            po.getUpdatedAt()
        );
    }

    public static ReviewStylePO toPO(ReviewStyle style) {
        ReviewStylePO po = new ReviewStylePO();
        po.setId(style.getId());
        po.setName(style.getName());
        po.setIcon(style.getIcon());
        po.setScenario(style.getScenario());
        po.setRules(writeRules(style.getRules()));
        po.setEnabled(style.getEnabled());
        po.setIsDefault(style.getIsDefault());
        po.setSortOrder(style.getSortOrder());
        po.setVersion(style.getVersion());
        return po;
    }

    private static List<StyleRule> parseRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, RULE_LIST_TYPE);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.STYLE_RULE_INVALID, "评审风格规则 JSON 反序列化失败：" + ex.getMessage());
        }
    }

    private static String writeRules(List<StyleRule> rules) {
        try {
            return MAPPER.writeValueAsString(rules != null ? rules : List.of());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.STYLE_RULE_INVALID, "评审风格规则 JSON 序列化失败：" + ex.getMessage());
        }
    }
}
