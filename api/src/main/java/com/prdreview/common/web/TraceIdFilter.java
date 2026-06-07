package com.prdreview.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 注入 Filter。
 *
 * <p>在请求进入时生成 UUID（去横线）写入 MDC {@code traceId}，
 * 在响应返回时通过响应头 {@code X-Trace-Id} 暴露，请求结束后清理 MDC。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先使用客户端传入的 traceId，否则自动生成
        // 安全校验：过滤换行符防止 Log Injection，截断至 64 字符
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId;
        if (incoming != null && !incoming.isBlank()) {
            traceId = incoming.replaceAll("[\r\n]", "");
            if (traceId.length() > 64) {
                traceId = traceId.substring(0, 64);
            }
        } else {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
