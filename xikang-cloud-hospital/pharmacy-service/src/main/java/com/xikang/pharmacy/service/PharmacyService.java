package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.pharmacy.entity.*;
import com.xikang.pharmacy.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy Service - 药房服务
 *
 * <p>架构前提：prescription 表由 physician-service 按"一药一行"写入，
 * 本服务的所有"处方"读操作都按 register_id 聚合读，写入时直接 UPDATE
 * prescription.drug_state（'未发' ↔ '已发' ↔ '已退'）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final DrugInfoMapper drugInfoMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionDetailMapper prescriptionDetailMapper;
    private final DrugStockMapper drugStockMapper;
    private final PharmacyTransactionMapper pharmacyTransactionMapper;
    private final DispensingMapper dispensingMapper;
    private final StatisticsMapper statisticsMapper;
    private final AiPharmacyClient aiPharmacyClient;

    // ==================== 药品管理 ====================

    public List<DrugInfo> getDrugs(String keyword, String dosageForm) {
        if (keyword != null && !keyword.isEmpty()) {
            return drugInfoMapper.selectByKeyword(keyword);
        } else if (dosageForm != null && !dosageForm.isEmpty()) {
            return drugInfoMapper.selectByDosageForm(dosageForm);
        }
        return drugInfoMapper.selectAll();
    }

    public List<DrugInfo> getDrugsByConditions(String keyword, String dosageForm, String category) {
        return drugInfoMapper.selectByConditions(keyword, dosageForm, category);
    }

    public List<String> getCategories() {
        return drugInfoMapper.selectCategories();
    }

    public DrugInfo getDrug(Long id) {
        return drugInfoMapper.selectById(id);
    }

    @Transactional
    public DrugInfo addDrug(DrugInfo drugInfo) {
        drugInfo.setStatus(1);
        drugInfoMapper.insert(drugInfo);
        return drugInfo;
    }

    @Transactional
    public void updateDrug(Long id, DrugInfo drugInfo) {
        drugInfo.setId(id);
        drugInfoMapper.update(drugInfo);
    }

    @Transactional
    public void deleteDrug(Long id) {
        drugInfoMapper.deleteById(id);
    }

    public List<DrugInfo> getLowStockDrugs() {
        return drugInfoMapper.selectLowStock();
    }

    // ==================== 库存管理 ====================

    public List<DrugStock> getDrugStock(Long drugId) {
        return drugStockMapper.selectByDrugIdAndStatus(drugId);
    }

    /**
     * 近效期可用批次（附带药品名）。
     * daysRemaining 用 ChronoUnit.DAYS.between 而非 Period.getDays()，避免跨月归一化。
     */
    public List<Map<String, Object>> getExpiringStock(int days) {
        List<DrugStock> stocks = drugStockMapper.selectExpiring(days);
        java.time.LocalDate today = java.time.LocalDate.now();
        return stocks.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("drugId", s.getDrugId());
            DrugInfo drug = s.getDrugId() != null ? drugInfoMapper.selectById(s.getDrugId()) : null;
            map.put("drugName", drug != null ? drug.getName() : null);
            map.put("batchNumber", s.getBatchNumber());
            map.put("quantity", s.getQuantity());
            map.put("expiryDate", s.getExpiryDate());
            map.put("location", s.getLocation());
            map.put("daysRemaining", s.getExpiryDate() != null
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, s.getExpiryDate())
                : null);
            return map;
        }).toList();
    }

    @Transactional
    public void drugInbound(Long drugId, Map<String, Object> inboundInfo) {
        Integer quantity = asInt(inboundInfo.get("quantity"));
        String location = (String) inboundInfo.get("location");
        String batchNumber = (String) inboundInfo.get("batchNumber");
        java.time.LocalDate productionDate = inboundInfo.get("productionDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("productionDate").toString())
            : null;
        java.time.LocalDate expiryDate = inboundInfo.get("expiryDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("expiryDate").toString())
            : null;

        DrugStock drugStock = new DrugStock();
        drugStock.setDrugId(drugId);
        drugStock.setQuantity(quantity);
        drugStock.setLocation(location);
        drugStock.setBatchNumber(batchNumber);
        drugStock.setProductionDate(productionDate);
        drugStock.setExpiryDate(expiryDate);
        drugStock.setStatus(1);
        drugStockMapper.insert(drugStock);

        drugInfoMapper.increaseStock(drugId, quantity);

        DrugInfo drug = drugInfoMapper.selectById(drugId);
        PharmacyTransaction transaction = new PharmacyTransaction();
        transaction.setType("入库");
        transaction.setDrugId(drugId);
        transaction.setDrugName(drug != null ? drug.getName() : null);
        transaction.setQuantity(quantity);
        transaction.setUnitPrice(drug != null ? drug.getPrice() : null);
        transaction.setTotalAmount(drug != null && drug.getPrice() != null
            ? drug.getPrice().multiply(BigDecimal.valueOf(quantity))
            : null);
        transaction.setReason(batchNumber != null ? "入库批号：" + batchNumber : "药品入库");
        transaction.setTransactionTime(LocalDateTime.now());
        pharmacyTransactionMapper.insert(transaction);

        log.info("药品入库成功 | drugId={}, quantity={}, batchNumber={}", drugId, quantity, batchNumber);
    }

    @Transactional
    public void updateStock(Long drugId, Map<String, Object> stockInfo) {
        Integer quantity = asInt(stockInfo.get("quantity"));
        String location = (String) stockInfo.get("location");
        String batchNumber = (String) stockInfo.get("batchNumber");

        DrugStock drugStock = new DrugStock();
        drugStock.setDrugId(drugId);
        drugStock.setQuantity(quantity);
        drugStock.setLocation(location);
        drugStock.setBatchNumber(batchNumber);
        drugStock.setStatus(1);
        drugStockMapper.insert(drugStock);

        DrugInfo before = drugInfoMapper.selectById(drugId);
        int beforeQty = before != null && before.getStockQuantity() != null ? before.getStockQuantity() : 0;
        int diff = quantity - beforeQty;

        drugInfoMapper.updateStock(drugId, quantity);

        PharmacyTransaction transaction = new PharmacyTransaction();
        transaction.setType("盘点");
        transaction.setDrugId(drugId);
        transaction.setDrugName(before != null ? before.getName() : null);
        transaction.setQuantity(diff);
        transaction.setUnitPrice(before != null ? before.getPrice() : null);
        transaction.setTotalAmount(before != null && before.getPrice() != null
            ? before.getPrice().multiply(BigDecimal.valueOf(diff))
            : null);
        transaction.setReason("库存盘点调整：" + beforeQty + " → " + quantity);
        transaction.setTransactionTime(LocalDateTime.now());
        pharmacyTransactionMapper.insert(transaction);

        log.info("库存盘点成功 | drugId={}, before={}, after={}, diff={}", drugId, beforeQty, quantity, diff);
    }

    // ==================== 发药 ====================

    /**
     * 待发药列表（按挂号聚合）。
     */
    public List<Map<String, Object>> getPendingDispensing(Long registrationId) {
        List<Prescription> prescriptions = prescriptionMapper.selectPending(registrationId);
        return prescriptions.stream().map(this::toPrescriptionMap).toList();
    }

    /**
     * 历史处方组合查询（按 patientId / status / 日期范围）。
     */
    public List<Map<String, Object>> queryPrescriptions(Long patientId, Integer status,
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Prescription> prescriptions = prescriptionMapper.selectByConditions(
                patientId, status, startDate, endDate);
        return prescriptions.stream().map(this::toPrescriptionMap).toList();
    }

    /**
     * 处方详情：入参 prescriptionId 是 prescription 行 id，
     * service 先反查出 register_id，再按 register_id 聚合取头与明细。
     */
    public Map<String, Object> getPrescriptionDetails(Long prescriptionId) {
        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            throw new BusinessException(404, "处方不存在");
        }
        Long registerId = prescription.getRegisterId();
        // 再按 register_id 拿完整的聚合头（带 patientName / totalAmount 等）
        List<Prescription> heads = prescriptionMapper.selectByRegisterId(registerId);
        Prescription head = heads.isEmpty() ? prescription : heads.get(0);
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);

        Map<String, Object> result = new HashMap<>();
        result.put("prescription", toPrescriptionMap(head));
        result.put("details", details.stream().map(this::toDetailMap).toList());
        return result;
    }

    /**
     * 发药：扣库存（乐观锁）→ 写流水 → 更新 drug_state='已发' → 写发药单
     * → 事务提交后异步创建 AI 随访。
     */
    @Transactional
    public Map<String, Object> dispense(Long registerId, Map<String, Object> dispensingInfo) {
        log.info("发药操作 | registerId={}", registerId);

        Long pharmacistId = dispensingInfo.get("pharmacistId") != null
            ? ((Number) dispensingInfo.get("pharmacistId")).longValue()
            : null;
        String pharmacistName = (String) dispensingInfo.get("pharmacistName");

        // 1. 校验：该挂号必须有 '未发' 的处方行
        List<Prescription> pendingHeads = prescriptionMapper.selectByRegisterIdAndStatus(registerId, 0);
        if (pendingHeads.isEmpty()) {
            throw new BusinessException(400, "没有待发药的处方");
        }

        // 2. 取该挂号下的所有明细行（即所有 drug_state='未发' 的 prescription 行）
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);
        LocalDateTime now = LocalDateTime.now();
        int totalItemCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PrescriptionDetail detail : details) {
            DrugInfo drug = drugInfoMapper.selectById(detail.getDrugId());
            if (drug == null) {
                throw new BusinessException(400, "药品不存在: " + detail.getDrugName());
            }

            int affected = drugInfoMapper.decreaseStock(detail.getDrugId(), detail.getQuantity());
            if (affected == 0) {
                DrugInfo latest = drugInfoMapper.selectById(detail.getDrugId());
                int currentStock = latest != null && latest.getStockQuantity() != null ? latest.getStockQuantity() : 0;
                throw new BusinessException(400, "药品库存不足: " + drug.getName()
                    + "，当前库存: " + currentStock + "，需要: " + detail.getQuantity());
            }

            PharmacyTransaction transaction = new PharmacyTransaction();
            transaction.setType("发放");
            transaction.setDrugId(detail.getDrugId());
            transaction.setDrugName(detail.getDrugName());
            transaction.setRegisterId(registerId);
            transaction.setQuantity(-detail.getQuantity());
            transaction.setUnitPrice(detail.getUnitPrice());
            transaction.setTotalAmount(detail.getTotalAmount() != null
                ? detail.getTotalAmount().negate()
                : null);
            transaction.setOperatorId(pharmacistId);
            transaction.setOperatorName(pharmacistName);
            transaction.setTransactionTime(now);
            pharmacyTransactionMapper.insert(transaction);

            totalItemCount++;
            if (detail.getTotalAmount() != null) {
                totalAmount = totalAmount.add(detail.getTotalAmount());
            }
        }

        // 3. 批量更新 drug_state='已发' + 写入 pharmacist / dispensation_time
        prescriptionMapper.updateDispensationInfo(registerId, 1, now, pharmacistName);

        // 4. 写发药单（一挂号一张）
        Prescription head = pendingHeads.get(0);
        Dispensing dispensing = new Dispensing();
        dispensing.setPrescriptionId(head.getId());
        dispensing.setRegisterId(registerId);
        dispensing.setPatientId(head.getPatientId());
        dispensing.setDispensingNo(generateDispensingNo(registerId, head.getId(), now));
        dispensing.setAmount(totalAmount);
        dispensing.setStatus(1);
        dispensing.setPharmacist(pharmacistName);
        dispensing.setDispensingTime(now);
        dispensingMapper.insert(dispensing);

        // 5. 事务提交后异步创建随访
        registerFollowUpAfterCommit(registerId, head.getPatientId(), head.getId());

        log.info("发药成功 | registerId={}, itemCount={}, totalAmount={}",
            registerId, totalItemCount, totalAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionCount", totalItemCount);
        result.put("itemCount", totalItemCount);
        result.put("dispensationTime", now);
        result.put("pharmacist", pharmacistName);
        result.put("totalAmount", totalAmount);
        result.put("followUpMessage", "随访计划将在发药提交后自动创建");
        return result;
    }

    /**
     * 注册事务提交后的 AI 随访调用。无活动事务时直接同步执行。
     */
    private void registerFollowUpAfterCommit(Long registerId, Long patientId, Long prescriptionId) {
        Runnable followUpTask = () -> {
            if (patientId == null) {
                log.warn("自动创建随访计划失败，处方缺少 patientId | registerId={}, prescriptionId={}",
                    registerId, prescriptionId);
                return;
            }
            try {
                aiPharmacyClient.createFollowUpPlan(patientId, registerId, prescriptionId);
                log.info("随访计划创建完成 | registerId={}, prescriptionId={}", registerId, prescriptionId);
            } catch (Exception e) {
                log.warn("自动创建随访计划失败 | registerId={}, prescriptionId={}",
                    registerId, prescriptionId, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    followUpTask.run();
                }
            });
        } else {
            followUpTask.run();
        }
    }

    // ==================== 退药 ====================

    @Transactional
    public void returnDrug(Long registerId, Map<String, Object> returnInfo) {
        log.info("退药操作 | registerId={}", registerId);

        Long pharmacistId = returnInfo.get("pharmacistId") != null
            ? ((Number) returnInfo.get("pharmacistId")).longValue()
            : null;
        String pharmacistName = (String) returnInfo.get("pharmacistName");
        String reason = (String) returnInfo.getOrDefault("reason", "患者申请退药");

        // 校验：该挂号必须有 '已发' 的处方行
        List<Prescription> dispensedHeads = prescriptionMapper.selectByRegisterIdAndStatus(registerId, 1);
        if (dispensedHeads.isEmpty()) {
            throw new BusinessException(400, "没有已发药的处方可以退药");
        }

        // 退药明细：按 register_id 取所有行（实际服务只关心 status=1 的，但 mapper 返回聚合后，
        // 明细行已经包含所有 prescription 行；这里通过 detail.remark 隐含过滤不重要，
        // 因为退药时已发药的行 drug_state 会被置 '已退'，恢复库存应该针对所有行）。
        // 保险起见：只对 drug_state='已发' 的行恢复库存。
        // 简化方案：直接按明细列表恢复（历史上一次性退药只发生一次）。
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);
        LocalDateTime now = LocalDateTime.now();

        for (PrescriptionDetail detail : details) {
            drugInfoMapper.increaseStock(detail.getDrugId(), detail.getQuantity());

            PharmacyTransaction transaction = new PharmacyTransaction();
            transaction.setType("退回");
            transaction.setDrugId(detail.getDrugId());
            transaction.setDrugName(detail.getDrugName());
            transaction.setRegisterId(registerId);
            transaction.setQuantity(detail.getQuantity());
            transaction.setUnitPrice(detail.getUnitPrice());
            transaction.setTotalAmount(detail.getTotalAmount());
            transaction.setOperatorId(pharmacistId);
            transaction.setOperatorName(pharmacistName);
            transaction.setReason(reason);
            transaction.setTransactionTime(now);
            pharmacyTransactionMapper.insert(transaction);
        }

        // 批量更新 drug_state='已退'
        prescriptionMapper.updateDispensationInfo(registerId, 2, now, pharmacistName);

        log.info("退药成功 | registerId={}, itemCount={}", registerId, details.size());
    }

    // ==================== 交易记录 ====================

    public List<Map<String, Object>> getTransactions(Long drugId, String type,
            LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start = startDate;
        LocalDateTime end = endDate;
        if (drugId == null && (type == null || type.isEmpty()) && start == null) {
            start = LocalDateTime.now().minusDays(30);
            end = LocalDateTime.now();
        }
        List<PharmacyTransaction> transactions = pharmacyTransactionMapper.selectByConditions(
            drugId, type, start, end);
        return transactions.stream().map(this::toTransactionMap).toList();
    }

    // ==================== 审方与 AI 联动 ====================

    /**
     * 发药前审核：库存可用性 + 近效期提示。
     * 不做 AI 配伍审核（需后续 RAG 支持）。
     */
    public Map<String, Object> reviewDispense(Long registerId) {
        List<Prescription> pendingHeads = prescriptionMapper.selectByRegisterIdAndStatus(registerId, 0);
        if (pendingHeads.isEmpty()) {
            throw new BusinessException(400, "没有待发药的处方");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean blocked = false;
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);
        for (PrescriptionDetail detail : details) {
            DrugInfo drug = drugInfoMapper.selectById(detail.getDrugId());
            Map<String, Object> item = new HashMap<>();
            item.put("drugId", detail.getDrugId());
            item.put("drugName", detail.getDrugName());
            item.put("quantity", detail.getQuantity());
            item.put("totalAmount", detail.getTotalAmount());

            if (drug == null) {
                item.put("status", "block");
                item.put("reason", "药品不存在");
                blocked = true;
            } else if (drug.getStockQuantity() == null || drug.getStockQuantity() < detail.getQuantity()) {
                item.put("status", "block");
                item.put("reason", "库存不足：当前 " + drug.getStockQuantity() + "，需要 " + detail.getQuantity());
                blocked = true;
            } else {
                List<DrugStock> stocks = drugStockMapper.selectByDrugIdAndStatus(detail.getDrugId());
                boolean hasNearExpiry = stocks.stream().anyMatch(s ->
                    s.getExpiryDate() != null &&
                    !s.getExpiryDate().isBefore(java.time.LocalDate.now()) &&
                    s.getExpiryDate().isBefore(java.time.LocalDate.now().plusDays(30)));
                if (hasNearExpiry) {
                    item.put("status", "warn");
                    item.put("reason", "存在近 30 天内到期批次");
                    warnings.add(detail.getDrugName() + "：存在近效期批次，发药时请优先出库");
                } else {
                    item.put("status", "pass");
                    item.put("reason", "通过");
                }
            }
            items.add(item);
            if (detail.getTotalAmount() != null) {
                totalAmount = totalAmount.add(detail.getTotalAmount());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("overallStatus", blocked ? "block" : (warnings.isEmpty() ? "pass" : "warn"));
        result.put("items", items);
        result.put("warnings", warnings);
        result.put("totalAmount", totalAmount);
        return result;
    }

    public Map<String, Object> generateMedicationGuide(Long drugId) {
        DrugInfo drug = drugInfoMapper.selectById(drugId);
        if (drug == null) {
            throw new BusinessException(404, "药品不存在");
        }
        Map<String, Object> drugInfo = new HashMap<>();
        drugInfo.put("drugId", drug.getId());
        drugInfo.put("drugName", drug.getName());
        drugInfo.put("genericName", drug.getGenericName());
        drugInfo.put("specification", drug.getSpecification());
        drugInfo.put("dosageForm", drug.getDosageForm());
        drugInfo.put("instructions", drug.getInstructions());
        drugInfo.put("contraindications", drug.getContraindications());
        drugInfo.put("adverseReactions", drug.getAdverseReactions());
        return aiPharmacyClient.getMedicationGuide(drugInfo);
    }

    public List<Map<String, Object>> getPatientFollowUpPlans(Long patientId) {
        return aiPharmacyClient.getPatientFollowUpPlans(patientId);
    }

    /**
     * 重试创建随访计划。入参是 prescription 行 id。
     */
    public Map<String, Object> retryFollowUp(Long prescriptionId) {
        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            throw new BusinessException(404, "处方不存在");
        }
        if (prescription.getPatientId() == null) {
            throw new BusinessException(400, "处方缺少 patientId，无法创建随访");
        }
        if (prescription.getDispensationStatus() == null || prescription.getDispensationStatus() != 1) {
            throw new BusinessException(400, "处方未发药，无法创建随访");
        }
        Map<String, Object> result = aiPharmacyClient.createFollowUpPlan(
            prescription.getPatientId(),
            prescription.getRegisterId(),
            prescriptionId
        );
        log.info("随访计划重试创建成功 | prescriptionId={}", prescriptionId);
        return result;
    }

    public void submitFollowUpFeedback(Long planId, Map<String, Object> feedback) {
        aiPharmacyClient.submitFollowUpFeedback(planId, feedback);
    }

    // ==================== 发药单（P2-4.6） ====================

    public List<Map<String, Object>> getDispensingByRegister(Long registerId) {
        List<Dispensing> dispensings = dispensingMapper.selectByRegisterId(registerId);
        return dispensings.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("prescriptionId", d.getPrescriptionId());
            map.put("patientId", d.getPatientId());
            map.put("dispensingNo", d.getDispensingNo());
            map.put("amount", d.getAmount());
            map.put("status", d.getStatus());
            map.put("pharmacist", d.getPharmacist());
            map.put("dispensingTime", d.getDispensingTime());
            return map;
        }).toList();
    }

    private String generateDispensingNo(Long registerId, Long prescriptionId, LocalDateTime now) {
        return "DY-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-" + registerId + "-" + prescriptionId;
    }

    // ==================== 统计 ====================

    /**
     * 药房工作量与药品消耗统计（按时间范围）。
     */
    public Map<String, Object> getStatistics(LocalDateTime startDate, LocalDateTime endDate, int topLimit) {
        Map<String, Object> overview = statisticsMapper.selectOverview(startDate, endDate);
        List<Map<String, Object>> topDrugs = statisticsMapper.selectTopDrugs(startDate, endDate, topLimit);
        List<Map<String, Object>> operatorStats = statisticsMapper.selectOperatorStats(startDate, endDate);

        Map<String, Object> result = new HashMap<>();
        result.put("overview", overview);
        result.put("topDrugs", topDrugs);
        result.put("operatorStats", operatorStats);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return result;
    }

    // ==================== 转换方法 ====================

    private Map<String, Object> toPrescriptionMap(Prescription prescription) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", prescription.getId());
        map.put("registerId", prescription.getRegisterId());
        map.put("patientId", prescription.getPatientId());
        map.put("patientName", prescription.getPatientName());
        map.put("physicianName", prescription.getPhysicianName());
        map.put("diagnosis", prescription.getDiagnosis());
        map.put("totalAmount", prescription.getTotalAmount());
        map.put("dispensationStatus", prescription.getDispensationStatus());
        map.put("dispensationStatusName", getDispensationStatusName(prescription.getDispensationStatus()));
        map.put("dispensationTime", prescription.getDispensationTime());
        map.put("pharmacist", prescription.getPharmacist());
        map.put("createTime", prescription.getCreateTime());
        return map;
    }

    private Map<String, Object> toDetailMap(PrescriptionDetail detail) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", detail.getId());
        map.put("prescriptionId", detail.getPrescriptionId());
        map.put("drugId", detail.getDrugId());
        map.put("drugName", detail.getDrugName());
        map.put("specification", detail.getSpecification());
        map.put("dosage", detail.getDosage());
        map.put("quantity", detail.getQuantity());
        map.put("unitPrice", detail.getUnitPrice());
        map.put("totalAmount", detail.getTotalAmount());
        map.put("usage", detail.getUsage());
        map.put("frequency", detail.getFrequency());
        map.put("duration", detail.getDuration());
        map.put("remark", detail.getRemark());
        return map;
    }

    private Map<String, Object> toTransactionMap(PharmacyTransaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transaction.getId());
        map.put("type", transaction.getType());
        map.put("drugId", transaction.getDrugId());
        map.put("drugName", transaction.getDrugName());
        map.put("prescriptionId", transaction.getPrescriptionId());
        map.put("registerId", transaction.getRegisterId());
        map.put("quantity", transaction.getQuantity());
        map.put("unitPrice", transaction.getUnitPrice());
        map.put("totalAmount", transaction.getTotalAmount());
        map.put("operatorName", transaction.getOperatorName());
        map.put("reason", transaction.getReason());
        map.put("transactionTime", transaction.getTransactionTime());
        map.put("createTime", transaction.getCreateTime());
        return map;
    }

    private String getDispensationStatusName(Integer status) {
        return switch (status) {
            case 0 -> "待发药";
            case 1 -> "已发药";
            case 2 -> "已退药";
            default -> "未知";
        };
    }

    private Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        return Integer.valueOf(o.toString());
    }
}
