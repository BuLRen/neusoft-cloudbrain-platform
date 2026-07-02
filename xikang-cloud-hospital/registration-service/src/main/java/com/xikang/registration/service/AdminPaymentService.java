package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.Register;
import com.xikang.registration.feign.AuthPatientFeignClient;
import com.xikang.registration.feign.PaymentFeignClient;
import com.xikang.registration.mapper.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private static final String COUNTER_OPERATOR = "现场收费窗口";

    private final PaymentFeignClient paymentFeignClient;
    private final AuthPatientFeignClient authPatientFeignClient;
    private final ChargeService chargeService;
    private final RegistrationMapper registrationMapper;

    public Map<String, Object> listPaymentOrders(
        String keyword,
        Long patientId,
        Integer status,
        LocalDate startDate,
        LocalDate endDate,
        Integer page,
        Integer size
    ) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        return unwrapData(paymentFeignClient.listAdminOrders(
            keyword, patientId, status, startDate, endDate, currentPage, pageSize
        ));
    }

    public Map<String, Object> getPaymentOrderDetail(Long registerId) {
        if (registerId == null) {
            throw new BusinessException(400, "挂号号不能为空");
        }
        Map<String, Object> detail = unwrapData(paymentFeignClient.getAdminOrderDetail(registerId));
        applyRegisterPatientInfo(detail, registerId);
        enrichWithPatientBalance(detail);
        return detail;
    }

    /** 以挂号表 patient_id 为准，避免 payment 侧误用挂号号作为患者 ID。 */
    private void applyRegisterPatientInfo(Map<String, Object> detail, Long registerId) {
        Register register = registrationMapper.selectById(registerId);
        if (register == null) {
            return;
        }
        if (register.getPatientId() != null) {
            detail.put("patientId", register.getPatientId());
        }
        if (register.getRealName() != null && !register.getRealName().isBlank()) {
            detail.put("patientName", register.getRealName());
        }
    }

    private Integer requirePatientIdFromRegister(Long registerId) {
        Register register = registrationMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (register.getPatientId() == null) {
            throw new BusinessException(400, "挂号记录未关联患者档案，无法充值");
        }
        return register.getPatientId().intValue();
    }

    public Map<String, Object> getPatientBalance(Integer patientId) {
        if (patientId == null) {
            throw new BusinessException(400, "患者 ID 不能为空");
        }
        return fetchPatientBalanceOrZero(patientId);
    }

    private void enrichWithPatientBalance(Map<String, Object> detail) {
        Object pid = detail.get("patientId");
        if (pid == null) {
            detail.put("accountBalance", 0);
            return;
        }
        int patientId = pid instanceof Number n ? n.intValue() : Integer.parseInt(pid.toString());
        Map<String, Object> balance = fetchPatientBalanceOrZero(patientId);
        detail.put("accountBalance", balance.get("accountBalance"));
        if (Boolean.TRUE.equals(balance.get("balanceUnavailable"))) {
            detail.put("balanceUnavailable", true);
        }
    }

    private Map<String, Object> fetchPatientBalanceOrZero(Integer patientId) {
        try {
            return unwrapData(authPatientFeignClient.getBalance(patientId));
        } catch (BusinessException e) {
            if (e.getCode() == 404) {
                log.warn("患者余额查询失败（患者档案不存在）| patientId={}", patientId);
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("patientId", patientId);
                fallback.put("accountBalance", 0);
                fallback.put("balanceUnavailable", true);
                return fallback;
            }
            throw e;
        } catch (Exception e) {
            log.warn("患者余额查询失败 | patientId={}, err={}", patientId, e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("patientId", patientId);
            fallback.put("accountBalance", 0);
            fallback.put("balanceUnavailable", true);
            return fallback;
        }
    }

    public List<Map<String, Object>> searchPatients(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int size = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        Map<String, Object> resp = authPatientFeignClient.searchPatients(keyword.trim(), size);
        return unwrapList(resp);
    }

    public Map<String, Object> rechargeByRegister(
        Long registerId,
        BigDecimal amount,
        String remark,
        Long operatorId,
        String operatorName
    ) {
        Integer patientId = requirePatientIdFromRegister(registerId);
        return rechargePatient(patientId, amount, remark, operatorId, operatorName);
    }

    public Map<String, Object> rechargePatient(
        Integer patientId,
        BigDecimal amount,
        String remark,
        Long operatorId,
        String operatorName
    ) {
        if (patientId == null) {
            throw new BusinessException(400, "患者 ID 不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于 0");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("businessType", "COUNTER_RECHARGE");
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName != null && !operatorName.isBlank() ? operatorName : COUNTER_OPERATOR);
        body.put("remark", remark != null && !remark.isBlank() ? remark : "现场窗口充值");
        return unwrapData(authPatientFeignClient.rechargeBalance(patientId, body));
    }

    /**
     * 现场现金收费：直接将待缴费用标记为已支付（不扣余额）。
     */
    public Map<String, Object> markItemsPaid(
        Long registerId,
        List<?> itemIds,
        Long operatorId,
        String operatorName
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("registerId", registerId);
        List<Long> normalizedIds = normalizeItemIds(itemIds);
        if (!normalizedIds.isEmpty()) {
            request.put("itemIds", normalizedIds);
        }
        request.put("operatorId", operatorId);
        request.put("operatorName", operatorName != null && !operatorName.isBlank() ? operatorName : COUNTER_OPERATOR);
        return chargeService.charge(request);
    }

    /**
     * 余额扣款支付单条费用。
     */
    public Map<String, Object> payItemByBalance(
        Long itemId,
        Long operatorId,
        String operatorName
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName != null && !operatorName.isBlank() ? operatorName : COUNTER_OPERATOR);
        return unwrapData(paymentFeignClient.payItem(itemId, body));
    }

    /**
     * 余额扣款支付挂号下全部待缴费用。
     */
    public Map<String, Object> payAllByBalance(
        Long registerId,
        Long operatorId,
        String operatorName
    ) {
        Map<String, Object> detail = getPaymentOrderDetail(registerId);
        Object itemsObj = detail.get("items");
        if (!(itemsObj instanceof List<?> items)) {
            throw new BusinessException(400, "该账单没有可支付的费用项");
        }

        List<Long> pendingIds = new ArrayList<>();
        for (Object itemObj : items) {
            if (itemObj instanceof Map<?, ?> item) {
                Object status = item.get("status");
                Object id = item.get("id");
                if (id != null && (status == null || Integer.valueOf(0).equals(status))) {
                    pendingIds.add(Long.valueOf(id.toString()));
                }
            }
        }
        if (pendingIds.isEmpty()) {
            throw new BusinessException(400, "该账单没有待缴费用");
        }

        int success = 0;
        int failed = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Object lastBalance = null;
        List<Map<String, Object>> failedItems = new ArrayList<>();
        String opName = operatorName != null && !operatorName.isBlank() ? operatorName : COUNTER_OPERATOR;

        for (Long itemId : pendingIds) {
            try {
                Map<String, Object> result = payItemByBalance(itemId, operatorId, opName);
                success++;
                if (result.get("paidAmount") != null) {
                    totalPaid = totalPaid.add(new BigDecimal(result.get("paidAmount").toString()));
                }
                if (result.get("accountBalance") != null) {
                    lastBalance = result.get("accountBalance");
                }
            } catch (Exception e) {
                failed++;
                Map<String, Object> failedItem = new LinkedHashMap<>();
                failedItem.put("itemId", itemId);
                failedItem.put("reason", e.getMessage());
                failedItems.add(failedItem);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", failed == 0);
        summary.put("registerId", registerId);
        summary.put("paidCount", success);
        summary.put("failedCount", failed);
        summary.put("totalAmount", totalPaid);
        summary.put("accountBalance", lastBalance);
        summary.put("failedItems", failedItems);
        summary.put("paymentMessage", failed == 0
            ? "支付成功"
            : (success + " 项支付成功，" + failed + " 项失败"));
        return summary;
    }

    private List<Long> normalizeItemIds(List<?> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Object itemId : itemIds) {
            if (itemId instanceof Number number) {
                normalized.add(number.longValue());
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> unwrapList(Map<String, Object> resp) {
        if (resp == null) {
            throw new BusinessException(502, "患者服务无响应");
        }
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() != 200) {
            Object message = resp.get("message");
            throw new BusinessException(n.intValue(), message != null ? message.toString() : "患者服务调用失败");
        }
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(Map<String, Object> resp) {
        if (resp == null) {
            throw new BusinessException(502, "支付服务无响应");
        }
        Object code = resp.get("code");
        if (code instanceof Number n && n.intValue() != 200) {
            Object message = resp.get("message");
            throw new BusinessException(n.intValue(), message != null ? message.toString() : "支付服务调用失败");
        }
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new BusinessException(502, "支付服务返回格式异常");
    }
}
