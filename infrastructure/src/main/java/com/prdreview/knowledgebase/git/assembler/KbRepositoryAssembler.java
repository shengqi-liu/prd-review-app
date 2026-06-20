package com.prdreview.knowledgebase.git.assembler;

import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.po.KbRepositoryPO;

/**
 * KbRepositoryPO ↔ KbRepository 双向转换。
 * 静态工具类，无 Spring 依赖。
 */
public final class KbRepositoryAssembler {

    private KbRepositoryAssembler() {}

    public static KbRepository toDomain(KbRepositoryPO po) {
        if (po == null) return null;
        return KbRepository.reconstruct(
            po.getId(),
            po.getName(),
            po.getRemoteUrl(),
            po.getBranch(),
            po.getLocalPath(),
            po.getAuthType() != null ? AuthType.valueOf(po.getAuthType()) : AuthType.NONE,
            po.getAuthSecret(),
            po.getPollIntervalMs(),
            po.getSyncStatus() != null ? SyncStatus.valueOf(po.getSyncStatus()) : SyncStatus.HEALTHY,
            po.getLastSyncedCommit(),
            po.getLastSyncedAt(),
            po.getLastErrorMessage(),
            po.getVersion(),
            po.getDeleted(),
            po.getCreatedAt(),
            po.getUpdatedAt()
        );
    }

    public static KbRepositoryPO toPO(KbRepository r) {
        KbRepositoryPO po = new KbRepositoryPO();
        po.setId(r.getId());
        po.setName(r.getName());
        po.setRemoteUrl(r.getRemoteUrl());
        po.setBranch(r.getBranch());
        po.setLocalPath(r.getLocalPath());
        po.setAuthType(r.getAuthType() != null ? r.getAuthType().name() : AuthType.NONE.name());
        po.setAuthSecret(r.getAuthSecret());
        po.setPollIntervalMs(r.getPollIntervalMs());
        po.setSyncStatus(r.getSyncStatus() != null ? r.getSyncStatus().name() : SyncStatus.HEALTHY.name());
        po.setLastSyncedCommit(r.getLastSyncedCommit());
        po.setLastSyncedAt(r.getLastSyncedAt());
        po.setLastErrorMessage(r.getLastErrorMessage());
        po.setVersion(r.getVersion());
        // deleted / createdAt / updatedAt 由 MyBatis-Plus 自动处理
        return po;
    }
}
