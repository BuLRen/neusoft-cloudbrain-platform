package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Pharmacy Client - 调用 AI 药房服务
 */
@Slf4j
@Service
public class AiPharmacyClient {

    private final RestTemplate restTemplate;
    private final String aiPharmacyBaseUrl;

    public AiPharmacyClient(
            @Qualifier("aiPharmacyRestTemplate") RestTemplate restTemplate,
            @Value("${services.ai-pharmacy.base-url:http://localhost:8104}") String aiPharmacyBaseUrl) {
        this.restTemplate = restTemplate;
        this.aiPharmacyBaseUrl = aiPharmacyBaseUrl;
    }

    public Map<String, Object> createFollowUpPlan(Long patientId, Long registerId, Long prescriptionId) {
        Map<String, Object> request = new HashMap<>();
        request.put("patientId", patientId);
        request.put("registerId", registerId);
        request.put("prescriptionId", prescriptionId);

        return postForMap("/api/ai/pharmacy/followup", request, "AI药房服务创建随访计划失败");
    }

    /**
     * P1-6.1 生成用药指导
     */
    public Map<String, Object> getMedicationGuide(Map<String, Object> drugInfo) {
        return postForMap("/api/ai/pharmacy/guide", drugInfo, "AI药房服务生成用药指导失败");
    }

    /**
     * P1-4.1 AI 处方审核
     */
    public Map<String, Object> reviewPrescription(Map<String, Object> prescription) {
        return postForMap("/api/ai/pharmacy/review", prescription, "AI药房服务处方审核失败");
    }

    /**
     * P1-6.2 获取患者随访计划列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPatientFollowUpPlans(Long patientId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                aiPharmacyBaseUrl + "/api/ai/pharmacy/followup/patient/" + patientId,
                Map.class
            );
            Object data = extractData(response, "AI药房服务获取随访计划失败");
            if (data instanceof List<?> list) {
                List<Map<String, Object>> result = new java.util.ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> r = new HashMap<>();
                        m.forEach((k, v) -> r.put(String.valueOf(k), v));
                        result.add(r);
                    }
                }
                return result;
            }
            return java.util.Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("调用AI药房服务获取随访计划失败 | patientId={}", patientId, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    /**
     * P2-6.4 录入随访反馈
     */
    public void submitFollowUpFeedback(Long planId, Map<String, Object> feedback) {
        try {
            restTemplate.postForObject(
                aiPharmacyBaseUrl + "/api/ai/pharmacy/followup/" + planId + "/feedback",
                feedback,
                Map.class
            );
        } catch (RestClientException e) {
            log.warn("调用AI药房服务录入随访反馈失败 | planId={}", planId, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> postForMap(String path, Map<String, Object> body, String failMessage) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                aiPharmacyBaseUrl + path,
                body,
                Map.class
            );
            return extractData(response, failMessage);
        } catch (RestClientException e) {
            log.warn("调用AI药房服务失败 | path={}", path, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response, String failMessage) {
        if (response == null) {
            throw new BusinessException(500, "AI药房服务无响应");
        }
        Object code = response.get("code");
        if (!(code instanceof Number) || ((Number) code).intValue() != Result.SUCCESS_CODE) {
            Object msg = response.get("message");
            throw new BusinessException(500, msg instanceof String ? (String) msg : failMessage);
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> result = new HashMap<>();
            dataMap.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new HashMap<>();
    }
}
