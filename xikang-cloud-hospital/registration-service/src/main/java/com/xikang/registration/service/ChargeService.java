package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.entity.Register;
import com.xikang.registration.feign.AuthPatientFeignClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge Service - 收费核心服务（v3.2 改造）
 *
 * - payRegistration / payMedication：改 Feign 透传 payment-service（链 G/H 兼容入口）
 * - charge（cashier 集中收费）：保留旧路径，写前申请 write-token 防与 payment.payItem 并发双扣
 * - getPendingCharges：改 Feign 透传
 *
 * 保留 expenseRecordMapper：charge 路径双写期仍需直接读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final RegistrationMapper registrationMapper;
    private final AuthPatientFeignClient authPatientFeignClient;
    private final PaymentFeignClient paymentFeignClient;

    /**
     * 患者按挂号单自助缴费（v3.2：Feign 透传 payment-service.payItem）
     */
    public Map<String, Object> payRegistration(Long registerId) {
        Register register = registrationMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (register.getVisitState() != null && register.getVisitState() == 4) {
            throw new BusinessException(400, "该挂号已退号，不能继续缴费");
        }

        // 通过 payment.internal.items/by-register 找出该挂号的 REGISTRATION_FEE 待缴行
        Map<String, Object> itemResp = paymentFeignClient.getItemByRegister(registerId, "REGISTRATION_FEE");
        Object itemData = itemResp.get("data");
        if (!(itemData instanceof Map<?, ?> itemMap) || itemMap.get("id") == null) {
            throw new BusinessException(400, "没有待缴费的挂号费");
        }
        Integer status = (Integer) itemMap.get("status");
        if (status != null && status != 0) {
            throw new BusinessException(400, "挂号费当前状态不允许支付: " + status);
        }

        Long itemId = Long.valueOf(itemMap.get("id").toString());
        Map<String, Object> payBody = new HashMap<>();
        if (register.getPatientId() != null) {
            payBody.put("operatorId", register.getPatientId().longValue());
        }
        payBody.put("operatorName", "患者余额");

        Map<String, Object> resp = paymentFeignClient.payItem(itemId, payBody);
        Map<String, Object> data = unwrapMapData(resp, "余额扣款失败");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", Boolean.TRUE.equals(data.get("success")));
        result.put("registerId", registerId);
        result.put("amount", data.get("paidAmount"));
        result.put("accountBalance", data.get("accountBalance"));
        result.put("payTime", data.get("payTime"));
        result.put("paymentMessage", "缴费成功");
        // 重新汇总拿到最新 visitState / payStatus
        fillRegistrationStatus(result, registerId);
        log.info("患者自助缴费成功（Feign 透传）| registerId={}, itemId={}", registerId, itemId);
        return result;
    }

    /**
     * 患者自助支付药品费（v3.2：Feign 透传 payment-service.payItem）
     */
    public Map<String, Object> payMedication(Long registerId) {
        Register register = registrationMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }

        Map<String, Object> itemResp = paymentFeignClient.getItemByRegister(registerId, "MEDICATION_FEE");
        Object itemData = itemResp.get("data");
        if (!(itemData instanceof Map<?, ?> itemMap) || itemMap.get("id") == null) {
            throw new BusinessException(400, "未找到药品费账单，请先在患者端查看处方");
        }
        Integer status = (Integer) itemMap.get("status");
        if (status != null && status == 1) {
            throw new BusinessException(400, "药品费已支付，无需重复缴费");
        }
        if (status == null || status != 0) {
            throw new BusinessException(400, "当前药品费账单状态不允许支付");
        }

        Long itemId = Long.valueOf(itemMap.get("id").toString());
        Map<String, Object> payBody = new HashMap<>();
        if (register.getPatientId() != null) {
            payBody.put("operatorId", register.getPatientId().longValue());
        }
        payBody.put("operatorName", "患者余额");

        Map<String, Object> resp = paymentFeignClient.payItem(itemId, payBody);
        Map<String, Object> data = unwrapMapData(resp, "余额扣款失败");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", Boolean.TRUE.equals(data.get("success")));
        result.put("registerId", registerId);
        result.put("amount", data.get("paidAmount"));
        result.put("accountBalance", data.get("accountBalance"));
        result.put("payTime", data.get("payTime"));
        result.put("paymentMessage", "药品费支付成功");
        log.info("患者自助支付药品费成功（Feign 透传）| registerId={}, itemId={}", registerId, itemId);
        return result;
    }

    /**
     * 收费员集中收费（cashier）— v3.2 §2.2 链 F：申请 write-token 后直接读写
     */
    @Transactional
    public Map<String, Object> charge(Map<String, Object> request) {
        Long registerId = ((Number) request.get("registerId")).longValue();
        List<Long> itemIds = request.get("itemIds") != null
            ? (List<Long>) request.get("itemIds")
            : null;
        Long operatorId = request.get("operatorId") != null
            ? ((Number) request.get("operatorId")).longValue()
            : null;
        String operatorName = (String) request.get("operatorName");

        log.info("收费操作 | registerId={}, itemIds={}, operatorId={}", registerId, itemIds, operatorId);

        // v3.2 申请 write-token（与 payment-service.payItem 互斥）
        Map<String, Object> tokenBody = new HashMap<>();
        tokenBody.put("registerId", registerId);
        tokenBody.put("holder", "CHARGE_SERVICE");
        tokenBody.put("ttlSeconds", 30);
        Map<String, Object> tokenResp = paymentFeignClient.acquireWriteToken(tokenBody);
        Integer tokenCode = (Integer) tokenResp.get("code");
        if (tokenCode == null || tokenCode != 200) {
            String msg = String.valueOf(tokenResp.getOrDefault("message", "申请写令牌失败"));
            throw new BusinessException(409, msg);
        }
        log.info("charge 获取 write-token | registerId={}", registerId);

        try {
            List<ExpenseRecord> pendingItems;
            if (itemIds != null && !itemIds.isEmpty()) {
                pendingItems = expenseRecordMapper.selectPendingByRegisterAndIds(registerId, itemIds);
            } else {
                pendingItems = expenseRecordMapper.selectPendingByRegisterId(registerId);
            }

            if (pendingItems == null || pendingItems.isEmpty()) {
                throw new BusinessException(400, "没有待缴费项目");
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            int claimedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            // v3.2 防双写：updateIfPending 仅在 status=0 时生效。
            // 并发场景：payment-service.payItem 已 flip 某行为 status=1 → 该行 UPDATE 命中 0 行 → 跳过。
            for (ExpenseRecord item : pendingItems) {
                item.setStatus(1);
                item.setPayTime(now);
                item.setOperatorId(operatorId);
                item.setOperatorName(operatorName);
                int affected = expenseRecordMapper.updateIfPending(item);
                if (affected > 0) {
                    totalAmount = totalAmount.add(item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO);
                    claimedCount++;
                } else {
                    log.info("charge 跳过已被并发支付的项 | registerId={}, itemId={}", registerId, item.getId());
                }
            }

            if (claimedCount == 0) {
                throw new BusinessException(400, "待缴费项目已被支付，无需重复收费");
            }

            registrationMapper.updatePayStatus(registerId, 2);

            Register register = registrationMapper.selectById(registerId);
            if (register != null && register.getVisitState() == 1) {
                registrationMapper.updateStatus(registerId, 2);
            }

            // 收费员直接写了 expense_record，需要通知 payment-service 重新汇总（避免下次支付时状态不一致）
            // 通过 payment-service 的内部 API：本服务后续会通过 summary 端点拉取，所以这里不需要主动通知

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("itemCount", claimedCount);
            result.put("totalAmount", totalAmount);
            result.put("payTime", now);
            result.put("operatorName", operatorName);

            log.info("收费成功 | registerId={}, claimedCount={}, totalAmount={}", registerId, claimedCount, totalAmount);
            return result;
        } finally {
            // 释放 write-token
            try {
                Map<String, Object> relBody = new HashMap<>();
                relBody.put("registerId", registerId);
                relBody.put("holder", "CHARGE_SERVICE");
                paymentFeignClient.releaseWriteToken(relBody);
            } catch (Exception e) {
                log.warn("释放 write-token 失败 | registerId={}, err={}", registerId, e.getMessage());
                // 自然过期也 OK（30s TTL）
            }
        }
    }

    /**
     * 查询待缴费项目（v3.2：改 Feign 透传）
     */
    public List<Map<String, Object>> getPendingCharges(Long registerId) {
        Map<String, Object> resp = paymentFeignClient.records(null, registerId, 0, null, null);
        Object data = resp.get("data");
        return data instanceof List<?> list ? (List<Map<String, Object>>) (List) list : List.of();
    }

    /**
     * 查询患者的待缴费项目（v3.2：改 Feign 透传）
     */
    public List<Map<String, Object>> getPendingChargesByPatient(Long patientId) {
        Map<String, Object> resp = paymentFeignClient.records(patientId, null, 0, null, null);
        Object data = resp.get("data");
        return data instanceof List<?> list ? (List<Map<String, Object>>) (List) list : List.of();
    }

    private void fillRegistrationStatus(Map<String, Object> result, Long registerId) {
        try {
            Map<String, Object> resp = paymentFeignClient.summary(registerId);
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> m) {
                result.put("payStatus", m.get("payStatus"));
                result.put("payStatusName", m.get("payStatusName"));
            }
        } catch (Exception ignored) {}
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
