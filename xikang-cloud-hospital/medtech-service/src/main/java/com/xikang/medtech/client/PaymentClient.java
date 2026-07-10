package com.xikang.medtech.client;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * medtech-service 调 payment-service 内部 API（Feign 直连，地址见 PAYMENT_SERVICE_URL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentClient {

    private final PaymentFeignClient paymentFeignClient;

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

    public Map<String, Map<String, Object>> loadExpenseIndex(List<Long> registerIds, String itemCode) {
        if (registerIds == null || registerIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = registerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<String> itemCodes = itemCode != null && !itemCode.isBlank() ? List.of(itemCode.trim()) : null;
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> item : listItemsBatch(ids, itemCodes)) {
            Object registerId = item.get("registerId");
            Object sourceId = item.get("sourceId");
            if (registerId == null || sourceId == null) {
                continue;
            }
            String code = item.get("itemCode") != null ? item.get("itemCode").toString() : "";
            index.put(expenseKey(toLong(registerId), code, sourceId), item);
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
        try {
            return extractData(
                    paymentFeignClient.checkPaidByItem(registerId, itemCode, sourceId),
                    "查询缴费状态失败");
        } catch (FeignException e) {
            log.warn("调 payment-service 失败 | op=checkPaid item registerId={}", registerId, e);
            throw new BusinessException(500, "支付服务暂时不可用", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listItemsBatch(List<Long> registerIds, List<String> itemCodes) {
        try {
            Map<String, Object> response = paymentFeignClient.listItemsBatch(registerIds, itemCodes);
            Object data = response != null ? response.get("data") : null;
            if (!(data instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        } catch (FeignException e) {
            log.warn("批量查询费用明细失败 | registerCount={}", registerIds.size(), e);
            return List.of();
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

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.parseLong(value.toString());
        }
        return 0L;
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
