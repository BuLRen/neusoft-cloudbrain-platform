package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpOutcomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/outcome")
@RequiredArgsConstructor
public class FollowUpOutcomeController {

    private final FollowUpOutcomeService followUpOutcomeService;

    @GetMapping("/patients")
    public Result<List<Map<String, Object>>> listPatients(
        @RequestParam(required = false) Integer visitState
    ) {
        return Result.success(followUpOutcomeService.listPatients(visitState));
    }

    @GetMapping("/profile/{registerId}")
    public Result<Map<String, Object>> getProfile(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getProfile(registerId));
    }

    @GetMapping("/patient-detail/{registerId}")
    public Result<Map<String, Object>> getPatientDetail(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getPatientDetail(registerId));
    }

    @GetMapping("/metrics/{registerId}")
    public Result<List<Map<String, Object>>> getMetrics(
        @PathVariable Long registerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) List<String> metricKeys,
        @RequestParam(required = false) String sourceType
    ) {
        return Result.success(followUpOutcomeService.getMetrics(registerId, from, to, metricKeys, sourceType));
    }

    @GetMapping("/last-visit/{registerId}")
    public Result<Map<String, Object>> getLastVisit(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getLastVisit(registerId));
    }

    @GetMapping("/glucose-advice/{registerId}")
    public Result<Map<String, Object>> getGlucoseAdvice(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getGlucoseAdvice(registerId));
    }

    @GetMapping("/records/{registerId}")
    public Result<List<Map<String, Object>>> getRecords(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getRecords(registerId));
    }

    @GetMapping("/interview-schedule")
    public Result<List<Map<String, Object>>> listInterviewSchedules(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
        @RequestParam(required = false) String status
    ) {
        return Result.success(followUpOutcomeService.listInterviewSchedules(weekStart, status));
    }

    @GetMapping("/interview-schedule/status/{registerId}")
    public Result<Map<String, Object>> getInterviewScheduleStatus(@PathVariable Long registerId) {
        return Result.success(followUpOutcomeService.getCurrentWeekScheduleStatus(registerId));
    }

    @PostMapping("/interview-schedule")
    public Result<Map<String, Object>> createInterviewSchedule(@RequestBody Map<String, Object> request) {
        Map<String, Object> created = followUpOutcomeService.createInterviewSchedule(request);
        return Result.success("已加入本周访谈日程", created);
    }
}
