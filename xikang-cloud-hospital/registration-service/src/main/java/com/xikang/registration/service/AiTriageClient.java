package com.xikang.registration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI Triage Client - AI导诊服务调用客户端
 * 用于分诊台调用AI导诊服务
 */
@Slf4j
@Service
public class AiTriageClient {

    /**
     * 调用AI导诊服务分析症状
     */
    public Map<String, Object> analyzeSymptoms(String symptoms, Long patientId) {
        log.info("调用AI导诊服务: symptoms={}, patientId={}", symptoms, patientId);

        // TODO: 实际调用 ai-triage-service
        // 这里使用模拟实现
        // 实际应该使用 RestTemplate 或 Feign 调用 ai-triage-service

        // 模拟返回
        Map<String, Object> result = new HashMap<>();
        result.put("recommendedDepartment", getRecommendedDepartment(symptoms));
        result.put("recommendedDepartmentId", 1L);
        result.put("riskLevel", getRiskLevel(symptoms));
        result.put("recommendedDoctors", getRecommendedDoctors(symptoms));
        result.put("aiAnalysis", Map.of(
            "possibleConditions", getPossibleConditions(symptoms),
            "suggestedExaminations", List.of("血常规", "CRP", "体温测量"),
            "selfCareAdvice", "注意休息，多饮水"
        ));

        return result;
    }

    private String getRecommendedDepartment(String symptoms) {
        String lowerSymptoms = symptoms.toLowerCase();
        if (lowerSymptoms.contains("头痛") || lowerSymptoms.contains("发热") || lowerSymptoms.contains("感冒")) {
            return "内科";
        } else if (lowerSymptoms.contains("骨折") || lowerSymptoms.contains("扭伤") || lowerSymptoms.contains("外伤")) {
            return "外科";
        } else if (lowerSymptoms.contains("皮疹") || lowerSymptoms.contains("皮肤")) {
            return "皮肤科";
        } else if (lowerSymptoms.contains("牙") || lowerSymptoms.contains("口腔")) {
            return "口腔科";
        } else if (lowerSymptoms.contains("眼") || lowerSymptoms.contains("视力")) {
            return "眼科";
        }
        return "内科";
    }

    private String getRiskLevel(String symptoms) {
        String lowerSymptoms = symptoms.toLowerCase();
        if (lowerSymptoms.contains("胸痛") || lowerSymptoms.contains("呼吸困难") ||
            lowerSymptoms.contains("大出血") || lowerSymptoms.contains("昏迷")) {
            return "high";
        } else if (lowerSymptoms.contains("高热") || lowerSymptoms.contains("持续疼痛")) {
            return "medium";
        }
        return "low";
    }

    private List<Map<String, Object>> getRecommendedDoctors(String symptoms) {
        List<Map<String, Object>> doctors = new ArrayList<>();
        doctors.add(Map.of("id", 101L, "name", "张医生", "title", "主任医师", "department", "内科"));
        doctors.add(Map.of("id", 102L, "name", "李医生", "title", "副主任医师", "department", "内科"));
        return doctors;
    }

    private List<String> getPossibleConditions(String symptoms) {
        String lowerSymptoms = symptoms.toLowerCase();
        if (lowerSymptoms.contains("头痛") && lowerSymptoms.contains("发热")) {
            return List.of("上呼吸道感染", "病毒性感冒", "细菌性感染");
        } else if (lowerSymptoms.contains("咳嗽")) {
            return List.of("支气管炎", "肺炎", "上呼吸道感染");
        }
        return List.of("待进一步检查");
    }
}
