package com.xikang.pharmacy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
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
    private final MedicationGuideMapper medicationGuideMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final AiPharmacyClient aiPharmacyClient;
    private final ObjectMapper objectMapper;

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

    /**
     * 药品列表分页查询（照搬 physician-service getDrugsPage 模式）。
     * 入参 dosageForm → drug_dosage，category → drug_type。
     */
    public Map<String, Object> getDrugsPage(String keyword, String dosageForm, String category,
                                            Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (safePage - 1) * safeSize;
        long total = drugInfoMapper.countDrugs(keyword, dosageForm, category);
        List<DrugInfo> list = drugInfoMapper.selectDrugsPage(keyword, dosageForm, category, offset, safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    /**
     * 低库存分页查询。
     */
    public Map<String, Object> getLowStockDrugsPage(Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (safePage - 1) * safeSize;
        long total = drugInfoMapper.countLowStock();
        List<DrugInfo> list = drugInfoMapper.selectLowStockPage(offset, safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    /** 已用分类（drug_type）：西药/中成药/生物制品 */
    public List<String> getCategories() {
        return drugInfoMapper.selectDrugTypes();
    }

    /** 已用剂型（drug_dosage），供前端动态下拉 */
    public List<String> getDosageForms() {
        return drugInfoMapper.selectDosageForms();
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
        return toExpiringStockView(drugStockMapper.selectExpiring(days));
    }

    /**
     * 近效期可用批次分页（照搬 physician 分页响应结构）。
     */
    public Map<String, Object> getExpiringStockPage(int days, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (safePage - 1) * safeSize;
        long total = drugStockMapper.countExpiring(days);
        List<Map<String, Object>> list = toExpiringStockView(
            drugStockMapper.selectExpiringPage(days, offset, safeSize));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    /** DrugStock → 前端视图（带药品名、剩余天数）。全量与分页共用。 */
    private List<Map<String, Object>> toExpiringStockView(List<DrugStock> stocks) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return stocks.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("drugId", s.getDrugId());
            DrugInfo drug = s.getDrugId() != null ? drugInfoMapper.selectById(s.getDrugId()) : null;
            map.put("drugName", drug != null ? drug.getDrugName() : null);
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
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(400, "入库数量必须大于 0");
        }
        String location = (String) inboundInfo.get("location");
        String batchNumber = (String) inboundInfo.get("batchNumber");
        java.time.LocalDate productionDate = inboundInfo.get("productionDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("productionDate").toString())
            : null;
        java.time.LocalDate expiryDate = inboundInfo.get("expiryDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("expiryDate").toString())
            : null;

        java.time.LocalDate today = java.time.LocalDate.now();
        if (expiryDate != null) {
            if (expiryDate.isBefore(today)) {
                throw new BusinessException(400, "效期日期不能早于今天");
            }
            if (productionDate != null && !productionDate.isBefore(expiryDate)) {
                throw new BusinessException(400, "生产日期必须早于效期日期");
            }
        }

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
        transaction.setDrugName(drug != null ? drug.getDrugName() : null);
        transaction.setQuantity(quantity);
        transaction.setUnitPrice(drug != null ? drug.getDrugPrice() : null);
        transaction.setTotalAmount(drug != null && drug.getDrugPrice() != null
            ? drug.getDrugPrice().multiply(BigDecimal.valueOf(quantity))
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
        transaction.setDrugName(before != null ? before.getDrugName() : null);
        transaction.setQuantity(diff);
        transaction.setUnitPrice(before != null ? before.getDrugPrice() : null);
        transaction.setTotalAmount(before != null && before.getDrugPrice() != null
            ? before.getDrugPrice().multiply(BigDecimal.valueOf(diff))
            : null);
        transaction.setReason("库存盘点调整：" + beforeQty + " → " + quantity);
        transaction.setTransactionTime(LocalDateTime.now());
        pharmacyTransactionMapper.insert(transaction);

        log.info("库存盘点成功 | drugId={}, before={}, after={}, diff={}", drugId, beforeQty, quantity, diff);
    }

    // ==================== 发药 ====================

    /**
     * 待发药列表（按挂号聚合）。
     * 每条附带药品费支付状态（paid），用于前端禁用「确认发药」按钮。
     */
    public List<Map<String, Object>> getPendingDispensing(Long registrationId) {
        List<Prescription> prescriptions = prescriptionMapper.selectPending(registrationId);
        return prescriptions.stream()
            .peek(p -> p.setPaid(isMedicationPaid(p.getRegisterId())))
            .map(this::toPrescriptionMap)
            .toList();
    }

    /**
     * 历史处方组合查询（按 patientId / status / 日期范围）。
     */
    public List<Map<String, Object>> queryPrescriptions(Long patientId, Integer status,
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Prescription> prescriptions = prescriptionMapper.selectByConditions(
                patientId, status, startDate, endDate);
        return prescriptions.stream()
            .peek(p -> p.setPaid(isMedicationPaid(p.getRegisterId())))
            .map(this::toPrescriptionMap)
            .toList();
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
        // 填充 paid 字段，前端发药按钮的 disabled 依赖此字段
        head.setPaid(isMedicationPaid(registerId));
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> prescriptionMap = toPrescriptionMap(head);
        // 已发药/已退药时，附带发药单号（来自最新的 dispensing 行）
        // 详情区"发药单号 / 发药人 / 发药时间"展示用，避免再开"查看发药单"弹窗
        Integer status = head.getDispensationStatus();
        if (status != null && status != 0) {
            List<Dispensing> dispensings = dispensingMapper.selectByRegisterId(registerId);
            if (!dispensings.isEmpty()) {
                // 取最新一条（按 id desc 在 mapper 内排，见 DispensingMapper.xml）
                Dispensing latest = dispensings.get(0);
                prescriptionMap.put("dispensingNo", latest.getDispensingNo());
            }
        }
        result.put("prescription", prescriptionMap);
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

        // 1.1 校验：药品费必须已缴清才能发药
        assertMedicationPaid(registerId);

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
                throw new BusinessException(400, "药品库存不足: " + drug.getDrugName()
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

        // 5. 事务提交后异步创建随访 + 异步生成用药指导单数据（PDF 延迟到下载时渲染）
        registerFollowUpAfterCommit(registerId, head.getPatientId(), head.getId());
        registerMedicationGuideAfterCommit(registerId, head.getPatientId(), head.getId());

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

    /**
     * 注册事务提交后的用药指导单生成。失败不抛异常，仅写 failed 记录。
     */
    private void registerMedicationGuideAfterCommit(Long registerId, Long patientId, Long prescriptionId) {
        Runnable guideTask = () -> {
            try {
                generateAndSaveMedicationGuide(registerId, patientId, prescriptionId);
            } catch (Exception e) {
                log.warn("用药指导单生成失败（异步）| registerId={}", registerId, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guideTask.run();
                }
            });
        } else {
            guideTask.run();
        }
    }

    // ==================== 用药指导单 ====================

    /**
     * 组装 ctx 并调 ai-pharmacy 生成指导单，落 medication_guide 表。
     * 生成成功 status='success'；调用失败 status='failed'，仍写一条记录便于追溯。
     */
    public void generateAndSaveMedicationGuide(Long registerId, Long patientId, Long prescriptionId) {
        log.info("生成用药指导单 | registerId={}, patientId={}, prescriptionId={}",
            registerId, patientId, prescriptionId);

        // 1. 组装 ctx
        Map<String, Object> ctx = buildMedicationGuideContext(registerId, patientId, prescriptionId);

        Map<String, Object> guideContent;
        String source;
        String status;
        String errorMessage = null;

        try {
            guideContent = aiPharmacyClient.generateMedicationGuide(ctx);
            source = "ai";
            status = "success";
            // ai-pharmacy 走了降级时 modelVersion 会带 "(fallback)"，这里据此调整 source
            Object mv = guideContent.get("modelVersion");
            if (mv instanceof String s && s.contains("fallback")) {
                source = "fallback";
            }
        } catch (Exception e) {
            log.warn("调 ai-pharmacy 生成指导单失败，落 failed 记录 | registerId={}", registerId, e);
            guideContent = null;
            source = "ai";
            status = "failed";
            errorMessage = e.getMessage();
        }

        saveMedicationGuideRecord(registerId, patientId, prescriptionId, ctx, guideContent, source, status, errorMessage);
    }

    private Map<String, Object> buildMedicationGuideContext(Long registerId, Long patientId, Long prescriptionId) {
        // 处方聚合头
        List<Prescription> heads = prescriptionMapper.selectByRegisterId(registerId);
        Prescription head = heads.isEmpty() ? null : heads.get(0);
        String patientName = head != null ? head.getPatientName() : null;

        // 明细
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(registerId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PrescriptionDetail d : details) {
            DrugInfo drug = d.getDrugId() != null ? drugInfoMapper.selectById(d.getDrugId()) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("drugId", d.getDrugId());
            item.put("drugName", d.getDrugName() != null ? d.getDrugName() : (drug != null ? drug.getDrugName() : null));
            item.put("drugFormat", drug != null ? drug.getDrugFormat() : null);
            item.put("drugDosage", drug != null ? drug.getDrugDosage() : null);
            item.put("quantity", d.getQuantity());
            item.put("usageText", d.getUsage());
            item.put("instructions", drug != null ? drug.getInstructions() : null);
            item.put("contraindications", drug != null ? drug.getContraindications() : null);
            item.put("adverseReactions", drug != null ? drug.getAdverseReactions() : null);
            item.put("storageConditions", drug != null ? drug.getStorageConditions() : null);
            items.add(item);
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("registerId", registerId);
        ctx.put("patientId", patientId);
        ctx.put("patientName", patientName);
        ctx.put("diagnosis", head != null ? head.getDiagnosis() : null);
        ctx.put("items", items);
        return ctx;
    }

    private void saveMedicationGuideRecord(Long registerId, Long patientId, Long prescriptionId,
            Map<String, Object> ctx, Map<String, Object> guideContent,
            String source, String status, String errorMessage) {
        try {
            MedicationGuide record = new MedicationGuide();
            record.setRegisterId(registerId);
            record.setPrescriptionId(prescriptionId);
            record.setPatientId(patientId);
            record.setPatientName((String) ctx.get("patientName"));
            record.setGuideContent(guideContent != null ? objectMapper.writeValueAsString(guideContent) : "{}");
            record.setSource(source);
            record.setStatus(status);
            record.setErrorMessage(errorMessage);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            medicationGuideMapper.insert(record);
            log.info("用药指导单记录已落库 | registerId={}, status={}, source={}",
                registerId, status, source);
        } catch (Exception e) {
            log.error("落库 medication_guide 失败 | registerId={}", registerId, e);
        }
    }

    /**
     * 查询最新一条用药指导单。前端探测按钮状态用。
     */
    public MedicationGuide getLatestMedicationGuide(Long registerId) {
        return medicationGuideMapper.selectLatestByRegisterId(registerId);
    }

    /**
     * 手动重试生成（同步）。返回最新一条记录。
     */
    public MedicationGuide retryMedicationGuide(Long registerId) {
        // 先看是否有 patientId/prescriptionId（取发药单据上的）
        List<Prescription> heads = prescriptionMapper.selectByRegisterId(registerId);
        if (heads.isEmpty()) {
            throw new BusinessException(400, "处方不存在");
        }
        Prescription head = heads.get(0);
        generateAndSaveMedicationGuide(registerId, head.getPatientId(), head.getId());
        return medicationGuideMapper.selectLatestByRegisterId(registerId);
    }

    // ==================== 患者端：处方查看 + 出账 ====================

    /**
     * 患者端「我的处方」页：返回该患者所有处方（按挂号聚合），
     * 并对每个"待发药"挂号幂等地生成药品费 expense_record 行。
     * 已发药/已退药的处方不再出账（防止脏数据）。
     * 已存在 MEDICATION_FEE 行的挂号不重写，金额以已落库的为准。
     */
    @Transactional
    public List<Map<String, Object>> getPatientPrescriptions(Long patientId) {
        List<Prescription> heads = prescriptionMapper.selectByPatientId(patientId);
        for (Prescription head : heads) {
            // 只对待发药（dispensationStatus=0）的处方出账
            if (head.getDispensationStatus() != null && head.getDispensationStatus() == 0) {
                ensureMedicationFeeCreated(head);
            }
            head.setPaid(isMedicationPaid(head.getRegisterId()));
        }
        return heads.stream().map(this::toPrescriptionMap).toList();
    }

    /**
     * 幂等出账：DB 部分唯一索引 + ON CONFLICT DO NOTHING 兜底并发。
     * 金额 > 0：写 status=0（待缴费）；金额 = 0/null（数据异常）：写 status=1 直接视为已结清，
     *           否则 payMedication 会因 amount<=0 抛异常导致流程卡死。
     */
    private void ensureMedicationFeeCreated(Prescription head) {
        Long registerId = head.getRegisterId();
        if (registerId == null) return;

        BigDecimal amount = head.getTotalAmount();
        boolean zeroAmount = amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
        int initialStatus = zeroAmount ? 1 : 0;
        BigDecimal safeAmount = zeroAmount ? BigDecimal.ZERO : amount;

        int affected = expenseRecordMapper.insertMedicationFee(
            registerId,
            head.getPatientId(),
            head.getPatientName(),
            safeAmount,
            initialStatus,
            "医生开药后药房自动出账",
            LocalDateTime.now()
        );
        if (affected > 0) {
            log.info("药品费出账 | registerId={}, patientId={}, amount={}, initialStatus={}",
                registerId, head.getPatientId(), safeAmount, initialStatus);
        }
    }

    /**
     * 该挂号的药品费是否已缴清（status=1）。
     * 未出账、已退款、已作废都视为未缴清。
     */
    private boolean isMedicationPaid(Long registerId) {
        if (registerId == null) return false;
        Integer status = expenseRecordMapper.selectMedicationFeeStatus(registerId);
        return status != null && status == 1;
    }

    /**
     * 发药前置校验：药品费必须已缴清。
     */
    private void assertMedicationPaid(Long registerId) {
        Integer status = expenseRecordMapper.selectMedicationFeeStatus(registerId);
        if (status == null) {
            throw new BusinessException(400, "该挂号尚未生成药品费账单，请患者在患者端查看处方后再发药");
        }
        if (status != 1) {
            throw new BusinessException(400, "患者尚未支付药品费，无法发药");
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

        // 退药明细：只对 drug_state='已发' 的行恢复库存，避免重复回滚
        List<PrescriptionDetail> allDetails = prescriptionDetailMapper.selectByPrescriptionId(registerId);
        List<PrescriptionDetail> details = allDetails.stream()
            .filter(d -> "已发".equals(d.getDrugState()))
            .toList();
        if (details.isEmpty()) {
            throw new BusinessException(400, "没有可退药的处方行");
        }
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
        drugInfo.put("drugName", drug.getDrugName());
        drugInfo.put("drugFormat", drug.getDrugFormat());
        drugInfo.put("drugDosage", drug.getDrugDosage());
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

    // ==================== 批量入库 ====================

    /**
     * 批量入库：单事务，要么全部成功要么全部回滚。
     * 入参每个 item 必填：drugId、quantity、batchNumber、productionDate、expiryDate、location。
     */
    @Transactional
    public Map<String, Object> batchInbound(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "批量入库不能为空");
        }

        List<String> errors = new ArrayList<>();
        int totalItems = 0;
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        // 预先校验所有条目，失败一次抛出整体回滚
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String prefix = "第 " + (i + 1) + " 行：";

            Object drugIdObj = item.get("drugId");
            if (drugIdObj == null) {
                errors.add(prefix + "缺少 drugId");
                continue;
            }
            Long drugId = ((Number) drugIdObj).longValue();
            Integer quantity = asInt(item.get("quantity"));
            String batchNumber = (String) item.get("batchNumber");
            String location = (String) item.get("location");
            java.time.LocalDate productionDate = item.get("productionDate") != null
                ? java.time.LocalDate.parse(item.get("productionDate").toString())
                : null;
            java.time.LocalDate expiryDate = item.get("expiryDate") != null
                ? java.time.LocalDate.parse(item.get("expiryDate").toString())
                : null;

            if (quantity == null || quantity <= 0) {
                errors.add(prefix + "入库数量必须大于 0");
            }
            if (batchNumber == null || batchNumber.isBlank()) {
                errors.add(prefix + "批号不能为空");
            }
            if (location == null || location.isBlank()) {
                errors.add(prefix + "货位不能为空");
            }
            if (productionDate == null) {
                errors.add(prefix + "生产日期不能为空");
            }
            if (expiryDate == null) {
                errors.add(prefix + "失效日期不能为空");
            }
            if (productionDate != null && expiryDate != null) {
                java.time.LocalDate today = java.time.LocalDate.now();
                if (expiryDate.isBefore(today)) {
                    errors.add(prefix + "失效日期不能早于今天");
                } else if (!productionDate.isBefore(expiryDate)) {
                    errors.add(prefix + "生产日期必须早于失效日期");
                }
            }

            DrugInfo drug = drugInfoMapper.selectById(drugId);
            if (drug == null) {
                errors.add(prefix + "药品不存在 (id=" + drugId + ")");
            } else {
                // 同药品同批号在本药品下不能重复（防御性）
                // 这里不强制做唯一性检查，由 DB 唯一索引/业务规则决定
                totalItems++;
                totalQuantity += quantity == null ? 0 : quantity;
                if (drug.getDrugPrice() != null && quantity != null) {
                    totalAmount = totalAmount.add(drug.getDrugPrice().multiply(BigDecimal.valueOf(quantity)));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(400, "批量入库校验失败：\n" + String.join("\n", errors));
        }

        // 全部校验通过，开始执行
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long drugId = ((Number) item.get("drugId")).longValue();
            Integer quantity = asInt(item.get("quantity"));
            String batchNumber = (String) item.get("batchNumber");
            String location = (String) item.get("location");
            java.time.LocalDate productionDate = item.get("productionDate") != null
                ? java.time.LocalDate.parse(item.get("productionDate").toString())
                : null;
            java.time.LocalDate expiryDate = item.get("expiryDate") != null
                ? java.time.LocalDate.parse(item.get("expiryDate").toString())
                : null;

            DrugInfo drug = drugInfoMapper.selectById(drugId);

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

            PharmacyTransaction transaction = new PharmacyTransaction();
            transaction.setType("入库");
            transaction.setDrugId(drugId);
            transaction.setDrugName(drug != null ? drug.getDrugName() : null);
            transaction.setQuantity(quantity);
            transaction.setUnitPrice(drug != null ? drug.getDrugPrice() : null);
            transaction.setTotalAmount(drug != null && drug.getDrugPrice() != null
                ? drug.getDrugPrice().multiply(BigDecimal.valueOf(quantity))
                : null);
            transaction.setReason("批量入库批号：" + batchNumber);
            transaction.setTransactionTime(now);
            pharmacyTransactionMapper.insert(transaction);

            Map<String, Object> r = new HashMap<>();
            r.put("drugId", drugId);
            r.put("drugName", drug != null ? drug.getDrugName() : null);
            r.put("batchId", drugStock.getId());
            r.put("batchNumber", batchNumber);
            r.put("quantity", quantity);
            results.add(r);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", items.size());
        result.put("totalQuantity", totalQuantity);
        result.put("totalAmount", totalAmount);
        result.put("results", results);
        log.info("批量入库成功 | count={}, totalQuantity={}, totalAmount={}",
            items.size(), totalQuantity, totalAmount);
        return result;
    }

    // ==================== 报损 ====================

    /**
     * 药品报损：扣减 drug_info 总库存 + 写 transaction(type='报损')。
     * 不动 drug_stock 具体批次（按药品粒度报损，不指定批次时从最早过期批次扣减）。
     */
    @Transactional
    public void reportLoss(Long drugId, Map<String, Object> lossInfo) {
        Integer quantity = asInt(lossInfo.get("quantity"));
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(400, "报损数量必须大于 0");
        }
        String reason = lossInfo.get("reason") != null
            ? lossInfo.get("reason").toString()
            : "药品报损";
        Long batchId = lossInfo.get("batchId") != null
            ? ((Number) lossInfo.get("batchId")).longValue()
            : null;
        Long operatorId = lossInfo.get("operatorId") != null
            ? ((Number) lossInfo.get("operatorId")).longValue()
            : null;
        String operatorName = (String) lossInfo.get("operatorName");

        DrugInfo drug = drugInfoMapper.selectById(drugId);
        if (drug == null) {
            throw new BusinessException(404, "药品不存在");
        }
        if (drug.getStockQuantity() == null || drug.getStockQuantity() < quantity) {
            throw new BusinessException(400, "药品库存不足，当前库存: "
                + (drug.getStockQuantity() == null ? 0 : drug.getStockQuantity())
                + "，报损数量: " + quantity);
        }

        // 优先从指定批次扣减；否则按"早失效优先"扣减
        if (batchId != null) {
            DrugStock batch = drugStockMapper.selectById(batchId);
            if (batch == null || !batch.getDrugId().equals(drugId)) {
                throw new BusinessException(400, "批次不存在或不属于该药品");
            }
            if (batch.getQuantity() == null || batch.getQuantity() < quantity) {
                throw new BusinessException(400, "批次库存不足，当前: "
                    + (batch.getQuantity() == null ? 0 : batch.getQuantity())
                    + "，报损数量: " + quantity);
            }
            drugStockMapper.updateQuantity(batchId, batch.getQuantity() - quantity);
        } else {
            List<DrugStock> stocks = drugStockMapper.selectByDrugIdAndStatus(drugId);
            int remain = quantity;
            for (DrugStock s : stocks) {
                if (remain <= 0) break;
                int q = s.getQuantity() == null ? 0 : s.getQuantity();
                if (q <= 0) continue;
                int deduct = Math.min(q, remain);
                drugStockMapper.updateQuantity(s.getId(), q - deduct);
                remain -= deduct;
            }
            if (remain > 0) {
                throw new BusinessException(400, "批次库存不足以扣减报损数量");
            }
        }

        // 扣 drug_info 总库存
        drugInfoMapper.decreaseStock(drugId, quantity);

        // 写流水
        PharmacyTransaction transaction = new PharmacyTransaction();
        transaction.setType("报损");
        transaction.setDrugId(drugId);
        transaction.setDrugName(drug.getDrugName());
        transaction.setQuantity(-quantity);
        transaction.setUnitPrice(drug.getDrugPrice());
        transaction.setTotalAmount(drug.getDrugPrice() != null
            ? drug.getDrugPrice().multiply(BigDecimal.valueOf(quantity)).negate()
            : null);
        transaction.setOperatorId(operatorId);
        transaction.setOperatorName(operatorName);
        transaction.setReason(reason);
        transaction.setTransactionTime(LocalDateTime.now());
        pharmacyTransactionMapper.insert(transaction);

        log.info("药品报损成功 | drugId={}, quantity={}, batchId={}, reason={}",
            drugId, quantity, batchId, reason);
    }

    // ==================== 批次冻结/解冻 ====================

    /**
     * 冻结批次：drug_stock.status = 0
     */
    @Transactional
    public void freezeBatch(Long batchId) {
        DrugStock batch = drugStockMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (batch.getStatus() != null && batch.getStatus() == 0) {
            return; // 已经是冻结态，幂等
        }
        drugStockMapper.updateStatus(batchId, 0);
        log.info("批次冻结 | batchId={}, drugId={}", batchId, batch.getDrugId());
    }

    /**
     * 解冻批次：drug_stock.status = 1
     */
    @Transactional
    public void unfreezeBatch(Long batchId) {
        DrugStock batch = drugStockMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(404, "批次不存在");
        }
        if (batch.getStatus() != null && batch.getStatus() == 1) {
            return; // 已经是可用态，幂等
        }
        drugStockMapper.updateStatus(batchId, 1);
        log.info("批次解冻 | batchId={}, drugId={}", batchId, batch.getDrugId());
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
        map.put("paid", prescription.getPaid());
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
