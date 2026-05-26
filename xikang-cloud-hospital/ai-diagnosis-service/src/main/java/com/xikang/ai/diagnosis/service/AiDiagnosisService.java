package com.xikang.ai.diagnosis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.ai.diagnosis.entity.AiDiagnosisSuggestion;
import com.xikang.ai.diagnosis.mapper.AiDiagnosisSuggestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI Diagnosis Service - AI诊断服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDiagnosisService {

    private final AiDiagnosisSuggestionMapper aiDiagnosisSuggestionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 诊断推荐
     */
    public Map<String, Object> interpret(Map<String, Object> examResult) {
        log.info("AI诊断解读: {}", examResult);

        Long patientId = examResult.get("patientId") != null
            ? ((Number) examResult.get("patientId")).longValue()
            : null;
        Long registerId = examResult.get("registerId") != null
            ? ((Number) examResult.get("registerId")).longValue()
            : null;
        String resultType = (String) examResult.getOrDefault("resultType", "diagnosis");

        // TODO: 调用AI模型进行诊断解读
        Map<String, Object> result = new HashMap<>();
        result.put("primaryDiagnosis", "上呼吸道感染");
        result.put("differentialDiagnoses", List.of("病毒性感冒", "细菌性上呼吸道感染"));
        result.put("confidence", 0.85);
        result.put("recommendedTests", List.of("血常规", "CRP"));
        result.put("icdCodes", List.of(
            Map.of("code", "J06.9", "description", "急性上呼吸道感染"),
            Map.of("code", "J20.9", "description", "急性支气管炎")
        ));

        // 保存诊断建议
        saveDiagnosisSuggestion(patientId, registerId, "diagnosis", null, result);

        return result;
    }

    /**
     * 检查结果分析
     */
    public Map<String, Object> examAnalyze(Map<String, Object> examData) {
        log.info("AI检查分析: {}", examData);

        Long patientId = examData.get("patientId") != null
            ? ((Number) examData.get("patientId")).longValue()
            : null;
        Long registerId = examData.get("registerId") != null
            ? ((Number) examData.get("registerId")).longValue()
            : null;
        Long requestId = examData.get("requestId") != null
            ? ((Number) examData.get("requestId")).longValue()
            : null;
        String examType = (String) examData.getOrDefault("examType", "check");

        // TODO: 调用AI模型进行影像/检验结果分析
        Map<String, Object> result = new HashMap<>();
        result.put("abnormalIndicators", List.of(
            Map.of("name", "白细胞", "value", "12.5", "reference", "4-10", "status", "high"),
            Map.of("name", "中性粒细胞比例", "value", "75%", "reference", "50-70%", "status", "high")
        ));
        result.put("riskLevel", "medium");
        result.put("analysisReport", "白细胞计数升高，提示可能存在细菌感染，建议结合临床症状考虑抗生素治疗。");
        result.put("suggestions", List.of(
            "结合临床症状进行诊断",
            "必要时进行病原学检查",
            "根据情况决定是否使用抗生素"
        ));

        // 保存诊断建议
        saveDiagnosisSuggestion(patientId, registerId, examType, requestId, result);

        return result;
    }

    /**
     * 获取诊断建议
     */
    public Map<String, Object> getDiagnosisSuggestions(Map<String, Object> diagnosisRequest) {
        log.info("获取诊断建议: {}", diagnosisRequest);

        // TODO: 调用AI模型获取诊断建议
        Map<String, Object> suggestions = new HashMap<>();
        suggestions.put("primaryDiagnosis", "上呼吸道感染");
        suggestions.put("differentialDiagnoses", List.of("病毒性感冒", "细菌性上呼吸道感染"));
        suggestions.put("confidence", 0.85);
        suggestions.put("recommendedTests", List.of("血常规", "CRP"));

        return suggestions;
    }

    /**
     * 保存诊断建议
     */
    private void saveDiagnosisSuggestion(Long patientId, Long registerId, String requestType,
            Long requestId, Map<String, Object> result) {
        try {
            AiDiagnosisSuggestion suggestion = new AiDiagnosisSuggestion();
            suggestion.setPatientId(patientId);
            suggestion.setRegisterId(registerId);
            suggestion.setRequestType(requestType);
            suggestion.setRequestId(requestId);

            List<Map<String, Object>> abnormalIndicators = (List<Map<String, Object>>) result.get("abnormalIndicators");
            if (abnormalIndicators != null) {
                suggestion.setAbnormalIndicators(objectMapper.writeValueAsString(abnormalIndicators));
            }

            suggestion.setRiskLevel((String) result.getOrDefault("riskLevel", "low"));
            suggestion.setAnalysisReport((String) result.get("analysisReport"));
            suggestion.setSuggestions(objectMapper.writeValueAsString(result.get("suggestions")));
            suggestion.setStatus(1);
            suggestion.setCreateTime(LocalDateTime.now());

            aiDiagnosisSuggestionMapper.insert(suggestion);
        } catch (Exception e) {
            log.error("保存诊断建议失败", e);
        }
    }

    /**
     * 获取诊断建议
     */
    public AiDiagnosisSuggestion getSuggestion(Long id) {
        return aiDiagnosisSuggestionMapper.selectById(id);
    }

    /**
     * 按申请ID获取诊断建议
     */
    public AiDiagnosisSuggestion getSuggestionByRequestId(Long requestId) {
        return aiDiagnosisSuggestionMapper.selectByRequestId(requestId);
    }
}
