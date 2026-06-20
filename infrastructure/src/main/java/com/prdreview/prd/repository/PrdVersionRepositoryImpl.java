package com.prdreview.prd.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prdreview.prd.assembler.PrdAssembler;
import com.prdreview.prd.mapper.PrdVersionMapper;
import com.prdreview.prd.model.PrdVersion;
import com.prdreview.prd.po.PrdVersionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PrdVersionRepository 基础设施实现。
 */
@Repository
@RequiredArgsConstructor
public class PrdVersionRepositoryImpl implements PrdVersionRepository {

    private final PrdVersionMapper prdVersionMapper;

    @Override
    public void save(PrdVersion prdVersion) {
        PrdVersionPO po = PrdAssembler.toPO(prdVersion);
        prdVersionMapper.insert(po);
    }

    @Override
    public List<PrdVersion> findByPrdId(Long prdId) {
        LambdaQueryWrapper<PrdVersionPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrdVersionPO::getPrdId, prdId)
               .orderByAsc(PrdVersionPO::getVersion);
        return prdVersionMapper.selectList(wrapper).stream()
            .map(PrdAssembler::toDomain)
            .toList();
    }
}
