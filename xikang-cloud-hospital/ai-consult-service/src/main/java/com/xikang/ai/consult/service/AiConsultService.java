package com.xikang.ai.consult.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.consult.entity.AiPreVisitRecord;
import com.xikang.ai.consult.mapper.AiPreVisitRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Consult Service - AI预问诊服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConsultService {

    private final AiPreVisitRecordMapper aiPreVisitRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 预问诊
     */
    public Map<String, Object> previsit(Map<String, Object> patientInfo) {
        log.info("AI预问诊: {}", patientInfo);

        Long patientId = ((Number) patientInfo.get("patientId")).longValue();
        Long registerId = patientInfo.get("registerId") != null
            ? ((Number) patientInfo.get("registerId")).longValue()
            : null;
        String sessionId = (String) patientInfo.getOrDefault("sessionId", UUID.randomUUID().toString());

        // TODO: 调用AI模型进行预问诊
        // 模拟返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("chiefComplaint", "头痛、发热3天");
        result.put("presentIllness", "患者3天前开始出现头痛，伴有低热，体温38℃左右");
        result.put("pastHistory", "既往体健，无高血压、糖尿病史");
        result.put("allergyHistory", "无药物过敏史");
        result.put("physicalExamination", "神志清，咽充血，扁桃体I度肿大");
        result.put("preliminaryDiagnosis", "上呼吸道感染");
        result.put("summary", "主诉：头痛、发热3天。现病史：患者3天前开始出现头痛，伴有低热，体温38℃左右。既往史：既往体健。过敏史：无药物过敏史。体格检查：神志清，咽充血，扁桃体I度肿大。");
        result.put("suggestedTests", List.of("血常规", "CRP", "流感抗原检测"));
        result.put("sessionId", sessionId);

        // 保存预问诊记录
        savePreVisitRecord(patientId, registerId, sessionId, result);

        return result;
    }

    /**
     * 生成预问诊摘要
     */
    public Map<String, Object> generateSummary(Map<String, Object> context) {
        log.info("生成预问诊摘要: {}", context);

        // TODO: 调用AI模型生成摘要
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "患者主诉头痛、发热3天，伴低热。体格检查见咽充血。无过敏史。初步诊断：上呼吸道感染。建议检查：血常规、CRP。");
        result.put("structuredData", Map.of(
            "chiefComplaint", "头痛、发热3天",
            "presentIllness", "低热，体温38℃",
            "pastHistory", "既往体健",
            "allergyHistory", "无",
            "physicalExamination", "咽充血，扁桃体I度肿大"
        ));

        return result;
    }

    /**
     * 预问诊对话
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest) {
        log.info("预问诊对话: {}", chatRequest);

        String message = (String) chatRequest.getOrDefault("message", "");
        String sessionId = (String) chatRequest.getOrDefault("sessionId", UUID.randomUUID().toString());

        // TODO: 调用AI模型进行多轮对话
        Map<String, Object> response = new HashMap<>();
        response.put("reply", "请描述一下您的具体症状，例如：什么时候开始不舒服？体温最高多少度？");
        response.put("sessionId", sessionId);
        response.put("nextQuestions", List.of(
            "您的体温变化情况",
            "是否有咳嗽、咽痛等症状",
            "近期是否有接触过感冒患者"
        ));

        return response;
    }

    /**
     * 保存预问诊记录
     */
    private void savePreVisitRecord(Long patientId, Long registerId, String sessionId, Map<String, Object> result) {
        try {
            AiPreVisitRecord record = new AiPreVisitRecord();
            record.setPatientId(patientId);
            record.setRegisterId(registerId);
            record.setSessionId(sessionId);
            record.setChiefComplaint((String) result.get("chiefComplaint"));
            record.setPresentIllness((String) result.get("presentIllness"));
            record.setPastHistory((String) result.get("pastHistory"));
            record.setAllergyHistory((String) result.get("allergyHistory"));
            record.setPhysicalExamination((String) result.get("physicalExamination"));
            record.setPreliminaryDiagnosis((String) result.get("preliminaryDiagnosis"));
            record.setSummary((String) result.get("summary"));
            record.setRawConversation(objectMapper.writeValueAsString(result));
            record.setStatus(1);
            record.setCreateTime(LocalDateTime.now());

            aiPreVisitRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存预问诊记录失败", e);
        }
    }

    /**
     * 获取预问诊记录
     */
    public AiPreVisitRecord getPreVisitRecord(Long id) {
        return aiPreVisitRecordMapper.selectById(id);
    }

    /**
     * 按挂号ID获取预问诊记录
     */
    public AiPreVisitRecord getPreVisitRecordByRegisterId(Long registerId) {
        return aiPreVisitRecordMapper.selectByRegisterId(registerId);
    }
}
