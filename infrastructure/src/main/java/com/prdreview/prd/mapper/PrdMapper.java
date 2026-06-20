package com.prdreview.prd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.prd.po.PrdPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PRD Mapper（MyBatis-Plus）。
 */
@Mapper
public interface PrdMapper extends BaseMapper<PrdPO> {
}
