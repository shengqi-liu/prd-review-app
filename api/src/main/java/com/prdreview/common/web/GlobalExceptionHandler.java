package com.prdreview.common.web;

import com.prdreview.common.exception.AiServiceException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>处理策略：
 * <ul>
 *   <li>{@link BizException}          → HTTP 200 + 业务错误码</li>
 *   <li>参数校验异常                   → HTTP 200 + PARAM_INVALID(10002)</li>
 *   <li>{@link Exception}（兜底）      → HTTP 500 + SYSTEM_ERROR(99999)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 静态资源未找到：返回 404，不走业务兜底（避免 favicon.ico 等触发 500） */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Result<Void> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("[OptimisticLockingFailure] 数据版本冲突");
        return Result.error(ErrorCode.PRD_VERSION_CONFLICT, "数据已被修改，请刷新后重试");
    }

    @ExceptionHandler(AiServiceException.class)
    public Result<Void> handleAiServiceException(AiServiceException ex) {
        log.warn("[AiServiceException] {}", ex.getMessage());
        return Result.error(ErrorCode.AI_SERVICE_ERROR, ex.getMessage());
    }

    /** 业务异常：HTTP 200，返回对应错误码 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("[BizException] code={} message={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getErrorCode(), ex.getMessage());
    }

    /** @Valid 校验失败：RequestBody 场景 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse(ErrorCode.PARAM_INVALID.getMessage());
        log.warn("[ParamInvalid] {}", message);
        return Result.error(ErrorCode.PARAM_INVALID, message);
    }

    /** @Valid 校验失败：非 RequestBody 场景（form 表单、路径参数等） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse(ErrorCode.PARAM_INVALID.getMessage());
        log.warn("[BindException] {}", message);
        return Result.error(ErrorCode.PARAM_INVALID, message);
    }

    /** @Validated 方法级参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .findFirst()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .orElse(ErrorCode.PARAM_INVALID.getMessage());
        log.warn("[ConstraintViolation] {}", message);
        return Result.error(ErrorCode.PARAM_INVALID, message);
    }

    /** 兜底：未预期异常，HTTP 500。显式设置状态码避免与 ResponseBodyAdvice 冲突 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletResponse response) {
        log.error("[SystemError] 未捕获异常", ex);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
