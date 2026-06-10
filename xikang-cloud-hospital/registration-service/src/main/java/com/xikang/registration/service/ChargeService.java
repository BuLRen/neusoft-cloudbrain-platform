package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.entity.Register;
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
 * Charge Service - 收费核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final RegistrationMapper registrationMapper;
    private final AuthPatientFeignClient authPatientFeignClient;

    /**
     * 患者按挂号单自助缴费
     */
    @Transactional
    public Map<String, Object> payRegistration(Long registerId) {
        Register register = registrationMapper.selectById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (register.getVisitState() != null && register.getVisitState() == 4) {
            throw new BusinessException(400, "该挂号已退号，不能继续缴费");
        }
        if (register.getPatientId() == null) {
            throw new BusinessException(400, "该挂号缺少患者信息，无法自助缴费");
        }

        List<ExpenseRecord> pendingItems = filterPatientPayItems(expenseRecordMapper.selectPendingByRegisterId(registerId));
        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new BusinessException(400, "没有待缴费项目");
        }

        // 一个挂号单只会产生一条"待支付"的挂号费；如果历史脏数据产生多条，强制只取最早一条进行真实扣款，
        // 避免一次性扣 N 次造成余额异常。
        ExpenseRecord payItem = pendingItems.stream()
                .min((a, b) -> {
                    LocalDateTime ta = a.getCreateTime();
                    LocalDateTime tb = b.getCreateTime();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return ta.compareTo(tb);
                })
                .orElseThrow(() -> new BusinessException(400, "没有待缴费项目"));
        pendingItems = List.of(payItem);

        BigDecimal totalAmount = pendingItems.stream()
                .map(ExpenseRecord::getTotalAmount)
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "待缴费金额异常");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("amount", totalAmount);
        body.put("businessType", "REGISTRATION");
        body.put("businessId", registerId);
        body.put("operatorId", register.getPatientId());
        body.put("operatorName", "患者余额");
        body.put("remark", "患者自助余额支付挂号费");
        Map<String, Object> response = authPatientFeignClient.deductBalance(register.getPatientId().intValue(), body);
        Map<String, Object> data = unwrapMapData(response, "余额扣款失败");

        Map<String, Object> result = new HashMap<>();
        result.put("success", Boolean.TRUE.equals(data.get("success")));
        result.put("registerId", registerId);
        result.put("amount", totalAmount);
        result.put("accountBalance", data.get("accountBalance"));
        result.put("paymentMessage", String.valueOf(data.getOrDefault("message", "缴费失败")));

        if (!Boolean.TRUE.equals(data.get("success"))) {
            result.put("payStatus", 0);
            result.put("payStatusName", "待缴费");
            result.put("status", register.getVisitState());
            result.put("statusName", getStateName(register.getVisitState()));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        for (ExpenseRecord item : pendingItems) {
            item.setStatus(1);
            item.setPayTime(now);
            item.setOperatorId(register.getPatientId());
            item.setOperatorName("患者余额");
            item.setRemark("患者自助余额支付");
            expenseRecordMapper.update(item);
        }

        // 支付成功后，把同 registerId 下其余 status=0 的 REGISTRATION_FEE 作废，
        // 防止"取消挂号"时把多余的待缴费行也一起退掉。
        invalidateDuplicateRegistrationFees(registerId, payItem.getId());

        Integer status = register.getVisitState();

        result.put("payStatus", 1);
        result.put("payStatusName", "已缴费");
        result.put("payTime", now);
        result.put("status", status);
        result.put("statusName", getStateName(status));
        result.put("itemCount", pendingItems.size());
        result.put("operatorName", "患者余额");
        result.put("paymentMessage", "余额支付成功");

        log.info("患者自助缴费成功 | registerId={}, patientId={}, totalAmount={}", registerId, register.getPatientId(), totalAmount);
        return result;
    }

    /**
     * 收费核心流程：
     * 1. 根据 registerId 查询待缴费项目
     * 2. 计算总金额
     * 3. 创建 expense_record（状态设为已缴费）
     * 4. 更新 register 的 pay_status
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

        List<ExpenseRecord> pendingItems;
        if (itemIds != null && !itemIds.isEmpty()) {
            pendingItems = expenseRecordMapper.selectPendingByRegisterAndIds(registerId, itemIds);
        } else {
            pendingItems = expenseRecordMapper.selectPendingByRegisterId(registerId);
        }

        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new BusinessException(400, "没有待缴费项目");
        }

        BigDecimal totalAmount = pendingItems.stream()
            .map(ExpenseRecord::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime now = LocalDateTime.now();
        for (ExpenseRecord item : pendingItems) {
            item.setStatus(1);
            item.setPayTime(now);
            item.setOperatorId(operatorId);
            item.setOperatorName(operatorName);
            expenseRecordMapper.update(item);
        }

        registrationMapper.updatePayStatus(registerId, 2);

        Register register = registrationMapper.selectById(registerId);
        if (register != null && register.getVisitState() == 1) {
            registrationMapper.updateStatus(registerId, 2);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("itemCount", pendingItems.size());
        result.put("totalAmount", totalAmount);
        result.put("payTime", now);
        result.put("operatorName", operatorName);

        log.info("收费成功 | registerId={}, totalAmount={}", registerId, totalAmount);
        return result;
    }

    /**
     * 查询待缴费项目
     */
    public List<Map<String, Object>> getPendingCharges(Long registerId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectPendingByRegisterId(registerId);
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 查询患者的待缴费项目
     */
    public List<Map<String, Object>> getPendingChargesByPatient(Long patientId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectPendingByPatientId(patientId);
        return records.stream().map(this::toMap).toList();
    }

    private List<ExpenseRecord> filterPatientPayItems(List<ExpenseRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        boolean hasRegistrationFee = records.stream().anyMatch(record -> "REGISTRATION_FEE".equals(record.getItemCode()));
        if (!hasRegistrationFee) {
            return records;
        }
        return records.stream()
                .filter(record -> "REGISTRATION_FEE".equals(record.getItemCode()))
                .toList();
    }

    private void invalidateDuplicateRegistrationFees(Long registerId, Long keepId) {
        if (registerId == null) return;
        List<ExpenseRecord> pendingFees = expenseRecordMapper.selectByRegisterId(registerId).stream()
                .filter(record -> "REGISTRATION_FEE".equals(record.getItemCode()))
                .filter(record -> record.getStatus() != null && record.getStatus() == 0)
                .toList();
        for (ExpenseRecord record : pendingFees) {
            if (keepId != null && keepId.equals(record.getId())) {
                continue;
            }
            record.setStatus(3);
            record.setRemark("重复挂号费作废");
            expenseRecordMapper.update(record);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMapData(Map<String, Object> response, String errorMessage) {
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(500, errorMessage);
        }
        return (Map<String, Object>) dataMap;
    }

    private String getStateName(Integer state) {
        return switch (state) {
            case 1 -> "已挂号";
            case 2 -> "医生接诊";
            case 3 -> "看诊结束";
            case 4 -> "已退号";
            default -> "未知";
        };
    }

    private Map<String, Object> toMap(ExpenseRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("registerId", record.getRegisterId());
        map.put("patientId", record.getPatientId());
        map.put("patientName", record.getPatientName());
        map.put("categoryName", record.getCategoryName());
        map.put("itemId", record.getItemId());
        map.put("itemName", record.getItemName());
        map.put("quantity", record.getQuantity());
        map.put("unitPrice", record.getUnitPrice());
        map.put("totalAmount", record.getTotalAmount());
        map.put("createTime", record.getCreateTime());
        return map;
    }
}
