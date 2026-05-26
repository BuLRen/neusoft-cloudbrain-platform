package com.xikang.ai.diagnosis.controller;

import com.xikang.ai.diagnosis.entity.AiDiagnosisSuggestion;
import com.xikang.ai.diagnosis.service.AiDiagnosisService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Diagnosis Controller - AI诊断控制器
 */
@RestController
@RequestMapping("/api/ai/diagnosis")
@RequiredArgsConstructor
public class AiDiagnosisController {

    private final AiDiagnosisService aiDiagnosisService;

    /**
     * 诊断解读
     */
    @PostMapping("/interpret")
    public Result<Map<String, Object>> interpret(@RequestBody Map<String, Object> examResult) {
        Map<String, Object> result = aiDiagnosisService.interpret(examResult);
        return Result.success(result);
    }

    /**
     * 检查结果分析
     */
    @PostMapping("/exam-analyze")
    public Result<Map<String, Object>> examAnalyze(@RequestBody Map<String, Object> examData) {
        Map<String, Object> result = aiDiagnosisService.examAnalyze(examData);
        return Result.success(result);
    }

    /**
     * 获取诊断建议
     */
    @PostMapping("/suggest")
    public Result<Map<String, Object>> getDiagnosisSuggestions(@RequestBody Map<String, Object> diagnosisRequest) {
        Map<String, Object> suggestions = aiDiagnosisService.getDiagnosisSuggestions(diagnosisRequest);
        return Result.success(suggestions);
    }

    /**
     * 获取诊断建议详情
     */
    @GetMapping("/suggestion/{id}")
    public Result<AiDiagnosisSuggestion> getSuggestion(@PathVariable Long id) {
        AiDiagnosisSuggestion suggestion = aiDiagnosisService.getSuggestion(id);
        return Result.success(suggestion);
    }

    /**
     * 按申请ID获取诊断建议
     */
    @GetMapping("/suggestion/request/{requestId}")
    public Result<AiDiagnosisSuggestion> getSuggestionByRequestId(@PathVariable Long requestId) {
        AiDiagnosisSuggestion suggestion = aiDiagnosisService.getSuggestionByRequestId(requestId);
        return Result.success(suggestion);
    }
}
