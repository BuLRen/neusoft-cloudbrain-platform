package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpPatientPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/patient")
@RequiredArgsConstructor
public class FollowUpPatientPortalController {

    private final FollowUpPatientPortalService patientPortalService;

    @GetMapping("/plans")
    public Result<List<Map<String, Object>>> listPlans(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) List<Long> registerIds
    ) {
        return Result.success(patientPortalService.listPlans(patientId, registerIds));
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> listRecords(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) List<Long> registerIds
    ) {
        return Result.success(patientPortalService.listRecords(patientId, registerIds));
    }

    @GetMapping("/medications")
    public Result<List<Map<String, Object>>> listMedications(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) List<Long> registerIds
    ) {
        return Result.success(patientPortalService.listMedications(patientId, registerIds));
    }

    @PatchMapping("/plans/{id}/complete")
    public Result<Map<String, Object>> completePlan(@PathVariable Long id) {
        return Result.success("计划已标记完成", patientPortalService.completePlan(id));
    }

    @PostMapping("/feedback")
    public Result<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> request) {
        return Result.success("反馈已提交", patientPortalService.submitFeedback(request));
    }

    @GetMapping("/glucose-forecast")
    public Result<Map<String, Object>> glucoseForecast(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Long registerId
    ) {
        return Result.success(patientPortalService.getGlucoseForecast(patientId, registerId));
    }
}
