package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.feign.PaymentFeignClient;
import com.xikang.registration.mapper.ExpenseRecordMapper;
import com.xikang.registration.mapper.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refund Service - 退费服务（v3.2：全部改 Feign 调 payment-service）
 *
 * 边界确认（v3.2 R4）：本类不碰 register 表，无需回调 registration。
 * 只有 cancelRegistration 自己动 register.visit_state=4。
 *
 * v3.2 BLOCKER 修复：读已缴项改 paymentFeignClient.records，避免本地表 stale。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final ExpenseRecordMapper expenseRecordMapper; // 仅 fallback 用
    private final RegistrationMapper registrationMapper;
    private final PaymentFeignClient paymentFeignClient;

    /**
     * 单条费用退费
     */
    @Transactional
    public Map<String, Object> refund(Long expenseRecordId, Long operatorId, String operatorName, String reason) {
        log.info("退费操作 | expenseRecordId={}, operatorId={}, reason={}", expenseRecordId, operatorId, reason);

        Map<String, Object> body = new HashMap<>();
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName);
        body.put("reason", reason);
        Map<String, Object> resp = paymentFeignClient.refund(expenseRecordId, body);
        Map<String, Object> result = unwrapMapData(resp, "退费失败");
        log.info("退费成功 | expenseRecordId={}", expenseRecordId);
        return result;
    }

    /**
     * 按挂号ID退费（全部已缴费项目）
     */
    @Transactional
    public Map<String, Object> refundByRegisterId(Long registerId, Long operatorId, String operatorName, String reason) {
        log.info("按挂号ID退费 | registerId={}, operatorId={}", registerId, operatorId);

        // v3.2：读已支付项改 paymentFeignClient.records（payment-service 拥有 expense_record）
        List<Map<String, Object>> paidItems = fetchPaidItems(registerId, null);
        if (paidItems.isEmpty()) {
            throw new BusinessException(400, "该挂号没有可退费的已缴费记录");
        }

        BigDecimal totalRefunded = BigDecimal.ZERO;
        int count = 0;
        Object accountBalance = null;
        for (Map<String, Object> item : paidItems) {
            Long id = toLong(item.get("id"));
            if (id == null) continue;
            Map<String, Object> body = new HashMap<>();
            body.put("operatorId", operatorId);
            body.put("operatorName", operatorName);
            body.put("reason", reason);
            Map<String, Object> resp = paymentFeignClient.refund(id, body);
            Map<String, Object> data = unwrapMapData(resp, "退费失败");
            count++;
            if (data.get("refundAmount") != null) {
                totalRefunded = totalRefunded.add(new BigDecimal(data.get("refundAmount").toString()));
            }
            accountBalance = data.get("accountBalance");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("registerId", registerId);
        result.put("refundAmount", totalRefunded);
        result.put("refundedCount", count);
        result.put("accountBalance", accountBalance);
        log.info("按挂号ID退费完成 | registerId={}, count={}", registerId, paidItems.size());
        return result;
    }

    /**
     * 按挂号ID仅退挂号费（用于患者取消挂号）
     * 一次只退一条 REGISTRATION_FEE，避免历史脏数据导致重复退 N 次。
     */
    @Transactional
    public Map<String, Object> refundRegistrationFee(Long registerId, Long operatorId, String operatorName, String reason) {
        // v3.2：查 REGISTRATION_FEE 已支付项，改用 paymentFeignClient.records
        List<Map<String, Object>> paidRegistrationFees = fetchPaidItems(registerId, "REGISTRATION_FEE");

        if (paidRegistrationFees.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("registerId", registerId);
            result.put("refundAmount", BigDecimal.ZERO);
            result.put("refundedCount", 0);
            result.put("message", "没有可退款的已缴挂号费");
            return result;
        }

        // 取 payTime 最早的一条
        Map<String, Object> paidFee = paidRegistrationFees.stream()
                .min((a, b) -> {
                    LocalDateTime ta = toLocalDateTime(a.get("payTime"));
                    LocalDateTime tb = toLocalDateTime(b.get("payTime"));
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return ta.compareTo(tb);
                })
                .orElse(paidRegistrationFees.get(0));

        Long id = toLong(paidFee.get("id"));
        if (id == null) {
            throw new BusinessException(500, "已缴挂号费缺少 id 字段");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName);
        body.put("reason", reason != null ? reason : "挂号退费");
        Map<String, Object> resp = paymentFeignClient.refund(id, body);
        return unwrapMapData(resp, "退费失败");
    }

    // ============================================================
    // 辅助：通过 Feign 读已支付项（v3.2 BLOCKER-4 修复）
    // ============================================================

    /**
     * 通过 paymentFeignClient.records 读取已支付项。
     * @param itemCode null 表示不限
     * Feign 失败时降级读本地 mapper（双写期兼容，但会 stale）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchPaidItems(Long registerId, String itemCode) {
        try {
            Map<String, Object> resp = paymentFeignClient.records(null, registerId, 1, null, null);
            Object data = resp.get("data");
            if (!(data instanceof List<?> list)) return List.of();
            List<Map<String, Object>> all = (List<Map<String, Object>>) (List) list;
            if (itemCode != null) {
                return all.stream()
                        .filter(m -> itemCode.equals(m.get("itemCode")))
                        .toList();
            }
            return all;
        } catch (Exception e) {
            log.warn("Feign 调 payment.records 失败，降级读本地 | registerId={}, err={}", registerId, e.getMessage());
            return fallbackReadPaidLocal(registerId, itemCode);
        }
    }

    private List<Map<String, Object>> fallbackReadPaidLocal(Long registerId, String itemCode) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        return records.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .filter(r -> itemCode == null || itemCode.equals(r.getItemCode()))
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("itemCode", r.getItemCode());
                    m.put("payTime", r.getPayTime());
                    return m;
                })
                .toList();
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime ldt) return ldt;
        if (o instanceof java.time.Instant instant) return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        if (o instanceof java.util.Date d) return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
        if (o instanceof String s) {
            try { return LocalDateTime.parse(s.replace(' ', 'T')); } catch (Exception e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMapData(Map<String, Object> response, String errorMessage) {
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(500, errorMessage);
        }
        return (Map<String, Object>) dataMap;
    }
}
