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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Refund Service - 退费服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final RegistrationMapper registrationMapper;

    /**
     * 退费流程：
     * 1. 校验费用记录状态
     * 2. 更新状态为已退款
     * 3. 检查是否需要更新挂号缴费状态
     */
    @Transactional
    public void refund(Long expenseRecordId, Long operatorId, String operatorName, String reason) {
        log.info("退费操作 | expenseRecordId={}, operatorId={}, reason={}", expenseRecordId, operatorId, reason);

        // 1. 查询费用记录
        ExpenseRecord record = expenseRecordMapper.selectById(expenseRecordId);
        if (record == null) {
            throw new BusinessException(404, "费用记录不存在");
        }

        // 2. 校验状态
        if (record.getStatus() != 1) {
            throw new BusinessException(400, "该费用记录不可退费，当前状态：" + record.getStatus());
        }

        // 3. 更新状态为已退款
        record.setStatus(2); // 已退款
        record.setRefundTime(LocalDateTime.now());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setRemark(reason);
        expenseRecordMapper.update(record);

        // 4. 检查是否还有其他未退款的费用记录
        List<ExpenseRecord> remainingUnrefunded = expenseRecordMapper.selectUnrefundedByRegisterId(record.getRegisterId());
        if (remainingUnrefunded.isEmpty()) {
            // 所有费用都已退款，更新挂号缴费状态
            registrationMapper.updatePayStatus(record.getRegisterId(), 2);
        }

        log.info("退费成功 | expenseRecordId={}", expenseRecordId);
    }

    /**
     * 按挂号ID退费（全部退费）
     */
    @Transactional
    public void refundByRegisterId(Long registerId, Long operatorId, String operatorName, String reason) {
        log.info("按挂号ID退费 | registerId={}, operatorId={}", registerId, operatorId);

        List<ExpenseRecord> records = expenseRecordMapper.selectPendingByRegisterId(registerId);

        for (ExpenseRecord record : records) {
            if (record.getStatus() == 1) { // 只退已缴费的
                record.setStatus(2);
                record.setRefundTime(LocalDateTime.now());
                record.setOperatorId(operatorId);
                record.setOperatorName(operatorName);
                record.setRemark(reason);
                expenseRecordMapper.update(record);
            }
        }

        // 更新挂号状态
        registrationMapper.updatePayStatus(registerId, 2);

        log.info("按挂号ID退费完成 | registerId={}, count={}", registerId, records.size());
    }
}
