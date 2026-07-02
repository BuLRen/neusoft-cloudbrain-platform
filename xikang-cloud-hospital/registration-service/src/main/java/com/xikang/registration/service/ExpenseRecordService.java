package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.ExpenseRecord;
import com.xikang.registration.feign.PaymentFeignClient;
import com.xikang.registration.mapper.ExpenseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ExpenseRecord Service - 费用记录服务（v3.2：读改 Feign 透传 payment-service）
 *
 * 读：全部走 paymentFeignClient.records（payment-service 已聚合排序）。
 * 保留 expenseRecordMapper：onFeePaid 回调路径不需要，但 charge 双写期仍直接读本地，作为 fallback。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseRecordService {

    private final ExpenseRecordMapper expenseRecordMapper; // 双写期 fallback
    private final PaymentFeignClient paymentFeignClient;

    /**
     * 根据ID获取费用记录（v3.2：本地直读，避免为单条记录额外 Feign）
     */
    public ExpenseRecord getExpenseRecord(Long id) {
        return expenseRecordMapper.selectById(id);
    }

    /**
     * 根据挂号ID获取费用记录（v3.2：Feign 透传）
     */
    public List<Map<String, Object>> getExpenseRecordsByRegisterId(Long registerId) {
        return queryViaFeign(null, registerId, null, null, null);
    }

    /**
     * 根据患者ID获取费用记录（v3.2：Feign 透传）
     */
    public List<Map<String, Object>> getExpenseRecordsByPatientId(Long patientId) {
        return queryViaFeign(patientId, null, null, null, null);
    }

    /**
     * 根据状态获取费用记录（v3.2：Feign 透传）
     */
    public List<Map<String, Object>> getExpenseRecordsByStatus(Integer status) {
        return queryViaFeign(null, null, status, null, null);
    }

    /**
     * 获取待缴费项目（v3.2：Feign 透传 status=0）
     */
    public List<Map<String, Object>> getPendingExpenses(Long registerId) {
        return queryViaFeign(null, registerId, 0, null, null);
    }

    /**
     * 查询费用记录（支持多条件 + 时间区间）
     * v3.2：Feign 透传 payment-service.records，由 payment-service 完成排序/过滤。
     */
    public List<Map<String, Object>> queryExpenseRecords(
            Long patientId, Long registerId, Integer status,
            LocalDate startDate, LocalDate endDate) {
        return queryViaFeign(patientId, registerId, status, startDate, endDate);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryViaFeign(
            Long patientId, Long registerId, Integer status,
            LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, Object> resp = paymentFeignClient.records(patientId, registerId, status, startDate, endDate);
            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                return (List<Map<String, Object>>) (List) list;
            }
            return List.of();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Feign 调 payment.records 失败，回退本地读 | err={}", e.getMessage());
            return fallbackQuery(patientId, registerId, status);
        }
    }

    /**
     * 双写期降级：Feign 失败时本地直读（不实现时间区间过滤，仅作 fallback）。
     */
    private List<Map<String, Object>> fallbackQuery(Long patientId, Long registerId, Integer status) {
        List<ExpenseRecord> records;
        if (registerId != null) {
            records = expenseRecordMapper.selectByRegisterId(registerId);
        } else if (patientId != null) {
            records = expenseRecordMapper.selectByPatientId(patientId);
        } else if (status != null) {
            records = expenseRecordMapper.selectByStatus(status);
        } else {
            return List.of();
        }
        if (status != null) {
            records = records.stream().filter(r -> status.equals(r.getStatus())).toList();
        }
        return records.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(ExpenseRecord record) {
        Map<String, Object> map = new java.util.HashMap<>();
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
