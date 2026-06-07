package com.prdreview.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装 Advice。
 *
 * <p>自动将 {@code /api/**} 路径下控制器的返回值包装为 {@link Result}。
 * 跳过：
 * <ul>
 *   <li>Actuator 端点（{@code /actuator/**}）</li>
 *   <li>Swagger/OpenAPI 端点（{@code /swagger-ui/**}, {@code /v3/api-docs/**}）</li>
 *   <li>已经是 {@link Result} 类型的返回值（避免二次包装）</li>
 *   <li>字符串类型（Spring 使用不同的 Converter，需特殊处理）</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String[] SKIP_PREFIXES = {
        "/actuator", "/swagger-ui", "/v3/api-docs", "/webjars"
    };

    private final ObjectMapper objectMapper;

    public GlobalResponseAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        String path = request.getURI().getPath();

        // 跳过非业务路径
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return body;
            }
        }

        // 已经包装过，直接返回
        if (body instanceof Result<?>) {
            return body;
        }

        // String 类型需要特殊处理（Spring 用 StringHttpMessageConverter 直接写字符串）
        // 必须在此处序列化为 JSON 字符串返回，否则 StringHttpMessageConverter 会写出 Result.toString()
        if (body instanceof String str) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(Result.success(str));
            } catch (JsonProcessingException e) {
                // 序列化失败时抛出运行时异常，由 GlobalExceptionHandler 兜底处理
                throw new IllegalStateException("响应序列化失败", e);
            }
        }

        return Result.success(body);
    }
}
