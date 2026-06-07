package com.prdreview.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度必须在 2-32 字符之间")
    @Schema(description = "用户名，2-32 字符", example = "zhangsan")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱地址", example = "zhangsan@company.com")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于 8 位")
    @Schema(description = "密码，最少 8 位", example = "password123")
    private String password;
}
