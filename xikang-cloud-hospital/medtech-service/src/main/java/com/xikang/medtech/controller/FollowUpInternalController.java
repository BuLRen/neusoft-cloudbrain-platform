package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpAutoEnrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/internal/follow-up")
@RequiredArgsConstructor
public class FollowUpInternalController {

    private final FollowUpAutoEnrollService autoEnrollService;

    @PostMapping("/visit-ended")
    public Result<Map<String, Object>> visitEnded(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Long employeeId = toLong(request.get("employeeId"));
        Long departmentId = toLong(request.get("departmentId"));
        LocalDateTime visitEndedAt = parseDateTime(request.get("visitEndedAt"));
        return Result.success(autoEnrollService.handleVisitEnded(registerId, visitEndedAt, employeeId, departmentId));
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value));
    }
}
