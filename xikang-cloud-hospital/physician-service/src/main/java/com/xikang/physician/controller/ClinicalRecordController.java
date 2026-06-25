package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.service.ClinicalRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/physician/clinical-record")
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;

    public ClinicalRecordController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @GetMapping("/visit/{registerId}/timeline")
    public Result<Map<String, Object>> getTimeline(@PathVariable Long registerId) {
        return Result.success(clinicalRecordService.getVisitTimeline(registerId));
    }

    @GetMapping("/visit/{registerId}/notebook")
    public Result<Map<String, Object>> getNotebook(@PathVariable Long registerId) {
        return Result.success(clinicalRecordService.getVisitNotebook(registerId));
    }

    @PostMapping("/visit/{registerId}/archive")
    public Result<Map<String, Object>> archiveVisit(@PathVariable Long registerId) {
        return Result.success("病历已归档并发布给患者", clinicalRecordService.archiveVisit(registerId));
    }

    @GetMapping("/patient/{patientId}/profile")
    public Result<Map<String, Object>> getProfile(@PathVariable Long patientId) {
        return Result.success(clinicalRecordService.getPatientProfile(patientId));
    }

    @PutMapping("/patient/{patientId}/profile")
    public Result<Map<String, Object>> updateProfile(
        @PathVariable Long patientId,
        @RequestBody Map<String, Object> request
    ) {
        return Result.success("患者长期档案已更新", clinicalRecordService.updatePatientProfile(patientId, request));
    }
}
