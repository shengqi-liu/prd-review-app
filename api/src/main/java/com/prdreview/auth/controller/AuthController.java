package com.prdreview.auth.controller;

import com.prdreview.auth.dto.LoginCommand;
import com.prdreview.auth.dto.LoginRequest;
import com.prdreview.auth.dto.RegisterRequest;
import com.prdreview.auth.service.AuthApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（注册 / 登录），白名单路径，无需 JWT。
 */
@Tag(name = "认证", description = "用户注册与登录")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authService;

    @Operation(summary = "用户注册", description = "注册新用户，返回用户基本信息（不含密码）")
    @PostMapping("/register")
    public Object register(@Valid @RequestBody RegisterRequest req) {
        var cmd = new com.prdreview.auth.dto.RegisterCommand();
        cmd.setUsername(req.getUsername());
        cmd.setEmail(req.getEmail());
        cmd.setPassword(req.getPassword());
        return authService.register(cmd);
    }

    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT access token")
    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest req) {
        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(req.getUsername());
        cmd.setPassword(req.getPassword());
        return authService.login(cmd);
    }
}
