package com.xikang.medtech.client;

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
import java.util.Objects;

/**
 * medtech-service 调 payment-service（执行前校验、列表缴费状态）。
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

    public void assertItemPaid(Long registerId, String itemCode, Long sourceId, String feeLabel) {
        Map<String, Object> status = checkPaidByItem(registerId, itemCode, sourceId);
        if (Boolean.TRUE.equals(status.get("paid"))) {
            return;
        }
        if (!Boolean.TRUE.equals(status.get("exists"))) {
            throw new BusinessException(4001, "该申请尚未生成" + feeLabel + "账单，请患者先完成缴费");
        }
        throw new BusinessException(4001, "患者尚未支付" + feeLabel + "，无法执行");
    }

    public Map<String, Map<String, Object>> loadExpenseIndex(List<Long> registerIds) {
        if (registerIds == null || registerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Long registerId : registerIds.stream().filter(Objects::nonNull).distinct().toList()) {
            for (Map<String, Object> item : listItemsByRegister(registerId)) {
                String itemCode = item.get("itemCode") != null ? item.get("itemCode").toString() : "";
                Object sourceId = item.get("sourceId");
                if (sourceId == null) {
                    continue;
                }
                index.put(expenseKey(registerId, itemCode, sourceId), item);
            }
        }
        return index;
    }

    public static String expenseKey(Long registerId, String itemCode, Object sourceId) {
        return registerId + ":" + itemCode + ":" + sourceId;
    }

    public static void applyPaymentFields(Map<String, Object> target, Map<String, Object> expense) {
        if (expense == null || expense.get("id") == null) {
            target.put("paid", false);
            target.put("payStatus", 0);
            target.put("payStatusText", "未缴费");
            target.put("feeAmount", null);
            return;
        }
        Integer status = expense.get("status") instanceof Number n ? n.intValue() : 0;
        target.put("paid", status == 1);
        target.put("payStatus", status);
        target.put("payStatusText", payStatusText(status));
        target.put("feeAmount", expense.get("totalAmount"));
    }

    private Map<String, Object> checkPaidByItem(Long registerId, String itemCode, Long sourceId) {
        return getForMap(
                "/api/payment/internal/check-paid/item?registerId={registerId}&itemCode={itemCode}&sourceId={sourceId}",
                Map.of("registerId", registerId, "itemCode", itemCode, "sourceId", sourceId),
                "查询缴费状态失败");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> listItemsByRegister(Long registerId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    paymentBaseUrl + "/api/payment/internal/items?registerId={registerId}",
                    Map.class,
                    Map.of("registerId", registerId));
            Object data = response != null ? response.get("data") : null;
            if (!(data instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        } catch (RestClientException e) {
            log.warn("查询费用明细失败 | registerId={}", registerId, e);
            return List.of();
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

    private static String payStatusText(int status) {
        return switch (status) {
            case 1 -> "已缴费";
            case 2 -> "已退款";
            case 3 -> "已作废";
            default -> "未缴费";
        };
    }
}
