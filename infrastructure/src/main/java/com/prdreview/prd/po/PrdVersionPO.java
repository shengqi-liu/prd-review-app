package com.prdreview.prd.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PRD 版本快照持久化对象（MyBatis-Plus）。
 */
@Data
@TableName("prd_version")
public class PrdVersionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("prd_id")
    private Long prdId;

    private Integer version;

    private String title;

    private String content;

    @TableField("source_url")
    private String sourceUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
