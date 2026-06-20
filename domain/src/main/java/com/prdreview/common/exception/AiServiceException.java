package com.prdreview.common.exception;

/**
 * AI 服务调用链路异常（文档拉取超时、AI API 超时/限流、响应解析失败等）。
 * <p>放在 domain 公共异常包，供 infrastructure（抛出）和 api {@code GlobalExceptionHandler}（捕获）共同访问。
 * <p>由 {@code GlobalExceptionHandler} 捕获后返回 {@code ErrorCode.AI_SERVICE_ERROR(99997)}。
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
