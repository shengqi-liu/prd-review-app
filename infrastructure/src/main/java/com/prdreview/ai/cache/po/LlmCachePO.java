package com.prdreview.ai.cache.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * llm_cache 表 PO。无逻辑删除,无乐观锁(并发命中误差可接受)。
 *
 * <p>{@code createdAt} / {@code lastHitAt} 不用 MyBatis-Plus 的 INSERT/UPDATE fill —
 * 全局 {@code MetaObjectHandlerConfig} 只填名为 createdAt / updatedAt 的字段,这里字段名不一致;
 * 且表定义 `DEFAULT CURRENT_TIMESTAMP` 已自动处理 INSERT,UPDATE 时由 incrementHit 显式 setSql。
 */
@Data
@TableName("llm_cache")
public class LlmCachePO {

    @TableId(value = "cache_key", type = IdType.INPUT)
    private String cacheKey;

    private String response;

    private String provider;

    private String model;

    @TableField("prompt_preview")
    private String promptPreview;

    @TableField("token_count_estimate")
    private Integer tokenCountEstimate;

    @TableField("hit_count")
    private Integer hitCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("last_hit_at")
    private LocalDateTime lastHitAt;
}
