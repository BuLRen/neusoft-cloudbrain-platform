package com.xikang.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * Unified response wrapper for all API responses
 *
 * @param <T> response data type
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Success code
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * Error code
     */
    public static final int ERROR_CODE = 500;

    /**
     * Response code
     */
    private int code;

    /**
     * Response message
     */
    private String message;

    /**
     * Response data
     */
    private T data;

    /**
     * Timestamp
     */
    private long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Return success result with data
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "Success", data);
    }

    /**
     * Return success result with message
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }

    /**
     * Return success result without data
     */
    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "Success", null);
    }

    /**
     * Return error result with message
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ERROR_CODE, message, null);
    }

    /**
     * Return error result with code and message
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * Return error result with code, message and data
     */
    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /**
     * Check if result is success
     */
    public boolean isSuccess() {
        return this.code == SUCCESS_CODE;
    }
}
