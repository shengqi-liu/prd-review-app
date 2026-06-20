package com.prdreview.reviewer.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prdreview.reviewer.assembler.ReviewerAssembler;
import com.prdreview.reviewer.mapper.ReviewerMapper;
import com.prdreview.reviewer.model.Reviewer;
import com.prdreview.reviewer.po.ReviewerPO;
import com.prdreview.reviewer.repository.ReviewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ReviewerRepository 基础设施实现。
 */
@Repository
@RequiredArgsConstructor
public class ReviewerRepositoryImpl implements ReviewerRepository {

    private final ReviewerMapper reviewerMapper;

    @Override
    public Reviewer findById(Long id) {
        ReviewerPO po = reviewerMapper.selectById(id);
        return ReviewerAssembler.toDomain(po);
    }

    @Override
    public Reviewer save(Reviewer reviewer) {
        ReviewerPO po = ReviewerAssembler.toPO(reviewer);
        reviewerMapper.insert(po);
        return ReviewerAssembler.toDomain(po);
    }

    @Override
    public void update(Reviewer reviewer) {
        ReviewerPO po = ReviewerAssembler.toPO(reviewer);
        reviewerMapper.updateById(po);
    }

    @Override
    public void softDelete(Long id) {
        reviewerMapper.deleteById(id); // @TableLogic → UPDATE deleted=1
    }

    @Override
    public ReviewerPage findPageByCondition(int page, int size, Boolean enabled) {
        LambdaQueryWrapper<ReviewerPO> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(ReviewerPO::getEnabled, enabled);
        }
        wrapper.orderByAsc(ReviewerPO::getSortOrder).orderByAsc(ReviewerPO::getId);

        Page<ReviewerPO> pageResult = reviewerMapper.selectPage(new Page<>(page, size), wrapper);
        List<Reviewer> items = pageResult.getRecords().stream()
            .map(ReviewerAssembler::toDomain)
            .toList();
        return new ReviewerPage(pageResult.getTotal(), items);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<ReviewerPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewerPO::getName, name);
        if (excludeId != null) {
            wrapper.ne(ReviewerPO::getId, excludeId);
        }
        // @TableLogic 自动追加 AND deleted=0
        return reviewerMapper.selectCount(wrapper) > 0;
    }
}
