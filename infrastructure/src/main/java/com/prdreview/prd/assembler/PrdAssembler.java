package com.prdreview.prd.assembler;

import com.prdreview.prd.model.Prd;
import com.prdreview.prd.model.PrdStatus;
import com.prdreview.prd.model.PrdVersion;
import com.prdreview.prd.po.PrdPO;
import com.prdreview.prd.po.PrdVersionPO;

/**
 * PO ↔ 领域对象双向转换器。
 *
 * <p>静态工具类，无状态，无 Spring 依赖。
 */
public final class PrdAssembler {

    private PrdAssembler() {}

    // ── PrdPO ↔ Prd ─────────────────────────────────────────────────

    public static Prd toDomain(PrdPO po) {
        if (po == null) return null;
        return Prd.reconstruct(
            po.getId(),
            po.getTitle(),
            po.getContent(),
            po.getSourceUrl(),
            po.getAuthorId(),
            PrdStatus.valueOf(po.getStatus()),
            po.getVersion(),
            po.getCreatedAt(),
            po.getUpdatedAt()
        );
    }

    public static PrdPO toPO(Prd prd) {
        PrdPO po = new PrdPO();
        po.setId(prd.getId());
        po.setTitle(prd.getTitle());
        po.setContent(prd.getContent());
        po.setSourceUrl(prd.getSourceUrl());
        po.setAuthorId(prd.getAuthorId());
        po.setStatus(prd.getStatus().name());
        po.setVersion(prd.getVersion());
        // createdAt / updatedAt 由 MetaObjectHandler 填充，不在此设置
        return po;
    }

    // ── PrdVersionPO ↔ PrdVersion ────────────────────────────────────

    public static PrdVersion toDomain(PrdVersionPO po) {
        if (po == null) return null;
        return PrdVersion.reconstruct(
            po.getId(),
            po.getPrdId(),
            po.getVersion(),
            po.getTitle(),
            po.getContent(),
            po.getSourceUrl(),
            po.getCreatedAt()
        );
    }

    public static PrdVersionPO toPO(PrdVersion prdVersion) {
        PrdVersionPO po = new PrdVersionPO();
        po.setId(prdVersion.getId());
        po.setPrdId(prdVersion.getPrdId());
        po.setVersion(prdVersion.getVersion());
        po.setTitle(prdVersion.getTitle());
        po.setContent(prdVersion.getContent());
        po.setSourceUrl(prdVersion.getSourceUrl());
        return po;
    }
}
