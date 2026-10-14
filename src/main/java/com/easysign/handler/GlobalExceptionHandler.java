package com.easysign.handler;

import com.easysign.common.BizCode;
import com.easysign.common.BizException;
import com.easysign.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理
 * <p>
 * 使用 {@link RestControllerAdvice} 统一捕获 Controller 层抛出的各类异常，
 * 按异常类型返回结构一致的 {@link Result} 响应体，避免将堆栈信息直接暴露给客户端。
 * </p>
 * <p>
 * 处理类型覆盖：业务异常（{@link BizException}）、参数校验失败、约束校验失败、
 * 参数缺失、请求体格式错误、参数类型转换失败，以及兜底的未预期异常。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * @Valid 参数校验失败
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValid(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.error(BizCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * @PathVariable/@RequestParam 校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("约束校验失败: {}", e.getMessage());
        return Result.error(BizCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 必填参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("必填参数缺失: {}", e.getParameterName());
        return Result.error(BizCode.PARAM_ERROR.getCode(), "必填参数缺失: " + e.getParameterName());
    }

    /**
     * 请求体格式错误（JSON 解析失败）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.error(BizCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    /**
     * 参数类型转换失败
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}", e.getMessage());
        return Result.error(BizCode.PARAM_ERROR.getCode(), "参数类型错误: " + e.getName());
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("未预期异常: ", e);
        return Result.error(BizCode.INTERNAL_ERROR);
    }
}