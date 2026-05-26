package com.xikang.ai.triage.controller;

import com.xikang.ai.triage.entity.AiTriageRecord;
import com.xikang.ai.triage.service.AiTriageService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Triage Controller - AI导诊控制器
 */
@RestController
@RequestMapping("/api/ai/triage")
@RequiredArgsConstructor
public class AiTriageController {

    private final AiTriageService aiTriageService;

    /**
     * 症状分析并推荐科室
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody Map<String, Object> symptoms) {
        Map<String, Object> result = aiTriageService.analyzeSymptoms(symptoms);
        return Result.success(result);
    }

    /**
     * 获取科室推荐
     */
    @PostMapping("/department")
    public Result<Map<String, Object>> getDepartmentRecommendation(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = aiTriageService.getDepartmentRecommendation(request);
        return Result.success(result);
    }

    /**
     * 导诊对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> chatRequest) {
        Map<String, Object> response = aiTriageService.chat(chatRequest);
        return Result.success(response);
    }

    /**
     * 获取导诊记录
     */
    @GetMapping("/record/{id}")
    public Result<AiTriageRecord> getTriageRecord(@PathVariable Long id) {
        AiTriageRecord record = aiTriageService.getTriageRecord(id);
        return Result.success(record);
    }

    /**
     * 获取患者的导诊记录
     */
    @GetMapping("/records/patient/{patientId}")
    public Result<List<AiTriageRecord>> getPatientTriageRecords(@PathVariable Long patientId) {
        List<AiTriageRecord> records = aiTriageService.getPatientTriageRecords(patientId);
        return Result.success(records);
    }
}
