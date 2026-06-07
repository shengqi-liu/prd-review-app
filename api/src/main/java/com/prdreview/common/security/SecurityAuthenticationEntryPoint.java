package com.prdreview.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 自定义认证入口点。
 *
 * <p>未认证访问受保护接口时，返回 {@code Result.error(UNAUTHORIZED)} JSON，HTTP 200。
 * 与 {@link com.prdreview.common.web.GlobalExceptionHandler} 规范保持一致。</p>
 */
@Component
@RequiredArgsConstructor
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Result<Void> result = Result.error(ErrorCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
