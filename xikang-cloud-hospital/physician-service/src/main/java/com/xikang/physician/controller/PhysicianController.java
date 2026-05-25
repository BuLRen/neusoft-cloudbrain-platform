package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.service.PhysicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Physician Controller
 */
@RestController
@RequestMapping("/api/physician")
@RequiredArgsConstructor
public class PhysicianController {

    private final PhysicianService physicianService;

    /**
     * Get diagnosis list by registration ID
     */
    @GetMapping("/diagnosis/{registrationId}")
    public Result<Object> getDiagnosisList(@PathVariable Long registrationId) {
        return Result.success(physicianService.getDiagnosisList(registrationId));
    }

    /**
     * Create diagnosis
     */
    @PostMapping("/diagnosis")
    public Result<Map<String, Object>> createDiagnosis(@RequestBody Map<String, Object> diagnosisRequest) {
        Map<String, Object> result = physicianService.createDiagnosis(diagnosisRequest);
        return Result.success(result);
    }

    /**
     * Get prescription by registration ID
     */
    @GetMapping("/prescription/{registrationId}")
    public Result<Object> getPrescriptionList(@PathVariable Long registrationId) {
        return Result.success(physicianService.getPrescriptionList(registrationId));
    }

    /**
     * Create prescription
     */
    @PostMapping("/prescription")
    public Result<Map<String, Object>> createPrescription(@RequestBody Map<String, Object> prescriptionRequest) {
        Map<String, Object> result = physicianService.createPrescription(prescriptionRequest);
        return Result.success(result);
    }
}
