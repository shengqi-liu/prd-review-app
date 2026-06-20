package com.prdreview.reviewer.style.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.reviewer.style.po.ReviewStylePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 评审风格 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface ReviewStyleMapper extends BaseMapper<ReviewStylePO> {

    /**
     * 一次性清除所有未删除风格的默认标记。仅供 set-default 事务内调用。
     */
    @Update("UPDATE review_style SET is_default = 0 WHERE is_default = 1 AND deleted = 0")
    int clearAllDefaultFlags();
}
