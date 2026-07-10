package com.xikang.physician.client;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * physician-service 调 payment-service 内部 API（Feign 直连，地址见 PAYMENT_SERVICE_URL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentClient {

    private final PaymentFeignClient paymentFeignClient;

    public Map<String, Object> createTechFee(Long registerId, Long patientId, String patientName,
                                             String itemCode, Long sourceId, Long techId, String techName,
                                             BigDecimal unitPrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("registerId", registerId);
        body.put("patientId", patientId);
        body.put("patientName", patientName);
        body.put("itemCode", itemCode);
        body.put("sourceId", sourceId);
        body.put("categoryId", categoryIdFor(itemCode));
        body.put("categoryName", categoryNameFor(itemCode));
        body.put("itemId", techId);
        body.put("itemName", techName);
        body.put("quantity", 1);
        body.put("unitPrice", unitPrice != null ? unitPrice : BigDecimal.ZERO);
        body.put("totalAmount", unitPrice != null ? unitPrice : BigDecimal.ZERO);
        return postForMap(body, "推送医技费用失败");
    }

    public Map<String, Object> createMedicationFee(Long registerId, Long patientId, String patientName,
                                                   BigDecimal amount) {
        boolean zeroAmount = amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
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
        body.put("unitPrice", zeroAmount ? BigDecimal.ZERO : amount);
        body.put("totalAmount", zeroAmount ? BigDecimal.ZERO : amount);
        body.put("initialStatus", zeroAmount ? 1 : 0);
        body.put("remark", "医生开药后出账");
        return postForMap(body, "推送药品费失败");
    }

    public void assertAllPaid(Long registerId) {
        Map<String, Object> status = getCheckPaidByRegister(registerId);
        Object allPaid = status.get("allPaid");
        if (Boolean.TRUE.equals(allPaid)) {
            return;
        }
        Object pendingAmount = status.get("pendingAmount");
        String amountText = pendingAmount != null ? pendingAmount.toString() : "0";
        throw new BusinessException(4001,
                "该患者有待支付费用（¥" + amountText + "），请先完成缴费后再继续");
    }

    private Map<String, Object> postForMap(Map<String, Object> request, String failMessage) {
        try {
            return extractData(paymentFeignClient.createItem(request), failMessage);
        } catch (FeignException e) {
            log.warn("调 payment-service 失败 | op=createItem", e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    private Map<String, Object> getCheckPaidByRegister(Long registerId) {
        try {
            return extractData(paymentFeignClient.checkPaidByRegister(registerId), "查询缴费状态失败");
        } catch (FeignException e) {
            log.warn("调 payment-service 失败 | op=checkPaid registerId={}", registerId, e);
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

    private static int categoryIdFor(String itemCode) {
        return switch (itemCode) {
            case "CHECK_FEE" -> 3;
            case "INSPECTION_FEE" -> 4;
            case "DISPOSAL_FEE" -> 5;
            default -> 0;
        };
    }

    private static String categoryNameFor(String itemCode) {
        return switch (itemCode) {
            case "CHECK_FEE" -> "检查费";
            case "INSPECTION_FEE" -> "检验费";
            case "DISPOSAL_FEE" -> "处置费";
            default -> itemCode;
        };
    }
}
