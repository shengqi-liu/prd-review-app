package com.prdreview.reviewer.assembler;

import com.prdreview.reviewer.model.Reviewer;
import com.prdreview.reviewer.po.ReviewerPO;

/**
 * 评审员 PO ↔ 领域对象双向转换器。
 *
 * <p>静态工具类，无状态，无 Spring 依赖。
 */
public final class ReviewerAssembler {

    private ReviewerAssembler() {}

    public static Reviewer toDomain(ReviewerPO po) {
        if (po == null) return null;
        return Reviewer.reconstruct(
            po.getId(),
            po.getName(),
            po.getIcon(),
            po.getDescription(),
            po.getPromptTemplate(),
            po.getEnabled(),
            po.getSortOrder(),
            po.getVersion(),
            po.getDeleted(),
            po.getCreatedAt(),
            po.getUpdatedAt()
        );
    }

    public static ReviewerPO toPO(Reviewer reviewer) {
        ReviewerPO po = new ReviewerPO();
        po.setId(reviewer.getId());
        po.setName(reviewer.getName());
        po.setIcon(reviewer.getIcon());
        po.setDescription(reviewer.getDescription());
        po.setPromptTemplate(reviewer.getPromptTemplate());
        po.setEnabled(reviewer.getEnabled());
        po.setSortOrder(reviewer.getSortOrder());
        po.setVersion(reviewer.getVersion());
        // deleted / createdAt / updatedAt 由 MyBatis-Plus 自动处理
        return po;
    }
}
