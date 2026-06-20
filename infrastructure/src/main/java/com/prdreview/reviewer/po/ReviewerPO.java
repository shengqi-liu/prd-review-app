package com.prdreview.reviewer.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评审员持久化对象（MyBatis-Plus）。
 *
 * <p>与领域模型 {@link com.prdreview.reviewer.model.Reviewer} 分离，通过 ReviewerAssembler 转换。
 * <ul>
 *   <li>{@code @Version} — 乐观锁，需注册 OptimisticLockerInnerInterceptor</li>
 *   <li>{@code @TableLogic} — 逻辑删除，所有查询自动追加 AND deleted=0</li>
 * </ul>
 */
@Data
@TableName("reviewer")
public class ReviewerPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private String description;

    @TableField("prompt_template")
    private String promptTemplate;

    private Boolean enabled;

    @TableField("sort_order")
    private Integer sortOrder;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
