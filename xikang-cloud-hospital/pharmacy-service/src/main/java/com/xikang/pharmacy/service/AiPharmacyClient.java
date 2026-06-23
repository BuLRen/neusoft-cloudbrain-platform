package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Pharmacy Client - 调用 AI 药房服务
 */
@Slf4j
@Service
public class AiPharmacyClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiPharmacyBaseUrl;

    public AiPharmacyClient(@Value("${services.ai-pharmacy.base-url:http://localhost:8104}") String aiPharmacyBaseUrl) {
        this.aiPharmacyBaseUrl = aiPharmacyBaseUrl;
    }

    public Map<String, Object> createFollowUpPlan(Long patientId, Long registerId, Long prescriptionId) {
        Map<String, Object> request = new HashMap<>();
        request.put("patientId", patientId);
        request.put("registerId", registerId);
        request.put("prescriptionId", prescriptionId);

        try {
            Map response = restTemplate.postForObject(
                aiPharmacyBaseUrl + "/api/ai/pharmacy/followup",
                request,
                Map.class
            );
            if (response == null) {
                throw new BusinessException(500, "AI药房服务无响应");
            }

            Object code = response.get("code");
            if (!(code instanceof Number) || ((Number) code).intValue() != Result.SUCCESS_CODE) {
                String message = response.get("message") instanceof String ? (String) response.get("message") : "AI药房服务创建随访计划失败";
                throw new BusinessException(500, message);
            }

            Object data = response.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Map<String, Object> result = new HashMap<>();
                dataMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
            return new HashMap<>();
        } catch (RestClientException e) {
            log.warn("调用AI药房服务失败 | registerId={}, prescriptionId={}", registerId, prescriptionId, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }
}
