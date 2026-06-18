package com.xikang.physician.ai;

/**
 * Raised when Dify workflow HTTP call or execution fails (caller maps to user-safe message).
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

    public boolean isPaused() {
        String msg = getMessage();
        return msg != null && (msg.contains("paused") || msg.contains("人工介入"));
    }
}
