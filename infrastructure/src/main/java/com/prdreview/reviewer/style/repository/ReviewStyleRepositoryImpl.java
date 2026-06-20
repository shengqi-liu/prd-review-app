package com.prdreview.reviewer.style.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prdreview.reviewer.style.assembler.ReviewStyleAssembler;
import com.prdreview.reviewer.style.mapper.ReviewStyleMapper;
import com.prdreview.reviewer.style.model.ReviewStyle;
import com.prdreview.reviewer.style.po.ReviewStylePO;
import com.prdreview.reviewer.style.repository.ReviewStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ReviewStyleRepository 基础设施实现。
 */
@Repository
@RequiredArgsConstructor
public class ReviewStyleRepositoryImpl implements ReviewStyleRepository {

    private final ReviewStyleMapper mapper;

    @Override
    public ReviewStyle findById(Long id) {
        ReviewStylePO po = mapper.selectById(id);
        return ReviewStyleAssembler.toDomain(po);
    }

    @Override
    public ReviewStyle findDefault() {
        LambdaQueryWrapper<ReviewStylePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewStylePO::getIsDefault, Boolean.TRUE)
            .eq(ReviewStylePO::getEnabled, Boolean.TRUE)
            .last("LIMIT 1");
        return ReviewStyleAssembler.toDomain(mapper.selectOne(wrapper));
    }

    @Override
    public List<ReviewStyle> findAllEnabled() {
        LambdaQueryWrapper<ReviewStylePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewStylePO::getEnabled, Boolean.TRUE)
            .orderByAsc(ReviewStylePO::getSortOrder)
            .orderByAsc(ReviewStylePO::getId);
        return mapper.selectList(wrapper).stream()
            .map(ReviewStyleAssembler::toDomain)
            .toList();
    }

    @Override
    public ReviewStyle save(ReviewStyle style) {
        ReviewStylePO po = ReviewStyleAssembler.toPO(style);
        mapper.insert(po);
        return ReviewStyleAssembler.toDomain(po);
    }

    @Override
    public void update(ReviewStyle style) {
        ReviewStylePO po = ReviewStyleAssembler.toPO(style);
        mapper.updateById(po);
    }

    @Override
    public void softDelete(Long id) {
        mapper.deleteById(id); // @TableLogic → UPDATE deleted=1
    }

    @Override
    public ReviewStylePage findPageByCondition(int page, int size, Boolean enabled) {
        LambdaQueryWrapper<ReviewStylePO> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(ReviewStylePO::getEnabled, enabled);
        }
        wrapper.orderByAsc(ReviewStylePO::getSortOrder).orderByAsc(ReviewStylePO::getId);

        Page<ReviewStylePO> pageResult = mapper.selectPage(new Page<>(page, size), wrapper);
        List<ReviewStyle> items = pageResult.getRecords().stream()
            .map(ReviewStyleAssembler::toDomain)
            .toList();
        return new ReviewStylePage(pageResult.getTotal(), items);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<ReviewStylePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewStylePO::getName, name);
        if (excludeId != null) {
            wrapper.ne(ReviewStylePO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public void clearAllDefaultFlags() {
        mapper.clearAllDefaultFlags();
    }
}
