package com.prdreview.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.auth.model.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
