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
import java.util.Map;

/**
 * Payment Client - pharmacy-service 调 payment-service 内部 API（v3.2 §4.2）
 *
 * 仅两个用途：
 *   - ensureMedicationFeeCreated → POST /api/payment/internal/items（推送药品费，幂等）
 *   - isMedicationPaid / assertMedicationPaid → GET  /api/payment/internal/items/by-register
 *
 * 复用 paymentRestTemplate（短超时），不引入 Feign/Nacos 依赖（与 pharmacy-service 现有约定一致）。
 */
@Slf4j
@Service
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentBaseUrl;

    public PaymentClient(
            @Qualifier("paymentRestTemplate") RestTemplate restTemplate,
            @Value("${services.payment-service.url:http://localhost:8096}") String paymentBaseUrl) {
        this.restTemplate = restTemplate;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    /**
     * 推送药品费（幂等）。
     * 返回 payment-service 的 data：{ id, created }
     */
    public Map<String, Object> createMedicationFee(Long registerId, Long patientId, String patientName,
                                                    java.math.BigDecimal amount, int initialStatus, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("registerId", registerId);
        body.put("patientId", patientId);
        body.put("patientName", patientName);
        body.put("itemCode", "MEDICATION_FEE");
        body.put("categoryId", 2);
        body.put("categoryName", "药品费");
        body.put("itemId", 0);
        body.put("itemName", "药品费");
        body.put("quantity", 1);
        body.put("unitPrice", amount);
        body.put("totalAmount", amount);
        body.put("initialStatus", initialStatus);
        body.put("remark", remark);
        return postForMap("/api/payment/internal/items", body, "推送药品费失败");
    }

    /**
     * 查挂号下药品费状态。
     * 返回 data：{ id, status, ... }；不存在返回 data.id == null。
     */
    public Map<String, Object> getMedicationFeeByRegister(Long registerId) {
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map<String, Object> response = restTemplate.getForObject(
                    paymentBaseUrl + "/api/payment/internal/items/by-register?registerId={rid}&itemCode=MEDICATION_FEE",
                    Map.class,
                    Map.of("rid", registerId)
            );
            return extractData(response, "查询药品费失败");
        } catch (RestClientException e) {
            log.warn("查询药品费失败 | registerId={}", registerId, e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postForMap(String path, Map<String, Object> request, String failMessage) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    paymentBaseUrl + path,
                    request,
                    Map.class
            );
            return extractData(response, failMessage);
        } catch (RestClientException e) {
            log.warn("调 payment-service 失败 | path={}", path, e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map<String, Object> response, String failMessage) {
        if (response == null) {
            throw new BusinessException(500, "支付服务无响应");
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
