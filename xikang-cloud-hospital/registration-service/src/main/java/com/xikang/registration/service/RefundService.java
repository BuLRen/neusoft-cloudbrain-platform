package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.feign.AuthPatientFeignClient;
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
 * Refund Service - 退费服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final RegistrationMapper registrationMapper;
    private final AuthPatientFeignClient authPatientFeignClient;

    /**
     * 单条费用退费
     */
    @Transactional
    public Map<String, Object> refund(Long expenseRecordId, Long operatorId, String operatorName, String reason) {
        log.info("退费操作 | expenseRecordId={}, operatorId={}, reason={}", expenseRecordId, operatorId, reason);

        ExpenseRecord record = expenseRecordMapper.selectById(expenseRecordId);
        if (record == null) {
            throw new BusinessException(404, "费用记录不存在");
        }
        if (record.getStatus() == null || record.getStatus() != 1) {
            throw new BusinessException(400, "该费用记录不可退费，当前状态：" + record.getStatus());
        }

        Map<String, Object> result = refundPaidRecords(List.of(record), operatorId, operatorName, reason);
        log.info("退费成功 | expenseRecordId={}", expenseRecordId);
        return result;
    }

    /**
     * 按挂号ID退费（全部已缴费项目）
     */
    @Transactional
    public Map<String, Object> refundByRegisterId(Long registerId, Long operatorId, String operatorName, String reason) {
        log.info("按挂号ID退费 | registerId={}, operatorId={}", registerId, operatorId);

        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        List<ExpenseRecord> paidRecords = records.stream()
                .filter(record -> record.getStatus() != null && record.getStatus() == 1)
                .toList();

        if (paidRecords.isEmpty()) {
            throw new BusinessException(400, "该挂号没有可退费的已缴费记录");
        }

        Map<String, Object> result = refundPaidRecords(paidRecords, operatorId, operatorName, reason);
        log.info("按挂号ID退费完成 | registerId={}, count={}", registerId, paidRecords.size());
        return result;
    }

    /**
     * 按挂号ID仅退挂号费（用于患者取消挂号）
     * 一次只退一条 REGISTRATION_FEE，避免历史脏数据导致重复退 N 次。
     */
    @Transactional
    public Map<String, Object> refundRegistrationFee(Long registerId, Long operatorId, String operatorName, String reason) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        List<ExpenseRecord> paidRegistrationFees = records.stream()
                .filter(record -> "REGISTRATION_FEE".equals(record.getItemCode()))
                .filter(record -> record.getStatus() != null && record.getStatus() == 1)
                .toList();

        if (paidRegistrationFees.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("registerId", registerId);
            result.put("refundAmount", BigDecimal.ZERO);
            result.put("refundedCount", 0);
            result.put("message", "没有可退款的已缴挂号费");
            return result;
        }

        ExpenseRecord paidFee = paidRegistrationFees.stream()
                .min((a, b) -> {
                    LocalDateTime ta = a.getPayTime();
                    LocalDateTime tb = b.getPayTime();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return ta.compareTo(tb);
                })
                .orElse(paidRegistrationFees.get(0));

        return refundPaidRecords(List.of(paidFee), operatorId, operatorName, reason);
    }

    private Map<String, Object> refundPaidRecords(List<ExpenseRecord> paidRecords, Long operatorId, String operatorName, String reason) {
        BigDecimal totalAmount = paidRecords.stream()
                .map(ExpenseRecord::getTotalAmount)
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "退款金额异常");
        }

        ExpenseRecord firstRecord = paidRecords.get(0);
        if (firstRecord.getPatientId() == null) {
            throw new BusinessException(400, "该费用记录缺少患者信息，无法退款到余额");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("amount", totalAmount);
        body.put("businessType", "REGISTRATION");
        body.put("businessId", firstRecord.getRegisterId());
        if (operatorId != null) {
            body.put("operatorId", operatorId);
        }
        body.put("operatorName", operatorName != null ? operatorName : "系统");
        body.put("remark", reason != null ? reason : "挂号退费");
        Map<String, Object> response = authPatientFeignClient.refundBalance(firstRecord.getPatientId().intValue(), body);
        Map<String, Object> data = unwrapMapData(response, "余额退款失败");
        if (!Boolean.TRUE.equals(data.get("success"))) {
            throw new BusinessException(400, String.valueOf(data.getOrDefault("message", "余额退款失败")));
        }

        LocalDateTime now = LocalDateTime.now();
        for (ExpenseRecord record : paidRecords) {
            record.setStatus(2);
            record.setRefundTime(now);
            record.setOperatorId(operatorId);
            record.setOperatorName(operatorName);
            record.setRemark(reason);
            expenseRecordMapper.update(record);
        }


        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("registerId", firstRecord.getRegisterId());
        result.put("patientId", firstRecord.getPatientId());
        result.put("refundAmount", totalAmount);
        result.put("refundedCount", paidRecords.size());
        result.put("refundTime", now);
        result.put("accountBalance", data.get("accountBalance"));
        result.put("message", data.getOrDefault("message", "退款成功"));
        return result;
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
