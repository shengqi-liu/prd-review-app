package com.prdreview.knowledgebase.git.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prdreview.knowledgebase.git.po.KbRepositoryPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库仓库 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface KbRepositoryMapper extends BaseMapper<KbRepositoryPO> {
}
