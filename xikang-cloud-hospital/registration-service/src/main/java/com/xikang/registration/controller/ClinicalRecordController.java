package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.ClinicalRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration/clinical-record")
@RequiredArgsConstructor
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;

    @GetMapping("/patient/{patientId}/visits")
    public Result<List<Map<String, Object>>> listVisits(
        @PathVariable Long patientId,
        HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("clinicalRecordUserId");
        boolean admin = Boolean.TRUE.equals(request.getAttribute("clinicalRecordAdmin"));
        clinicalRecordService.assertPatientAccess(userId, patientId, admin);
        return Result.success(clinicalRecordService.listVisitsByPatient(patientId, ClinicalRecordService.VIEWER_PATIENT));
    }

    @GetMapping("/visit/{registerId}")
    public Result<Map<String, Object>> getVisitDetail(
        @PathVariable Long registerId,
        HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("clinicalRecordUserId");
        boolean admin = Boolean.TRUE.equals(request.getAttribute("clinicalRecordAdmin"));
        Map<String, Object> detail = clinicalRecordService.getVisitDetail(registerId, ClinicalRecordService.VIEWER_PATIENT);
        Long patientId = toLong(detail.get("patientId"));
        clinicalRecordService.assertPatientAccess(userId, patientId, admin);
        return Result.success(detail);
    }

    @GetMapping("/visit/{registerId}/notebook")
    public Result<Map<String, Object>> getVisitNotebook(
        @PathVariable Long registerId,
        HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("clinicalRecordUserId");
        boolean admin = Boolean.TRUE.equals(request.getAttribute("clinicalRecordAdmin"));
        clinicalRecordService.assertRegisterPatientAccess(userId, registerId, admin);
        return Result.success(clinicalRecordService.getVisitNotebook(registerId, ClinicalRecordService.VIEWER_PATIENT));
    }

    @GetMapping("/patient/{patientId}/profile")
    public Result<Map<String, Object>> getProfile(
        @PathVariable Long patientId,
        HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("clinicalRecordUserId");
        boolean admin = Boolean.TRUE.equals(request.getAttribute("clinicalRecordAdmin"));
        clinicalRecordService.assertPatientAccess(userId, patientId, admin);
        return Result.success(clinicalRecordService.getPatientProfile(patientId));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
