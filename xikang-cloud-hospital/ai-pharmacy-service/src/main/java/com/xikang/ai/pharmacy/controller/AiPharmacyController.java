package com.xikang.ai.pharmacy.controller;

import com.xikang.ai.pharmacy.entity.AiFollowUpPlan;
import com.xikang.ai.pharmacy.entity.AiFollowUpRecord;
import com.xikang.ai.pharmacy.service.AiPharmacyService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Pharmacy Controller - AI药房控制器
 */
@RestController
@RequestMapping("/api/ai/pharmacy")
@RequiredArgsConstructor
public class AiPharmacyController {

    private final AiPharmacyService aiPharmacyService;

    /**
     * 创建随访计划
     */
    @PostMapping("/followup")
    public Result<Map<String, Object>> createFollowUpPlan(@RequestBody Map<String, Object> prescriptionInfo) {
        Map<String, Object> result = aiPharmacyService.createFollowUpPlan(prescriptionInfo);
        return Result.success(result);
    }

    /**
     * 获取用药指导
     */
    @PostMapping("/guide")
    public Result<Map<String, Object>> getMedicationGuide(@RequestBody Map<String, Object> drugInfo) {
        Map<String, Object> guide = aiPharmacyService.getMedicationGuide(drugInfo);
        return Result.success(guide);
    }

    /**
     * 生成处方级用药指导单（真 AI 调用，含降级）
     * <p>请求体由 pharmacy-service 组装：{registerId, patientName, diagnosis, items:[...]}
     */
    @PostMapping("/medication-guide")
    public Result<Map<String, Object>> generateMedicationGuide(@RequestBody Map<String, Object> ctx) {
        Map<String, Object> guide = aiPharmacyService.generateMedicationGuide(ctx);
        return Result.success(guide);
    }

    /**
     * 处方审核
     */
    @PostMapping("/review")
    public Result<Map<String, Object>> reviewPrescription(@RequestBody Map<String, Object> prescription) {
        Map<String, Object> result = aiPharmacyService.reviewPrescription(prescription);
        return Result.success(result);
    }

    /**
     * 记录随访反馈
     */
    @PostMapping("/followup/{planId}/feedback")
    public Result<Void> recordFollowUpFeedback(
            @PathVariable Long planId,
            @RequestBody Map<String, Object> feedback) {
        aiPharmacyService.recordFollowUpFeedback(planId, feedback);
        return Result.success();
    }

    /**
     * 获取随访计划
     */
    @GetMapping("/followup/{id}")
    public Result<AiFollowUpPlan> getFollowUpPlan(@PathVariable Long id) {
        AiFollowUpPlan plan = aiPharmacyService.getFollowUpPlan(id);
        return Result.success(plan);
    }

    /**
     * 获取患者的随访计划
     */
    @GetMapping("/followup/patient/{patientId}")
    public Result<List<AiFollowUpPlan>> getPatientFollowUpPlans(@PathVariable Long patientId) {
        List<AiFollowUpPlan> plans = aiPharmacyService.getPatientFollowUpPlans(patientId);
        return Result.success(plans);
    }

    /**
     * 获取随访记录
     */
    @GetMapping("/followup/{planId}/records")
    public Result<List<AiFollowUpRecord>> getFollowUpRecords(@PathVariable Long planId) {
        List<AiFollowUpRecord> records = aiPharmacyService.getFollowUpRecords(planId);
        return Result.success(records);
    }
}
