package com.prdreview.prd.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prdreview.prd.assembler.PrdAssembler;
import com.prdreview.prd.mapper.PrdMapper;
import com.prdreview.prd.model.Prd;
import com.prdreview.prd.model.PrdStatus;
import com.prdreview.prd.po.PrdPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PrdRepository 基础设施实现。
 */
@Repository
@RequiredArgsConstructor
public class PrdRepositoryImpl implements PrdRepository {

    private final PrdMapper prdMapper;

    @Override
    public Prd findById(Long id) {
        PrdPO po = prdMapper.selectById(id);
        return PrdAssembler.toDomain(po);
    }

    @Override
    public Prd save(Prd prd) {
        PrdPO po = PrdAssembler.toPO(prd);
        prdMapper.insert(po);
        // insert 后 MyBatis-Plus 将自增 id 回填到 po
        return PrdAssembler.toDomain(po);
    }

    @Override
    public void update(Prd prd) {
        PrdPO po = PrdAssembler.toPO(prd);
        prdMapper.updateById(po);
    }

    @Override
    public void softDelete(Long id) {
        prdMapper.deleteById(id);  // @TableLogic 将 DELETE 转为 UPDATE deleted=1
    }

    @Override
    public PrdPage findPageByCondition(int page, int size, Long authorId, boolean excludeInitializing) {
        LambdaQueryWrapper<PrdPO> wrapper = new LambdaQueryWrapper<>();

        // 按作者过滤（SUBMITTER 传 authorId，ADMIN/TEAM_MEMBER 传 null）
        if (authorId != null) {
            wrapper.eq(PrdPO::getAuthorId, authorId);
        }

        // 列表接口排除 INITIALIZING 状态
        if (excludeInitializing) {
            wrapper.ne(PrdPO::getStatus, PrdStatus.INITIALIZING.name());
        }

        // 固定 ORDER BY created_at DESC
        wrapper.orderByDesc(PrdPO::getCreatedAt);

        Page<PrdPO> pageResult = prdMapper.selectPage(new Page<>(page, size), wrapper);

        List<Prd> items = pageResult.getRecords().stream()
            .map(PrdAssembler::toDomain)
            .toList();

        return new PrdPage(pageResult.getTotal(), items);
    }
}
