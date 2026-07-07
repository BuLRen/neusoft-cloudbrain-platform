package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpDashboardService;
import com.xikang.medtech.service.FollowUpMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/dashboard")
@RequiredArgsConstructor
public class FollowUpDashboardController {

    private final FollowUpDashboardService followUpDashboardService;
    private final FollowUpMonitoringService monitoringService;

    @GetMapping("/context")
    public Result<Map<String, Object>> getContext(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long departmentId
    ) {
        return Result.success(followUpDashboardService.getContext(date, departmentId));
    }

    @GetMapping("/patients")
    public Result<List<Map<String, Object>>> listPatients(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long departmentId
    ) {
        return Result.success(followUpDashboardService.listPatients(date, departmentId));
    }

    @GetMapping("/schedule")
    public Result<List<Map<String, Object>>> listSchedules(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long departmentId
    ) {
        return Result.success(followUpDashboardService.listSchedules(from, to, departmentId));
    }

    @PostMapping("/schedule")
    public Result<Map<String, Object>> createSchedule(@RequestBody Map<String, Object> request) {
        Map<String, Object> created = followUpDashboardService.createSchedule(request);
        return Result.success("日程已创建", created);
    }

    @PatchMapping("/schedule/{id}")
    public Result<Map<String, Object>> updateScheduleStatus(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String status = request.get("status") != null ? String.valueOf(request.get("status")) : null;
        Map<String, Object> updated = followUpDashboardService.updateScheduleStatus(id, status);
        return Result.success("日程已更新", updated);
    }

    @PostMapping("/observation/confirm")
    public Result<Map<String, Object>> confirmObservation(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = followUpDashboardService.confirmObservation(request);
        return Result.success("已确认今日观察", result);
    }

    @PostMapping("/enroll")
    public Result<Map<String, Object>> enrollPatient(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = followUpDashboardService.enrollPatient(request);
        return Result.success("已纳入随访管理", result);
    }

    @PostMapping("/monitor/claim")
    public Result<Map<String, Object>> claimMonitoring(@RequestBody Map<String, Object> request) {
        Long registerId = request.get("registerId") != null ? Long.valueOf(String.valueOf(request.get("registerId"))) : null;
        return Result.success(followUpDashboardService.claimMonitoring(registerId));
    }

    @PostMapping("/monitor/release")
    public Result<Map<String, Object>> releaseMonitoring(@RequestBody Map<String, Object> request) {
        Long registerId = request.get("registerId") != null ? Long.valueOf(String.valueOf(request.get("registerId"))) : null;
        return Result.success("已释放监视", followUpDashboardService.releaseMonitoring(registerId));
    }

    @PostMapping("/monitor/transfer-request")
    public Result<Map<String, Object>> submitTransferRequest(@RequestBody Map<String, Object> request) {
        return Result.success("调换申请已提交", monitoringService.submitTransferRequest(request));
    }

    @GetMapping("/observation/status/{registerId}")
    public Result<Map<String, Object>> getObservationStatus(
        @PathVariable Long registerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return Result.success(followUpDashboardService.getObservationStatus(registerId, date));
    }
}
