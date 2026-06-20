package com.prdreview.knowledgebase.git.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prdreview.knowledgebase.git.assembler.KbRepositoryAssembler;
import com.prdreview.knowledgebase.git.mapper.KbRepositoryMapper;
import com.prdreview.knowledgebase.git.model.KbRepository;
import com.prdreview.knowledgebase.git.po.KbRepositoryPO;
import com.prdreview.knowledgebase.git.repository.KbRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * KbRepositoryRepository 基础设施实现。
 */
@Repository
@RequiredArgsConstructor
public class KbRepositoryRepositoryImpl implements KbRepositoryRepository {

    private final KbRepositoryMapper mapper;

    @Override
    public KbRepository findById(Long id) {
        return KbRepositoryAssembler.toDomain(mapper.selectById(id));
    }

    @Override
    public KbRepository findActive() {
        // @TableLogic 自动过滤 deleted=0；取 id 最小的一条
        LambdaQueryWrapper<KbRepositoryPO> w = new LambdaQueryWrapper<>();
        w.orderByAsc(KbRepositoryPO::getId).last("LIMIT 1");
        return KbRepositoryAssembler.toDomain(mapper.selectOne(w));
    }

    @Override
    public List<KbRepository> findAll() {
        LambdaQueryWrapper<KbRepositoryPO> w = new LambdaQueryWrapper<>();
        w.orderByAsc(KbRepositoryPO::getCreatedAt).orderByAsc(KbRepositoryPO::getId);
        return mapper.selectList(w).stream()
            .map(KbRepositoryAssembler::toDomain)
            .toList();
    }

    @Override
    public boolean existsActive() {
        return mapper.selectCount(new LambdaQueryWrapper<>()) > 0;
    }

    @Override
    public KbRepository save(KbRepository repository) {
        KbRepositoryPO po = KbRepositoryAssembler.toPO(repository);
        mapper.insert(po);
        return KbRepositoryAssembler.toDomain(po);
    }

    @Override
    public void update(KbRepository repository) {
        mapper.updateById(KbRepositoryAssembler.toPO(repository));
    }

    @Override
    public void softDelete(Long id) {
        mapper.deleteById(id); // @TableLogic → UPDATE deleted=1
    }
}
