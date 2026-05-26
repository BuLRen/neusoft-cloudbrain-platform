package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.mapper.ExpenseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ExpenseRecord Service - 费用记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseRecordService {

    private final ExpenseRecordMapper expenseRecordMapper;

    /**
     * 根据ID获取费用记录
     */
    public ExpenseRecord getExpenseRecord(Long id) {
        return expenseRecordMapper.selectById(id);
    }

    /**
     * 根据挂号ID获取费用记录
     */
    public List<Map<String, Object>> getExpenseRecordsByRegisterId(Long registerId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 根据患者ID获取费用记录
     */
    public List<Map<String, Object>> getExpenseRecordsByPatientId(Long patientId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByPatientId(patientId);
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 根据状态获取费用记录
     */
    public List<Map<String, Object>> getExpenseRecordsByStatus(Integer status) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByStatus(status);
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 获取待缴费项目
     */
    public List<Map<String, Object>> getPendingExpenses(Long registerId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectPendingByRegisterId(registerId);
        return records.stream().map(this::toMap).toList();
    }

    /**
     * 查询费用记录（支持多条件）
     */
    public List<Map<String, Object>> queryExpenseRecords(
            Long patientId, Long registerId, Integer status) {

        List<ExpenseRecord> records;

        if (registerId != null) {
            records = expenseRecordMapper.selectByRegisterId(registerId);
            if (status != null) {
                records = records.stream()
                    .filter(r -> r.getStatus().equals(status))
                    .toList();
            }
        } else if (patientId != null) {
            records = expenseRecordMapper.selectByPatientId(patientId);
            if (status != null) {
                records = records.stream()
                    .filter(r -> r.getStatus().equals(status))
                    .toList();
            }
        } else if (status != null) {
            records = expenseRecordMapper.selectByStatus(status);
        } else {
            records = List.of();
        }

        return records.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(ExpenseRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("registerId", record.getRegisterId());
        map.put("patientId", record.getPatientId());
        map.put("patientName", record.getPatientName());
        map.put("categoryId", record.getCategoryId());
        map.put("categoryName", record.getCategoryName());
        map.put("itemId", record.getItemId());
        map.put("itemName", record.getItemName());
        map.put("itemCode", record.getItemCode());
        map.put("quantity", record.getQuantity());
        map.put("unitPrice", record.getUnitPrice());
        map.put("totalAmount", record.getTotalAmount());
        map.put("status", record.getStatus());
        map.put("statusName", getStatusName(record.getStatus()));
        map.put("payTime", record.getPayTime());
        map.put("refundTime", record.getRefundTime());
        map.put("operatorName", record.getOperatorName());
        map.put("remark", record.getRemark());
        map.put("createTime", record.getCreateTime());
        return map;
    }

    private String getStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待缴费";
            case 1 -> "已缴费";
            case 2 -> "已退款";
            case 3 -> "已作废";
            default -> "未知";
        };
    }
}
