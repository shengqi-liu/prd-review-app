package com.prdreview.reviewer.style.po;

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
 * 评审风格持久化对象（MyBatis-Plus）。
 *
 * <p>rules 字段以 JSON 字符串存储，由 Assembler 用 Jackson 解析/序列化。
 * <ul>
 *   <li>{@code @Version} — 乐观锁，依赖 OptimisticLockerInnerInterceptor</li>
 *   <li>{@code @TableLogic} — 逻辑删除，所有查询自动追加 AND deleted=0</li>
 * </ul>
 */
@Data
@TableName("review_style")
public class ReviewStylePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private String scenario;

    /** JSON 数组字符串：[{"label":"...","content":"..."}] */
    private String rules;

    private Boolean enabled;

    @TableField("is_default")
    private Boolean isDefault;

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
