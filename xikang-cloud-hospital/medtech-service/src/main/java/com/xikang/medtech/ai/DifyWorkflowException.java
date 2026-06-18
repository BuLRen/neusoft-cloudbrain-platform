package com.xikang.medtech.ai;

/**
 * Raised when Dify workflow HTTP call or execution fails.
 */
public class DifyWorkflowException extends RuntimeException {

    private final int httpStatus;

    public DifyWorkflowException(String message) {
        this(message, 0);
    }

    public DifyWorkflowException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
