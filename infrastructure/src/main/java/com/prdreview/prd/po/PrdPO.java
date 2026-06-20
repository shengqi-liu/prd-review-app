package com.prdreview.prd.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PRD 持久化对象（MyBatis-Plus）。
 *
 * <p>与领域模型 {@link com.prdreview.prd.model.Prd} 分离，通过 PrdAssembler 转换。
 * <ul>
 *   <li>{@code @Version} — 乐观锁，需注册 OptimisticLockerInnerInterceptor</li>
 *   <li>{@code @TableLogic} — 逻辑删除，所有查询自动追加 AND deleted=0</li>
 * </ul>
 */
@Data
@TableName("prd")
public class PrdPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("author_id")
    private Long authorId;

    /** 状态字符串，与 PrdStatus 枚举名称一一对应 */
    private String status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
