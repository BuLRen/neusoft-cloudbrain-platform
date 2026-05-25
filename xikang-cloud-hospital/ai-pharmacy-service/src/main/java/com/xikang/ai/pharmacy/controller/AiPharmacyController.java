package com.xikang.ai.pharmacy.controller;

import com.xikang.ai.pharmacy.service.AiPharmacyService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Pharmacy Controller
 */
@RestController
@RequestMapping("/api/ai/pharmacy")
@RequiredArgsConstructor
public class AiPharmacyController {

    private final AiPharmacyService aiPharmacyService;

    /**
     * Check drug interactions
     */
    @PostMapping("/interaction")
    public Result<Map<String, Object>> checkDrugInteractions(@RequestBody Map<String, Object> drugRequest) {
        Map<String, Object> interactions = aiPharmacyService.checkDrugInteractions(drugRequest);
        return Result.success(interactions);
    }

    /**
     * Get medication guidance
     */
    @PostMapping("/guidance")
    public Result<Map<String, Object>> getMedicationGuidance(@RequestBody Map<String, Object> medicationRequest) {
        Map<String, Object> guidance = aiPharmacyService.getMedicationGuidance(medicationRequest);
        return Result.success(guidance);
    }

    /**
     * Check contraindications
     */
    @PostMapping("/contraindication")
    public Result<Map<String, Object>> checkContraindications(@RequestBody Map<String, Object> patientInfo) {
        Map<String, Object> contraindications = aiPharmacyService.checkContraindications(patientInfo);
        return Result.success(contraindications);
    }
}
