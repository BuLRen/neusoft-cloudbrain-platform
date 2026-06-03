package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.entity.Register;
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

        // 1. 查询待缴费项目
        List<ExpenseRecord> pendingItems;
        if (itemIds != null && !itemIds.isEmpty()) {
            pendingItems = expenseRecordMapper.selectPendingByRegisterAndIds(registerId, itemIds);
        } else {
            pendingItems = expenseRecordMapper.selectPendingByRegisterId(registerId);
        }

        if (pendingItems == null || pendingItems.isEmpty()) {
            throw new BusinessException(400, "没有待缴费项目");
        }

        // 2. 计算总金额
        BigDecimal totalAmount = pendingItems.stream()
            .map(ExpenseRecord::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 更新费用记录状态为已缴费
        LocalDateTime now = LocalDateTime.now();
        for (ExpenseRecord item : pendingItems) {
            item.setStatus(1); // 已缴费
            item.setPayTime(now);
            item.setOperatorId(operatorId);
            item.setOperatorName(operatorName);
            expenseRecordMapper.update(item);
        }

        // 4. 更新挂号记录的缴费状态（visit_state 改为 2 已缴费）
        registrationMapper.updatePayStatus(registerId, 2);

        // 5. 更新挂号状态（如果之前是已挂号状态(1)，改为已缴费(2)）
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
        // expense_record 表使用 card_number 作为患者标识，此方法暂不实现
        return List.of();
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
