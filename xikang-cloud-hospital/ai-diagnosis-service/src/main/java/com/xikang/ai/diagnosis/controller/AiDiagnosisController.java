package com.xikang.ai.diagnosis.controller;

import com.xikang.ai.diagnosis.service.AiDiagnosisService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Diagnosis Controller
 */
@RestController
@RequestMapping("/api/ai/diagnosis")
@RequiredArgsConstructor
public class AiDiagnosisController {

    private final AiDiagnosisService aiDiagnosisService;

    /**
     * Get diagnosis suggestions
     */
    @PostMapping("/suggest")
    public Result<Map<String, Object>> getDiagnosisSuggestions(@RequestBody Map<String, Object> diagnosisRequest) {
        Map<String, Object> suggestions = aiDiagnosisService.getDiagnosisSuggestions(diagnosisRequest);
        return Result.success(suggestions);
    }

    /**
     * Analyze symptoms
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeSymptoms(@RequestBody Map<String, Object> symptoms) {
        Map<String, Object> analysis = aiDiagnosisService.analyzeSymptoms(symptoms);
        return Result.success(analysis);
    }

    /**
     * Get ICD code recommendations
     */
    @PostMapping("/icd")
    public Result<Object> getIcdRecommendations(@RequestBody Map<String, Object> diagnosisInfo) {
        return Result.success(aiDiagnosisService.getIcdRecommendations(diagnosisInfo));
    }
}
