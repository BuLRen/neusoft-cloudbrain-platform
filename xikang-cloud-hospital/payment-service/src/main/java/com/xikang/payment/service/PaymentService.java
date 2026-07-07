package com.xikang.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.payment.entity.ExpenseRecord;
import com.xikang.payment.feign.AuthPatientFeignClient;
import com.xikang.payment.feign.RegistrationFeignClient;
import com.xikang.payment.mapper.ExpenseRecordMapper;
import com.xikang.payment.mapper.RegisterInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Payment Service（v3.2 §4.1 + §4.2）
 *
 * 接管 expense_record 表的读写主职责。
 *
 * 关键不变量：
 *  - MEDICATION_FEE 唯一：依赖既有 partial unique index uq_expense_record_medication_fee
 *  - payItem 防双扣：SELECT ... FOR UPDATE + 二次校验 status==0
 *  - 回调 on-fee-paid 幂等：registration 端重算 summary
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final RegisterInfoMapper registerInfoMapper;
    private final AuthPatientFeignClient authPatientFeignClient;
    private final RegistrationFeignClient registrationFeignClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ============================================================
    // 内部 API：createItem（v3.2 §4.2）
    // ============================================================

    /**
     * 推送费用项（幂等）。
     * MEDICATION_FEE：ON CONFLICT DO NOTHING，若冲突则返回既有 id。
     * REGISTRATION_FEE：先 SELECT (status IN 0,1)，命中返回既有；否则 INSERT。
     * 其他 item_code：直接 INSERT。
     */
    @Transactional
    public Map<String, Object> createItem(Map<String, Object> body) {
        String itemCode = str(body, "itemCode");
        Long registerId = lng(body, "registerId");
        if (itemCode == null || registerId == null) {
            throw new BusinessException(400, "itemCode 和 registerId 必填");
        }

        Long existingId = null;

        if ("MEDICATION_FEE".equals(itemCode)) {
            // 走 ON CONFLICT DO NOTHING
            ExpenseRecord inserted = buildFromRequest(body, "MEDICATION_FEE", 0);
            expenseRecordMapper.insertMedicationFeeIfAbsent(inserted);
            if (inserted.getId() != null) {
                log.info("createItem 新建 MEDICATION_FEE | registerId={}, id={}", registerId, inserted.getId());
                return createdResult(inserted.getId(), true);
            }
            // ON CONFLICT 命中既有行
            ExpenseRecord existing = expenseRecordMapper.selectMedicationFeeByRegisterId(registerId);
            if (existing != null) {
                log.info("createItem 命中既有 MEDICATION_FEE | registerId={}, id={}", registerId, existing.getId());
                return createdResult(existing.getId(), false);
            }
            // 极少见：ON CONFLICT 没插入但也没查到（事务隔离/并发删除）— 兜底重新 INSERT
            expenseRecordMapper.insert(inserted);
            return createdResult(inserted.getId(), true);
        }

        if ("REGISTRATION_FEE".equals(itemCode)) {
            ExpenseRecord existing = expenseRecordMapper.selectRegistrationFeeByRegisterId(registerId);
            if (existing != null) {
                log.info("createItem 命中既有 REGISTRATION_FEE | registerId={}, id={}", registerId, existing.getId());
                return createdResult(existing.getId(), false);
            }
            ExpenseRecord inserted = buildFromRequest(body, "REGISTRATION_FEE", 0);
            expenseRecordMapper.insert(inserted);
            log.info("createItem 新建 REGISTRATION_FEE | registerId={}, id={}", registerId, inserted.getId());
            return createdResult(inserted.getId(), true);
        }

        if (isTechFeeItemCode(itemCode)) {
            Long sourceId = lng(body, "sourceId");
            if (sourceId == null) {
                throw new BusinessException(400, itemCode + " 出账必须提供 sourceId（医技申请单 ID）");
            }
            ExpenseRecord existing = expenseRecordMapper.selectByRegisterSourceAndItemCode(registerId, sourceId, itemCode);
            if (existing != null) {
                log.info("createItem 命中既有 {} | registerId={}, sourceId={}, id={}",
                        itemCode, registerId, sourceId, existing.getId());
                return createdResult(existing.getId(), false);
            }
            ExpenseRecord inserted = buildFromRequest(body, itemCode, 0);
            expenseRecordMapper.insert(inserted);
            log.info("createItem 新建 {} | registerId={}, sourceId={}, id={}",
                    itemCode, registerId, sourceId, inserted.getId());
            return createdResult(inserted.getId(), true);
        }

        // 其他 item_code — 直接 INSERT
        ExpenseRecord inserted = buildFromRequest(body, itemCode, 0);
        expenseRecordMapper.insert(inserted);
        log.info("createItem 新建 {} | registerId={}, id={}", itemCode, registerId, inserted.getId());
        return createdResult(inserted.getId(), true);
    }

    // ============================================================
    // 内部 API：check-paid（医技/确诊卡点）
    // ============================================================

    /**
     * 校验挂号下是否全部费用已付清（扫描所有 status=0 行）。
     */
    public Map<String, Object> checkPaidByRegister(Long registerId) {
        List<ExpenseRecord> pending = expenseRecordMapper.selectPendingByRegisterIdAll(registerId);
        BigDecimal pendingAmount = pending.stream()
                .map(ExpenseRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> pendingItemCodes = pending.stream()
                .map(ExpenseRecord::getItemCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("allPaid", pending.isEmpty());
        result.put("pendingAmount", pendingAmount);
        result.put("pendingItems", pending.size());
        result.put("pendingItemCodes", pendingItemCodes);
        return result;
    }

    /**
     * 校验单条费用是否已缴（医技执行前按 sourceId 粒度校验）。
     */
    public Map<String, Object> checkPaidByItem(Long registerId, String itemCode, Long sourceId) {
        if (registerId == null || itemCode == null || sourceId == null) {
            throw new BusinessException(400, "registerId、itemCode、sourceId 必填");
        }
        ExpenseRecord record = expenseRecordMapper.selectByRegisterSourceAndItemCode(registerId, sourceId, itemCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("itemCode", itemCode);
        result.put("sourceId", sourceId);
        if (record == null) {
            result.put("exists", false);
            result.put("paid", false);
            result.put("status", null);
            return result;
        }
        result.put("exists", true);
        result.put("paid", record.getStatus() != null && record.getStatus() == 1);
        result.put("status", record.getStatus());
        result.put("expenseId", record.getId());
        result.put("totalAmount", record.getTotalAmount());
        return result;
    }

    /**
     * 按挂号 + itemCode + sourceId 查单条费用（供 medtech 列表 join）。
     */
    public Map<String, Object> getItemByRegisterAndSource(Long registerId, String itemCode, Long sourceId) {
        if (sourceId != null) {
            ExpenseRecord record = expenseRecordMapper.selectByRegisterSourceAndItemCode(registerId, sourceId, itemCode);
            return record != null ? toMap(record) : null;
        }
        List<Map<String, Object>> all = queryRecords(null, registerId, null, null, null);
        return all.stream()
                .filter(m -> itemCode.equals(m.get("itemCode")))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    // ============================================================
    // 内部 API：payItem（v3.2 §4.2 防双扣核心）
    // ============================================================

    /**
     * 内部触发支付（registration.tryBalancePayment 用）或患者自助支付用同一方法。
     * 步骤：
     *   1. SELECT ... FOR UPDATE
     *   2. 二次校验 status==0
     *   3. Feign auth.deductBalance
     *   4. UPDATE expense_record status=1
     *   5. afterCommit 回调 registration.on-fee-paid
     */
    @Transactional
    public Map<String, Object> payItem(Long itemId, Long operatorId, String operatorName) {
        ExpenseRecord item = expenseRecordMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new BusinessException(404, "费用项不存在: " + itemId);
        }
        if (item.getStatus() == null || item.getStatus() != 0) {
            throw new BusinessException(400, "该费用项当前状态不允许支付: status=" + item.getStatus());
        }
        if (item.getPatientId() == null) {
            throw new BusinessException(400, "费用项缺少 patientId，无法扣款");
        }
        BigDecimal amount = item.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "费用金额异常");
        }

        String businessType = businessTypeFor(item.getItemCode());

        Map<String, Object> deductBody = new HashMap<>();
        deductBody.put("amount", amount);
        deductBody.put("businessType", businessType);
        deductBody.put("businessId", item.getRegisterId());
        deductBody.put("operatorId", operatorId);
        deductBody.put("operatorName", operatorName != null ? operatorName : "系统");
        deductBody.put("remark", "支付 " + item.getItemName());

        Map<String, Object> response = authPatientFeignClient.deductBalance(item.getPatientId().intValue(), deductBody);
        Map<String, Object> data = unwrapMapData(response, "余额扣款失败");

        boolean success = Boolean.TRUE.equals(data.get("success"));
        if (!success) {
            String msg = String.valueOf(data.getOrDefault("message", "余额扣款失败"));
            throw new BusinessException(400, msg);
        }

        // 扣款成功，更新 expense_record
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(1);
        item.setPayTime(now);
        if (operatorId != null) item.setOperatorId(operatorId);
        item.setOperatorName(operatorName != null ? operatorName : "系统");
        item.setRemark((item.getRemark() == null ? "" : item.getRemark() + " | ") + "已支付");
        expenseRecordMapper.update(item);

        // afterCommit 回调 registration（v3.2 §5.3：payment-service 在自己事务 afterCommit 触发反向回调）
        // 这里用 TransactionSynchronization 在 commit 之后才调，避免事务回滚后 registration 状态错误。
        final Long registerId = item.getRegisterId();
        final String itemCode = item.getItemCode();
        final BigDecimal paidAmount = amount;
        final Long opId = operatorId;
        try {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            fireOnFeePaidCallback(registerId, itemCode, itemId, paidAmount, opId);
                        }
                    });
            } else {
                fireOnFeePaidCallback(registerId, itemCode, itemId, paidAmount, opId);
            }
        } catch (Exception e) {
            log.error("注册 afterCommit 回调失败，将依赖定时补偿 | itemId={}", itemId, e);
        }

        log.info("payItem 成功 | itemId={}, registerId={}, itemCode={}, amount={}",
                itemId, registerId, itemCode, amount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("registerId", registerId);
        result.put("itemId", itemId);
        result.put("itemName", item.getItemName());
        result.put("amount", amount);
        result.put("paidAmount", amount);
        result.put("accountBalance", data.get("accountBalance"));
        result.put("payTime", now);
        result.put("paymentMessage", "支付成功");
        return result;
    }

    /**
     * 全部支付：串行调 payItem，部分失败不中断。
     */
    public Map<String, Object> payAll(Long registerId, Long operatorId, String operatorName) {
        List<ExpenseRecord> pendings = expenseRecordMapper.selectPendingByRegisterId(registerId);
        if (pendings == null || pendings.isEmpty()) {
            throw new BusinessException(400, "该挂号没有待支付项目");
        }

        int success = 0, failed = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Object lastBalance = null;
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (ExpenseRecord item : pendings) {
            try {
                Map<String, Object> r = payItem(item.getId(), operatorId, operatorName);
                success++;
                if (r.get("paidAmount") != null) {
                    totalPaid = totalPaid.add(new BigDecimal(r.get("paidAmount").toString()));
                }
                if (r.get("accountBalance") != null) {
                    lastBalance = r.get("accountBalance");
                }
            } catch (Exception e) {
                failed++;
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("itemId", item.getId());
                f.put("itemName", item.getItemName());
                f.put("itemCode", item.getItemCode());
                f.put("amount", item.getTotalAmount());
                f.put("reason", e.getMessage());
                failedItems.add(f);
                log.warn("payAll 子项失败 | registerId={}, itemId={}, err={}", registerId, item.getId(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", failed == 0);
        result.put("registerId", registerId);
        result.put("paidCount", success);
        result.put("totalAmount", totalPaid);
        result.put("accountBalance", lastBalance);
        result.put("failedCount", failed);
        result.put("failedItems", failedItems);
        result.put("paymentMessage", failed == 0
                ? "支付成功"
                : (success + " 项支付成功，" + failed + " 项失败"));
        return result;
    }

    /**
     * 退款（v3.2 §4.2）。
     * registration.cancelRegistration / RefundService 调用。
     */
    @Transactional
    public Map<String, Object> refund(Long itemId, Long operatorId, String operatorName, String reason) {
        ExpenseRecord item = expenseRecordMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new BusinessException(404, "费用项不存在: " + itemId);
        }
        if (item.getStatus() == null || item.getStatus() != 1) {
            throw new BusinessException(400, "该费用项不可退款，当前状态: " + item.getStatus());
        }
        if (item.getPatientId() == null) {
            throw new BusinessException(400, "费用项缺少 patientId，无法退款");
        }
        BigDecimal amount = item.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "退款金额异常");
        }

        String businessType = businessTypeFor(item.getItemCode());
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("businessType", businessType);
        body.put("businessId", item.getRegisterId());
        body.put("operatorId", operatorId);
        body.put("operatorName", operatorName != null ? operatorName : "系统");
        body.put("remark", reason != null ? reason : "退款");

        Map<String, Object> response = authPatientFeignClient.refundBalance(item.getPatientId().intValue(), body);
        Map<String, Object> data = unwrapMapData(response, "余额退款失败");
        if (!Boolean.TRUE.equals(data.get("success"))) {
            throw new BusinessException(400, String.valueOf(data.getOrDefault("message", "余额退款失败")));
        }

        LocalDateTime now = LocalDateTime.now();
        item.setStatus(2);
        item.setRefundTime(now);
        if (operatorId != null) item.setOperatorId(operatorId);
        item.setOperatorName(operatorName != null ? operatorName : "系统");
        item.setRemark((item.getRemark() == null ? "" : item.getRemark() + " | ") + "已退款：" + (reason != null ? reason : ""));
        expenseRecordMapper.update(item);

        log.info("refund 成功 | itemId={}, registerId={}, amount={}", itemId, item.getRegisterId(), amount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("itemId", itemId);
        result.put("refundAmount", amount);
        result.put("accountBalance", data.get("accountBalance"));
        result.put("refundTime", now);
        return result;
    }

    // ============================================================
    // 内部 API：summary / records / dailyCharges
    // ============================================================

    /**
     * 汇总某挂号所有 expense 行的支付状态（v3.2 §4.2 替代 RegistrationService.fillPaymentStatus）。
     * 由 registration.on-fee-paid 回调端点调用。
     *
     * 规则（与 RegistrationService.fillPaymentStatus 一致）：
     *   - 有 REGISTRATION_FEE 行时，只看 REGISTRATION_FEE
     *   - payStatus: 0 待缴费 / 1 已缴费 / 2 已退费
     */
    public Map<String, Object> summary(Long registerId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        if (records == null) records = List.of();

        boolean hasRegistrationFee = records.stream()
                .anyMatch(r -> "REGISTRATION_FEE".equals(r.getItemCode()));

        boolean hasPending = false;
        boolean hasPaid = false;
        boolean allRefunded = true;
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime latestPayTime = null;
        LocalDateTime latestRefundTime = null;

        for (ExpenseRecord r : records) {
            if (hasRegistrationFee && !"REGISTRATION_FEE".equals(r.getItemCode())) {
                continue;
            }
            if (r.getStatus() == null || r.getStatus() == 0) hasPending = true;
            if (r.getStatus() != null && r.getStatus() == 1) hasPaid = true;
            if (r.getStatus() == null || r.getStatus() != 2) allRefunded = false;
            if (r.getTotalAmount() != null) totalAmount = totalAmount.add(r.getTotalAmount());
            if (r.getPayTime() != null && (latestPayTime == null || r.getPayTime().isAfter(latestPayTime))) {
                latestPayTime = r.getPayTime();
            }
            if (r.getRefundTime() != null && (latestRefundTime == null || r.getRefundTime().isAfter(latestRefundTime))) {
                latestRefundTime = r.getRefundTime();
            }
        }

        int payStatus;
        String payStatusName;
        if (hasPending) {
            payStatus = 0; payStatusName = "待缴费";
        } else if (hasPaid) {
            payStatus = 1; payStatusName = "已缴费";
        } else if (allRefunded) {
            payStatus = 2; payStatusName = "已退费";
        } else {
            payStatus = 0; payStatusName = "待缴费";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("amount", totalAmount);
        result.put("payTime", latestPayTime);
        result.put("refundTime", latestRefundTime);
        result.put("payStatus", payStatus);
        result.put("payStatusName", payStatusName);
        result.put("itemCount", records.size());
        return result;
    }

    public List<Map<String, Object>> queryRecords(Long patientId, Long registerId, Integer status,
                                                  LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endTime = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        List<ExpenseRecord> records = expenseRecordMapper.queryRecords(patientId, registerId, status, startTime, endTime);
        return records.stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> dailyCharges(LocalDate startDate, LocalDate endDate) {
        return expenseRecordMapper.dailyCharges(startDate, endDate);
    }

    // ============================================================
    // 患者端 API：orders（v3.2 §4.1）
    // ============================================================

    /**
     * 我的账单列表（按挂号号聚合）。
     */
    public Map<String, Object> listOrders(Long patientId, Integer statusFilter, int page, int size) {
        List<ExpenseRecord> all = expenseRecordMapper.selectByPatientId(patientId);
        if (all == null) all = List.of();
        return paginateOrders(groupOrders(all, statusFilter), page, size);
    }

    /**
     * 管理员账单列表（全平台，按挂号号聚合）。
     */
    public Map<String, Object> listAdminOrders(String keyword, Long patientId, Integer statusFilter,
                                               LocalDate startDate, LocalDate endDate,
                                               int page, int size) {
        LocalDateTime startTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endTime = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        String trimmedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        List<ExpenseRecord> all = expenseRecordMapper.selectForAdminOrderList(
            trimmedKeyword, patientId, startTime, endTime);
        if (all == null) all = List.of();
        return paginateOrders(groupOrders(all, statusFilter), page, size);
    }

    private List<Map<String, Object>> groupOrders(List<ExpenseRecord> all, Integer statusFilter) {
        Map<Long, List<ExpenseRecord>> grouped = all.stream()
                .filter(r -> r.getRegisterId() != null)
                .collect(Collectors.groupingBy(ExpenseRecord::getRegisterId));

        Map<Long, Map<String, Object>> registerInfoMap = loadRegisterInfo(new ArrayList<>(grouped.keySet()));

        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map.Entry<Long, List<ExpenseRecord>> entry : grouped.entrySet()) {
            Map<String, Object> order = buildOrder(entry.getKey(), entry.getValue(), registerInfoMap.get(entry.getKey()));
            if (statusFilter != null) {
                Integer s = (Integer) order.get("status");
                if (s == null || !s.equals(statusFilter)) continue;
            }
            orders.add(order);
        }

        orders.sort((a, b) -> {
            Comparable ta = (Comparable) a.get("createTime");
            Comparable tb = (Comparable) b.get("createTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return orders;
    }

    private Map<String, Object> paginateOrders(List<Map<String, Object>> orders, int page, int size) {
        int total = orders.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageList = orders.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", pageList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getOrderDetail(Long registerId) {
        List<ExpenseRecord> records = expenseRecordMapper.selectByRegisterId(registerId);
        if (records == null) records = List.of();
        Map<Long, Map<String, Object>> infoMap = loadRegisterInfo(List.of(registerId));
        return buildOrder(registerId, records, infoMap.get(registerId));
    }

    /**
     * 批量加载挂号基础信息（科室 / 医生 / 就诊日期）。失败返回空 Map，buildOrder 会优雅降级。
     */
    private Map<Long, Map<String, Object>> loadRegisterInfo(List<Long> registerIds) {
        if (registerIds == null || registerIds.isEmpty()) return Map.of();
        try {
            List<Map<String, Object>> rows = registerInfoMapper.selectBasicByIds(registerIds);
            Map<Long, Map<String, Object>> result = new HashMap<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object rid = row.get("registerId");
                    if (rid instanceof Number n) {
                        result.put(n.longValue(), row);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("loadRegisterInfo 失败，订单将不显示科室/医生 | ids={}, err={}", registerIds, e.getMessage());
            return Map.of();
        }
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private Map<String, Object> buildOrder(Long registerId, List<ExpenseRecord> records, Map<String, Object> registerInfo) {
        int itemCount = records.size();
        int paidCount = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 1).count();
        int pendingCount = (int) records.stream().filter(r -> r.getStatus() == null || r.getStatus() == 0).count();

        BigDecimal totalAmount = records.stream()
                .map(ExpenseRecord::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = records.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(ExpenseRecord::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingAmount = totalAmount.subtract(paidAmount);

        // v3.2：状态语义与前端 PaymentOrder 对齐
        //   0 待缴（含部分已缴但仍有未缴）/ 1 已付清（无待缴且至少一条已缴）/ 2 含已退
        int refundedCount = (int) records.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 2)
                .count();
        int status;
        String statusName;
        if (refundedCount > 0) {
            status = 2; statusName = "含已退";
        } else if (pendingCount > 0) {
            status = 0; statusName = "待缴费";
        } else if (paidCount > 0) {
            status = 1; statusName = "已付清";
        } else {
            // 既无待缴也无已缴也无已退（极端：全是 status=3 已作废）
            status = 2; statusName = "已作废";
        }

        LocalDateTime latestCreate = records.stream()
                .map(ExpenseRecord::getCreateTime)
                .filter(c -> c != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        // 订单级 payTime：取最晚一条已缴 payTime（前端"支付时间"用）
        LocalDateTime latestPayTime = records.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(ExpenseRecord::getPayTime)
                .filter(c -> c != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("registerId", registerId);
        // 患者信息：优先 register 表（权威），费用行仅作兜底
        Long patientId = null;
        String patientName = null;
        if (registerInfo != null) {
            patientId = lng(registerInfo, "patientId");
            patientName = str(registerInfo, "patientName");
        }
        if (patientId == null && !records.isEmpty()) {
            patientId = records.get(0).getPatientId();
        }
        if (patientName == null && !records.isEmpty()) {
            patientName = records.get(0).getPatientName();
        }
        order.put("patientId", patientId);
        order.put("patientName", patientName);
        // 挂号基础信息（科室 / 医生 / 就诊日期）
        if (registerInfo != null) {
            order.put("departmentName", registerInfo.get("departmentName"));
            order.put("doctorName", registerInfo.get("doctorName"));
            order.put("visitDate", registerInfo.get("visitDate"));
        } else {
            order.put("departmentName", null);
            order.put("doctorName", null);
            order.put("visitDate", null);
        }
        order.put("itemCount", itemCount);
        order.put("paidItemCount", paidCount);
        order.put("pendingItemCount", pendingCount);
        order.put("totalAmount", totalAmount);
        order.put("paidAmount", paidAmount);
        order.put("pendingAmount", pendingAmount);
        order.put("status", status);
        order.put("statusName", statusName);
        order.put("createTime", latestCreate);
        order.put("payTime", latestPayTime);
        order.put("items", records.stream().map(this::toOrderItemView).collect(Collectors.toList()));
        return order;
    }

    private Map<String, Object> toOrderItemView(ExpenseRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("registerId", r.getRegisterId());
        m.put("patientId", r.getPatientId());
        m.put("patientName", r.getPatientName());
        m.put("categoryId", r.getCategoryId());
        m.put("categoryName", r.getCategoryName());
        m.put("itemId", r.getItemId());
        m.put("itemName", r.getItemName());
        m.put("itemCode", r.getItemCode());
        m.put("quantity", r.getQuantity());
        m.put("unitPrice", r.getUnitPrice());
        m.put("totalAmount", r.getTotalAmount());
        m.put("status", r.getStatus());
        m.put("statusName", statusName(r.getStatus()));
        m.put("payTime", r.getPayTime());
        m.put("sourceId", r.getSourceId());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    private Map<String, Object> toMap(ExpenseRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("registerId", r.getRegisterId());
        m.put("patientId", r.getPatientId());
        m.put("patientName", r.getPatientName());
        m.put("categoryId", r.getCategoryId());
        m.put("categoryName", r.getCategoryName());
        m.put("itemId", r.getItemId());
        m.put("itemName", r.getItemName());
        m.put("itemCode", r.getItemCode());
        m.put("quantity", r.getQuantity());
        m.put("unitPrice", r.getUnitPrice());
        m.put("totalAmount", r.getTotalAmount());
        m.put("status", r.getStatus());
        m.put("statusName", statusName(r.getStatus()));
        m.put("payTime", r.getPayTime());
        m.put("refundTime", r.getRefundTime());
        m.put("operatorId", r.getOperatorId());
        m.put("operatorName", r.getOperatorName());
        m.put("remark", r.getRemark());
        m.put("sourceId", r.getSourceId());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    private boolean isTechFeeItemCode(String itemCode) {
        return "CHECK_FEE".equals(itemCode)
                || "INSPECTION_FEE".equals(itemCode)
                || "DISPOSAL_FEE".equals(itemCode);
    }

    private String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待缴费";
            case 1 -> "已缴费";
            case 2 -> "已退款";
            case 3 -> "已作废";
            default -> "未知";
        };
    }

    private String businessTypeFor(String itemCode) {
        if ("REGISTRATION_FEE".equals(itemCode)) return "REGISTRATION";
        if ("MEDICATION_FEE".equals(itemCode)) return "MEDICATION";
        if ("CHECK_FEE".equals(itemCode) || "INSPECTION_FEE".equals(itemCode)) return "EXAMINATION";
        if ("DISPOSAL_FEE".equals(itemCode)) return "DISPOSAL";
        if ("EXAMINATION_FEE".equals(itemCode)) return "EXAMINATION";
        return "OTHER";
    }

    private void fireOnFeePaidCallback(Long registerId, String itemCode, Long itemId, BigDecimal paidAmount, Long operatorId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("itemCode", itemCode);
            body.put("itemId", itemId);
            body.put("paidAmount", paidAmount);
            body.put("operatorId", operatorId);
            registrationFeignClient.notifyFeePaid(registerId, body);
            log.info("on-fee-paid 回调成功 | registerId={}, itemId={}", registerId, itemId);
        } catch (Exception e) {
            // 回调失败不影响支付主流程，registration 定时任务或下次支付时会重算 summary
            log.error("on-fee-paid 回调失败 | registerId={}, itemId={}（registration 后续汇总会兜底）",
                    registerId, itemId, e);
        }
    }

    private ExpenseRecord buildFromRequest(Map<String, Object> body, String itemCode, int status) {
        ExpenseRecord r = new ExpenseRecord();
        r.setRegisterId(lng(body, "registerId"));
        r.setPatientId(lng(body, "patientId"));
        r.setPatientName(str(body, "patientName"));
        r.setCategoryId(lng(body, "categoryId"));
        r.setCategoryName(str(body, "categoryName"));
        r.setItemId(lng(body, "itemId"));
        r.setItemName(str(body, "itemName") != null ? str(body, "itemName") : defaultItemName(itemCode));
        r.setItemCode(itemCode);
        r.setQuantity(intVal(body, "quantity", 1));
        r.setUnitPrice(dec(body, "unitPrice"));
        BigDecimal amount = dec(body, "amount");
        r.setTotalAmount(amount != null ? amount : r.getUnitPrice());
        r.setStatus(status);
        r.setOperatorId(lng(body, "operatorId"));
        r.setOperatorName(str(body, "operatorName"));
        r.setRemark(str(body, "remark"));
        r.setSourceId(lng(body, "sourceId"));
        return r;
    }

    private String defaultItemName(String itemCode) {
        return switch (itemCode) {
            case "REGISTRATION_FEE" -> "挂号费";
            case "MEDICATION_FEE" -> "药品费";
            case "CHECK_FEE" -> "检查费";
            case "INSPECTION_FEE" -> "检验费";
            case "DISPOSAL_FEE" -> "处置费";
            case "EXAMINATION_FEE" -> "检查费";
            default -> itemCode;
        };
    }

    private Map<String, Object> createdResult(Long id, boolean created) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", id);
        m.put("created", created);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMapData(Map<String, Object> response, String errorMessage) {
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(500, errorMessage);
        }
        return (Map<String, Object>) dataMap;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static Long lng(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private static Integer intVal(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    private static BigDecimal dec(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }
}
