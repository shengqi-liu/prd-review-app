package com.prdreview.reviewer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.reviewer.po.ReviewerPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评审员 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface ReviewerMapper extends BaseMapper<ReviewerPO> {
}
