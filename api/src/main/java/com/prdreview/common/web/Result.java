package com.prdreview.common.web;

import com.prdreview.common.exception.ErrorCode;
import lombok.Getter;
import org.slf4j.MDC;

/**
 * 统一 HTTP 响应包装。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code code}    — 错误码，0 表示成功</li>
 *   <li>{@code message} — 提示信息</li>
 *   <li>{@code data}    — 业务数据（成功时）</li>
 *   <li>{@code traceId} — 链路追踪 ID，自动从 MDC 读取</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * return Result.success(user);
 * return Result.success();
 * return Result.error(ErrorCode.PRD_NOT_FOUND);
 * return Result.error(ErrorCode.PARAM_INVALID, "字段 title 不能为空");
 * }</pre>
 */
@Getter
public class Result<T> {

    private static final String TRACE_ID_KEY = "traceId";

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(TRACE_ID_KEY);
    }

    // ── 成功 ──────────────────────────────────────────────

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> success() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    // ── 失败 ──────────────────────────────────────────────

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ── 判断 ──────────────────────────────────────────────

    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
