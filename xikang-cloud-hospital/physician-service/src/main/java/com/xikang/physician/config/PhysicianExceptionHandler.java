package com.xikang.physician.config;

import com.xikang.common.result.Result;
import com.xikang.physician.ai.DifyWorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Physician module exceptions (Dify workflow failures return business errors, not HTTP 500).
 */
@RestControllerAdvice(basePackages = "com.xikang.physician")
public class PhysicianExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PhysicianExceptionHandler.class);

    @ExceptionHandler(DifyWorkflowException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleDifyWorkflowException(DifyWorkflowException ex) {
        log.warn("Dify workflow error: {}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
}
