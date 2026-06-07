package com.prdreview.common.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 *
 * <p>使用示例：
 * <pre>{@code
 * throw new BizException(ErrorCode.PRD_NOT_FOUND);
 * throw new BizException(ErrorCode.PARAM_INVALID, "字段 title 不能为空");
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 快捷方法：直接拿到 code 数值 */
    public int getCode() {
        return errorCode.getCode();
    }
}
