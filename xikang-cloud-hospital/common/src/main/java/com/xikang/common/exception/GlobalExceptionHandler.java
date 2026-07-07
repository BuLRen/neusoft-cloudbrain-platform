package com.xikang.common.exception;

import com.xikang.common.result.Result;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for unified exception processing
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle business exception
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * Handle validation exception
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation exception: {}", message);
        return Result.error(400, message);
    }

    /**
     * Handle bind exception
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Bind exception: {}", message);
        return Result.error(400, message);
    }

    /**
     * Handle request method not supported exception
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMessage());
        return Result.error(405, "Method not supported: " + e.getMethod());
    }

    /**
     * Handle no handler found exception
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("No handler found: {}", e.getMessage());
        return Result.error(404, "Resource not found");
    }

    /**
     * Handle NoResourceFoundException (Spring Boot 3.2 / Spring MVC 6.1+)
     *
     * Spring MVC 6.1 起请求路径找不到 handler 时抛 NoResourceFoundException（不再是 NoHandlerFoundException），
     * 它会落到兜底的 ResourceHttpRequestHandler 上。如果不显式处理，会被下方 handleException(Exception)
     * 兜底返回 500 + 把"No static resource xxx"信息泄漏给前端。
     *
     * 典型触发场景：
     *   - 路径真的不存在（拼写错误）
     *   - @RequestParam(required=true) 缺参数时，Spring 6.1 在某些情况下也会抛此异常（而非 MissingServletRequestParameterException）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("No resource found: {}", e.getMessage());
        return Result.error(404, "Resource not found");
    }

    /**
     * Handle illegal argument exception
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
