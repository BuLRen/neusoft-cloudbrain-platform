package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpPatientPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/last-visit")
    public Result<Map<String, Object>> getLastVisit(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Long registerId
    ) {
        return Result.success(patientPortalService.getLastVisit(patientId, registerId));
    }

    @GetMapping("/observations")
    public Result<List<Map<String, Object>>> listObservations(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Long registerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String sourceType
    ) {
        return Result.success(patientPortalService.listObservations(patientId, registerId, from, to, sourceType));
    }

    @PostMapping("/observations")
    public Result<Map<String, Object>> createObservation(
        @RequestParam(required = false) Long patientId,
        @RequestBody Map<String, Object> request
    ) {
        return Result.success("血糖已记录", patientPortalService.createObservation(patientId, request));
    }

    @GetMapping("/glucose-advice")
    public Result<Map<String, Object>> getGlucoseAdvice(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Long registerId
    ) {
        return Result.success(patientPortalService.getGlucoseAdvice(patientId, registerId));
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

    @PostMapping("/glucose-forecast/refresh")
    public Result<Map<String, Object>> refreshGlucoseForecast(
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) Long registerId
    ) {
        return Result.success("预测已更新", patientPortalService.refreshGlucoseForecast(patientId, registerId));
    }

    @GetMapping("/communication/sessions/{registerId}")
    public Result<Map<String, Object>> getCommunicationSession(
        @RequestParam(required = false) Long patientId,
        @PathVariable Long registerId
    ) {
        return Result.success(patientPortalService.getCommunicationSession(patientId, registerId));
    }

    @GetMapping("/communication/sessions/{registerId}/messages")
    public Result<Map<String, Object>> listCommunicationMessages(
        @RequestParam(required = false) Long patientId,
        @PathVariable Long registerId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset
    ) {
        return Result.success(patientPortalService.listCommunicationMessages(patientId, registerId, limit, offset));
    }

    @GetMapping("/communication/case-summary/{registerId}")
    public Result<Map<String, Object>> getSharedCaseSummary(
        @RequestParam(required = false) Long patientId,
        @PathVariable Long registerId
    ) {
        return Result.success(patientPortalService.getSharedCaseSummary(patientId, registerId));
    }

    @GetMapping("/communication/unread-summary")
    public Result<Map<String, Object>> unreadSummary(
        @RequestParam(required = false) Long patientId,
        @RequestParam Long registerId
    ) {
        return Result.success(patientPortalService.getPatientUnreadSummary(patientId, registerId));
    }

    @PostMapping("/communication/sessions/{registerId}/mark-read")
    public Result<Map<String, Object>> markSessionRead(
        @RequestParam(required = false) Long patientId,
        @PathVariable Long registerId
    ) {
        return Result.success(patientPortalService.markPatientSessionRead(patientId, registerId));
    }
}
