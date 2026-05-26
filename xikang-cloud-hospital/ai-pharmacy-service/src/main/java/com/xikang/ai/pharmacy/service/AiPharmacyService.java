package com.xikang.ai.pharmacy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.pharmacy.entity.AiFollowUpPlan;
import com.xikang.ai.pharmacy.entity.AiFollowUpRecord;
import com.xikang.ai.pharmacy.mapper.AiFollowUpPlanMapper;
import com.xikang.ai.pharmacy.mapper.AiFollowUpRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Pharmacy Service - AI药房服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPharmacyService {

    private final AiFollowUpPlanMapper aiFollowUpPlanMapper;
    private final AiFollowUpRecordMapper aiFollowUpRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建随访计划
     */
    public Map<String, Object> createFollowUpPlan(Map<String, Object> prescriptionInfo) {
        log.info("AI创建随访计划: {}", prescriptionInfo);

        Long patientId = ((Number) prescriptionInfo.get("patientId")).longValue();
        Long registerId = prescriptionInfo.get("registerId") != null
            ? ((Number) prescriptionInfo.get("registerId")).longValue()
            : null;
        Long prescriptionId = prescriptionInfo.get("prescriptionId") != null
            ? ((Number) prescriptionInfo.get("prescriptionId")).longValue()
            : null;

        // TODO: 调用AI模型分析处方并生成随访计划
        Map<String, Object> plan = new HashMap<>();
        plan.put("planType", "medication");
        plan.put("startDate", LocalDate.now().toString());
        plan.put("endDate", LocalDate.now().plusDays(7).toString());
        plan.put("frequency", "daily");
        plan.put("followUpItems", List.of(
            Map.of("question", "今天是否按时服药？", "type", "yesno"),
            Map.of("question", "是否有不良反应？", "type", "choice", "options", List.of("无", "轻微", "严重")),
            Map.of("question", "症状改善情况？", "type", "scale", "range", "1-10")
        ));
        plan.put("instructions", "请每天按时服药，如有不适应及时联系医生。");
        plan.put("planId", 1L); // 模拟ID

        // 保存随访计划
        Long planId = saveFollowUpPlan(patientId, registerId, prescriptionId, plan);

        Map<String, Object> result = new HashMap<>(plan);
        result.put("planId", planId);
        result.put("status", "created");

        return result;
    }

    /**
     * 获取用药指导
     */
    public Map<String, Object> getMedicationGuide(Map<String, Object> drugInfo) {
        log.info("AI获取用药指导: {}", drugInfo);

        String drugName = (String) drugInfo.getOrDefault("drugName", "");

        // TODO: 调用AI模型生成用药指导
        Map<String, Object> guide = new HashMap<>();
        guide.put("drugName", drugName);
        guide.put("usage", "口服");
        guide.put("dosage", "一次一片，一日三次");
        guide.put("timing", "饭后半小时服用");
        guide.put("duration", "7天");
        guide.put("precautions", List.of(
            "忌烟酒及辛辣食物",
            "儿童用量减半",
            "孕妇慎用"
        ));
        guide.put("sideEffects", List.of(
            "可能出现恶心、头晕",
            "严重不良反应需立即停药并就医"
        ));
        guide.put("storage", "遮光，密封保存");

        return guide;
    }

    /**
     * 处方审核
     */
    public Map<String, Object> reviewPrescription(Map<String, Object> prescription) {
        log.info("AI处方审核: {}", prescription);

        // TODO: 调用AI模型进行处方审核
        Map<String, Object> result = new HashMap<>();
        result.put("hasIssue", false);
        result.put("interactions", List.of());
        result.put("warnings", List.of());
        result.put("recommendations", List.of("处方合理，可正常发药"));
        result.put("审核状态", "通过");

        return result;
    }

    /**
     * 记录随访反馈
     */
    public void recordFollowUpFeedback(Long planId, Map<String, Object> feedback) {
        log.info("记录随访反馈: planId={}, feedback={}", planId, feedback);

        String medicationCompliance = (String) feedback.getOrDefault("medicationCompliance", "good");
        String symptomFeedback = (String) feedback.get("symptomFeedback");
        String sideEffects = (String) feedback.get("sideEffects");
        String recoveryStatus = (String) feedback.get("recoveryStatus");

        AiFollowUpRecord record = new AiFollowUpRecord();
        record.setPlanId(planId);
        record.setPatientId(((Number) feedback.get("patientId")).longValue());
        record.setMedicationCompliance(medicationCompliance);
        record.setSymptomFeedback(symptomFeedback);
        record.setSideEffects(sideEffects);
        record.setRecoveryStatus(recoveryStatus);
        record.setRecordTime(LocalDateTime.now());

        aiFollowUpRecordMapper.insert(record);

        // 更新计划状态
        AiFollowUpPlan plan = aiFollowUpPlanMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus(1); // 进行中
            aiFollowUpPlanMapper.update(plan);
        }
    }

    /**
     * 保存随访计划
     */
    private Long saveFollowUpPlan(Long patientId, Long registerId, Long prescriptionId, Map<String, Object> plan) {
        try {
            AiFollowUpPlan followUpPlan = new AiFollowUpPlan();
            followUpPlan.setPatientId(patientId);
            followUpPlan.setRegisterId(registerId);
            followUpPlan.setPrescriptionId(prescriptionId);
            followUpPlan.setPlanType((String) plan.get("planType"));
            followUpPlan.setStartDate(LocalDate.parse((CharSequence) plan.get("startDate")));
            followUpPlan.setEndDate(LocalDate.parse((CharSequence) plan.get("endDate")));
            followUpPlan.setFrequency((String) plan.get("frequency"));
            followUpPlan.setFollowUpItems(objectMapper.writeValueAsString(plan.get("followUpItems")));
            followUpPlan.setInstructions((String) plan.get("instructions"));
            followUpPlan.setStatus(0); // 待执行
            followUpPlan.setCreateTime(LocalDateTime.now());

            aiFollowUpPlanMapper.insert(followUpPlan);
            return followUpPlan.getId();
        } catch (Exception e) {
            log.error("保存随访计划失败", e);
            return null;
        }
    }

    /**
     * 获取随访计划
     */
    public AiFollowUpPlan getFollowUpPlan(Long id) {
        return aiFollowUpPlanMapper.selectById(id);
    }

    /**
     * 获取患者的随访计划
     */
    public List<AiFollowUpPlan> getPatientFollowUpPlans(Long patientId) {
        return aiFollowUpPlanMapper.selectByPatientId(patientId);
    }

    /**
     * 获取随访记录
     */
    public List<AiFollowUpRecord> getFollowUpRecords(Long planId) {
        return aiFollowUpRecordMapper.selectByPlanId(planId);
    }
}
