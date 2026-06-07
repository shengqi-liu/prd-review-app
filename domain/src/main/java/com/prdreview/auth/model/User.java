package com.prdreview.auth.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户聚合根。
 *
 * <p>注：MyBatis-Plus 注解仅作持久化映射元数据，不影响领域行为。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    /** BCrypt 加密后的密码，明文禁止存储 */
    private String password;

    @Builder.Default
    private UserRole role = UserRole.SUBMITTER;

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
