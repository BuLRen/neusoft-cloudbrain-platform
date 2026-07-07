package com.xikang.physician.client;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * physician-service 调 payment-service 内部 API（开单出账、确诊/结束看诊卡点）。
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
        return postForMap("/api/payment/internal/items", body, "推送医技费用失败");
    }

    /**
     * 开完处方后推送药品费（幂等）。
     * <p>category=2（药品费），与 pharmacy-service 保持一致；payment-service 走 ON CONFLICT DO NOTHING，
     * 所以即使 patient 已经在"我的处方"页触发过 pharmacy-service 的 ensureMedicationFeeCreated，这里再调也只会返回既有行。
     * <p>金额 ≤ 0 视为数据异常：payment-service 内部会判 amount 必须 ≥ 0，
     * 所以此处给 0 时 payment-service 会落 status=1（已结清）—— 为了不让 endVisit 的 assertAllPaid 卡住，
     * 这里也把 0 金额按 status=1 处理（与 pharmacy-service ensureMedicationFeeCreated 一致）。
     */
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
        // 金额为 0 直接视为已结清，避免 payMedication 因 amount<=0 抛异常卡死后续流程
        body.put("initialStatus", zeroAmount ? 1 : 0);
        body.put("remark", "医生开药后出账");
        return postForMap("/api/payment/internal/items", body, "推送药品费失败");
    }

    public void assertAllPaid(Long registerId) {
        Map<String, Object> status = getForMap(
                "/api/payment/internal/check-paid/register/{registerId}",
                Map.of("registerId", registerId),
                "查询缴费状态失败");
        Object allPaid = status.get("allPaid");
        if (Boolean.TRUE.equals(allPaid)) {
            return;
        }
        Object pendingAmount = status.get("pendingAmount");
        String amountText = pendingAmount != null ? pendingAmount.toString() : "0";
        throw new BusinessException(4001,
                "该患者有待支付费用（¥" + amountText + "），请先完成缴费后再继续");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> postForMap(String path, Map<String, Object> request, String failMessage) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    paymentBaseUrl + path, request, Map.class);
            return extractData(response, failMessage);
        } catch (RestClientException e) {
            log.warn("调 payment-service 失败 | path={}", path, e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> getForMap(String pathTemplate, Map<String, ?> uriVars, String failMessage) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    paymentBaseUrl + pathTemplate, Map.class, uriVars);
            return extractData(response, failMessage);
        } catch (RestClientException e) {
            log.warn("调 payment-service 失败 | path={}", pathTemplate, e);
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
