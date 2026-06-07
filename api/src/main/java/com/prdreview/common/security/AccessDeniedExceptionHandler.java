package com.prdreview.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 自定义权限拒绝处理器。
 *
 * <p>已认证用户访问权限不足的接口时，返回 {@code Result.error(FORBIDDEN)} JSON，HTTP 200。
 * 与 {@link SecurityAuthenticationEntryPoint} 及 {@link com.prdreview.common.web.GlobalExceptionHandler} 规范保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedExceptionHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[RBAC] 权限不足: uri={}, user={}", request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "unknown");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Result<Void> result = Result.error(ErrorCode.FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
