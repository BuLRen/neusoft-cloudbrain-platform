package com.xikang.ai.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.triage.entity.AiTriageRecord;
import com.xikang.ai.triage.mapper.AiTriageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Triage Service - AI导诊服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTriageService {

    private final AiTriageRecordMapper aiTriageRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 症状分析并推荐科室
     */
    public Map<String, Object> analyzeSymptoms(Map<String, Object> symptomsData) {
        log.info("AI分析症状: {}", symptomsData);

        String symptoms = (String) symptomsData.getOrDefault("symptoms", "");
        Long patientId = symptomsData.get("patientId") != null
            ? ((Number) symptomsData.get("patientId")).longValue()
            : null;
        String sessionId = (String) symptomsData.getOrDefault("sessionId", UUID.randomUUID().toString());

        // TODO: 调用AI模型进行症状分析
        // 这里使用模拟实现，实际应该调用 Spring AI 或其他AI服务

        // 模拟返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("recommendedDepartment", "内科");
        result.put("recommendedDepartmentId", 1L);
        result.put("riskLevel", "low");
        result.put("recommendedDoctors", List.of(
            Map.of("id", 101L, "name", "张医生", "title", "主任医师"),
            Map.of("id", 102L, "name", "李医生", "title", "副主任医师")
        ));
        result.put("aiAnalysis", Map.of(
            "possibleConditions", List.of("普通感冒", "轻度上呼吸道感染"),
            "suggestedExaminations", List.of("血常规", "CRP检测"),
            "selfCareAdvice", "多休息，多喝水"
        ));
        result.put("sessionId", sessionId);

        // 保存导诊记录
        saveTriageRecord(patientId, sessionId, symptoms, result);

        return result;
    }

    /**
     * 获取导诊建议（根据科室）
     */
    public Map<String, Object> getDepartmentRecommendation(Map<String, Object> request) {
        String department = (String) request.getOrDefault("department", "");
        String symptoms = (String) request.getOrDefault("symptoms", "");

        // TODO: 调用AI模型获取科室推荐
        Map<String, Object> result = new HashMap<>();
        result.put("department", department);
        result.put("specialists", List.of(
            Map.of("id", 101L, "name", "张医生", "title", "主任医师", "available", true)
        ));
        result.put("waitTime", "约30分钟");

        return result;
    }

    /**
     * 导诊对话
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest) {
        log.info("导诊对话请求: {}", chatRequest);

        String message = (String) chatRequest.getOrDefault("message", "");
        String sessionId = (String) chatRequest.getOrDefault("sessionId", UUID.randomUUID().toString());

        // TODO: 调用AI模型进行多轮对话
        Map<String, Object> response = new HashMap<>();
        response.put("reply", "感谢您的描述。请问您还有哪些不适症状？");
        response.put("sessionId", sessionId);
        response.put("suggestions", List.of("继续描述症状", "查看推荐科室", "结束问诊"));

        return response;
    }

    /**
     * 保存导诊记录
     */
    private void saveTriageRecord(Long patientId, String sessionId, String symptoms, Map<String, Object> result) {
        try {
            AiTriageRecord record = new AiTriageRecord();
            record.setPatientId(patientId);
            record.setSessionId(sessionId);
            record.setSymptoms(symptoms);
            record.setSymptomsJson(objectMapper.writeValueAsString(Map.of("symptoms", symptoms)));

            Map<String, Object> analysis = (Map<String, Object>) result.get("aiAnalysis");
            if (analysis != null) {
                record.setPossibleConditions(objectMapper.writeValueAsString(analysis.get("possibleConditions")));
                record.setSuggestedExaminations(objectMapper.writeValueAsString(analysis.get("suggestedExaminations")));
            }

            record.setRecommendedDepartment((String) result.get("recommendedDepartment"));
            record.setRecommendedDepartmentId(result.get("recommendedDepartmentId") != null
                ? ((Number) result.get("recommendedDepartmentId")).longValue()
                : null);
            record.setRiskLevel((String) result.get("riskLevel"));
            record.setAiAnalysis(objectMapper.writeValueAsString(result.get("aiAnalysis")));
            record.setStatus(1);
            record.setCreateTime(LocalDateTime.now());

            aiTriageRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存导诊记录失败", e);
        }
    }

    /**
     * 获取导诊记录
     */
    public AiTriageRecord getTriageRecord(Long id) {
        return aiTriageRecordMapper.selectById(id);
    }

    /**
     * 获取患者的导诊记录
     */
    public List<AiTriageRecord> getPatientTriageRecords(Long patientId) {
        return aiTriageRecordMapper.selectByPatientId(patientId);
    }
}
