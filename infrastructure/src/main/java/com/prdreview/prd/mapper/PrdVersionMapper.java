package com.prdreview.prd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.prd.po.PrdVersionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PRD 版本快照 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface PrdVersionMapper extends BaseMapper<PrdVersionPO> {
}
