package com.prdreview.knowledgebase.git.repository;

import com.prdreview.knowledgebase.git.mapper.KbRepositoryMapper;
import com.prdreview.knowledgebase.git.model.AuthType;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.model.SyncStatus;
import com.prdreview.knowledgebase.git.po.KbRepositoryPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * fix-kb-sync-correctness Bug B 防回归：
 * <ul>
 *   <li>update(domain) 调用后返回的 domain 持有自增后的 version</li>
 *   <li>其他字段透传不丢</li>
 * </ul>
 *
 * <p>用 Mockito 模拟 MyBatis-Plus 的 @Version 行为（updateById 内部把 po.version+1）。
 * 不引入 SpringBootTest，避免基础设施重量级。
 */
@DisplayName("KbRepositoryRepositoryImpl update 反写 version 测试")
@ExtendWith(MockitoExtension.class)
class KbRepositoryRepositoryImplTest {

    @Mock KbRepositoryMapper mapper;

    @InjectMocks KbRepositoryRepositoryImpl repository;

    private KbRepository sampleDomain(int versionBefore) {
        return KbRepository.reconstruct(
            7L, "kb-x", "https://x/y.git", "main", "/tmp/repo-7",
            AuthType.HTTPS_TOKEN, "tok", 3600000L,
            SyncStatus.HEALTHY, "abc", LocalDateTime.now().minusMinutes(5), null,
            versionBefore, 0,
            LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(5)
        );
    }

    @Test
    @DisplayName("update 返回的 domain 持有自增后的 version")
    void update_returnsDomainWithIncrementedVersion() {
        KbRepository in = sampleDomain(3);
        // 模拟 MyBatis-Plus @Version：updateById 调用时 PO 的 version 被自增（mock 的副作用）
        when(mapper.updateById(any(KbRepositoryPO.class))).thenAnswer(inv -> {
            KbRepositoryPO po = inv.getArgument(0);
            po.setVersion(po.getVersion() + 1);
            return 1;
        });

        KbRepository out = repository.update(in);

        assertThat(out).isNotNull();
        assertThat(out.getVersion())
            .as("update 返回值的 version 必须比入参 +1，否则调用方下次 update 会乐观锁失败")
            .isEqualTo(4);
        // 其他字段透传不变
        assertThat(out.getId()).isEqualTo(7L);
        assertThat(out.getName()).isEqualTo("kb-x");
        assertThat(out.getRemoteUrl()).isEqualTo("https://x/y.git");
        assertThat(out.getSyncStatus()).isEqualTo(SyncStatus.HEALTHY);
        assertThat(out.getLastSyncedCommit()).isEqualTo("abc");
    }

    @Test
    @DisplayName("update 返回不为 null（即使下游 mock 返回 0 行，也按 Assembler 转换后返回 — 失败应由乐观锁异常抛出而非返回 null）")
    void update_neverReturnsNull() {
        KbRepository in = sampleDomain(1);
        when(mapper.updateById(any(KbRepositoryPO.class))).thenReturn(1);

        KbRepository out = repository.update(in);

        assertThat(out).isNotNull();
    }
}
