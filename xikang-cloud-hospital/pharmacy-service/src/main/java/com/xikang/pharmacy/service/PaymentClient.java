package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.pharmacy.client.PaymentFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * pharmacy-service 调 payment-service 内部 API（经 gateway-service → Nacos → payment-service）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentClient {

    private final PaymentFeignClient paymentFeignClient;

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
        return postForMap(body, "推送药品费失败");
    }

    public Map<String, Object> getMedicationFeeByRegister(Long registerId) {
        try {
            return extractData(
                    paymentFeignClient.getItemByRegister(registerId, "MEDICATION_FEE"),
                    "查询药品费失败");
        } catch (FeignException e) {
            log.warn("查询药品费失败 | registerId={}", registerId, e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    private Map<String, Object> postForMap(Map<String, Object> request, String failMessage) {
        try {
            return extractData(paymentFeignClient.createItem(request), failMessage);
        } catch (FeignException e) {
            log.warn("调 payment-service 失败 | op=createItem", e);
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
