package com.prdreview.common.security;

import com.prdreview.common.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>从请求头 {@code Authorization: Bearer <token>} 提取 JWT，验证通过后写入 SecurityContextHolder。
 * token 无效或过期时不中断请求，由 EntryPoint 处理未认证情况。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // 只解析一次 Claims，避免重复计算
                var claims  = jwtUtil.parseToken(token);
                Long userId   = claims.get("userId", Long.class);
                String username = claims.get("username", String.class);
                String role   = claims.get("role", String.class);

                // 构造 Authentication 写入安全上下文
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, username, role),
                    null,
                    authorities
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (ExpiredJwtException e) {
                log.debug("[JWT] Token 已过期: {}", e.getMessage());
                // 不设置 Authentication，由 EntryPoint 处理
            } catch (JwtException e) {
                log.debug("[JWT] Token 无效: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
