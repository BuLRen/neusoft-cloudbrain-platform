package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.pharmacy.client.AiPharmacyFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Pharmacy Client - 调用 AI 药房服务（经 Nacos 服务发现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPharmacyClient {

    private final AiPharmacyFeignClient aiPharmacyFeignClient;

    public Map<String, Object> createFollowUpPlan(Long patientId, Long registerId, Long prescriptionId) {
        Map<String, Object> request = new HashMap<>();
        request.put("patientId", patientId);
        request.put("registerId", registerId);
        request.put("prescriptionId", prescriptionId);

        return postForMap(() -> aiPharmacyFeignClient.createFollowUpPlan(request),
                "/api/ai/pharmacy/followup", "AI药房服务创建随访计划失败");
    }

    /**
     * P1-6.1 生成用药指导
     */
    public Map<String, Object> getMedicationGuide(Map<String, Object> drugInfo) {
        return postForMap(() -> aiPharmacyFeignClient.getMedicationGuide(drugInfo),
                "/api/ai/pharmacy/guide", "AI药房服务生成用药指导失败");
    }

    /**
     * 生成处方级用药指导单（真 AI 调用，ai-pharmacy 内部含降级）。
     */
    public Map<String, Object> generateMedicationGuide(Map<String, Object> ctx) {
        return postForMap(() -> aiPharmacyFeignClient.generateMedicationGuide(ctx),
                "/api/ai/pharmacy/medication-guide", "AI药房服务生成用药指导单失败");
    }

    /**
     * P1-4.1 AI 处方审核
     */
    public Map<String, Object> reviewPrescription(Map<String, Object> prescription) {
        return postForMap(() -> aiPharmacyFeignClient.reviewPrescription(prescription),
                "/api/ai/pharmacy/review", "AI药房服务处方审核失败");
    }

    /**
     * P1-6.2 获取患者随访计划列表
     */
    public List<Map<String, Object>> getPatientFollowUpPlans(Long patientId) {
        try {
            Object data = extractRawData(
                    aiPharmacyFeignClient.getPatientFollowUpPlans(patientId),
                    "AI药房服务获取随访计划失败");
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
        } catch (FeignException e) {
            log.warn("调用AI药房服务获取随访计划失败 | patientId={}", patientId, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    /**
     * P2-6.4 录入随访反馈
     */
    public void submitFollowUpFeedback(Long planId, Map<String, Object> feedback) {
        try {
            aiPharmacyFeignClient.submitFollowUpFeedback(planId, feedback);
        } catch (FeignException e) {
            log.warn("调用AI药房服务录入随访反馈失败 | planId={}", planId, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> postForMap(FeignCall call, String path, String failMessage) {
        try {
            return extractData(call.invoke(), failMessage);
        } catch (FeignException e) {
            log.warn("调用AI药房服务失败 | path={}", path, e);
            throw new BusinessException(500, "AI药房服务暂时不可用", e);
        }
    }

    private Map<String, Object> extractData(Map<String, Object> response, String failMessage) {
        Object data = extractRawData(response, failMessage);
        if (data instanceof Map<?, ?> dataMap) {
            Map<String, Object> result = new HashMap<>();
            dataMap.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new HashMap<>();
    }

    private Object extractRawData(Map<String, Object> response, String failMessage) {
        if (response == null) {
            throw new BusinessException(500, "AI药房服务无响应");
        }
        Object code = response.get("code");
        if (!(code instanceof Number) || ((Number) code).intValue() != Result.SUCCESS_CODE) {
            Object msg = response.get("message");
            throw new BusinessException(500, msg instanceof String ? (String) msg : failMessage);
        }
        return response.get("data");
    }

    @FunctionalInterface
    private interface FeignCall {
        Map<String, Object> invoke();
    }
}
