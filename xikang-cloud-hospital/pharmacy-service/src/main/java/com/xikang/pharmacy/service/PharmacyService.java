package com.xikang.pharmacy.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.pharmacy.entity.*;
import com.xikang.pharmacy.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy Service - 药房服务
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
    private final AiPharmacyClient aiPharmacyClient;

    // ==================== 药品管理 ====================

    /**
     * 获取药品列表
     */
    public List<DrugInfo> getDrugs(String keyword, String dosageForm) {
        if (keyword != null && !keyword.isEmpty()) {
            return drugInfoMapper.selectByKeyword(keyword);
        } else if (dosageForm != null && !dosageForm.isEmpty()) {
            return drugInfoMapper.selectByDosageForm(dosageForm);
        }
        return drugInfoMapper.selectAll();
    }

    /**
     * 获取药品详情
     */
    public DrugInfo getDrug(Long id) {
        return drugInfoMapper.selectById(id);
    }

    /**
     * 添加药品
     */
    @Transactional
    public DrugInfo addDrug(DrugInfo drugInfo) {
        drugInfo.setStatus(1);
        drugInfoMapper.insert(drugInfo);
        return drugInfo;
    }

    /**
     * 更新药品
     */
    @Transactional
    public void updateDrug(Long id, DrugInfo drugInfo) {
        drugInfo.setId(id);
        drugInfoMapper.update(drugInfo);
    }

    /**
     * 删除药品
     */
    @Transactional
    public void deleteDrug(Long id) {
        drugInfoMapper.deleteById(id);
    }

    /**
     * 获取低库存药品
     */
    public List<DrugInfo> getLowStockDrugs() {
        return drugInfoMapper.selectLowStock();
    }

    // ==================== 库存管理 ====================

    /**
     * 获取药品库存
     */
    public List<DrugStock> getDrugStock(Long drugId) {
        return drugStockMapper.selectByDrugIdAndStatus(drugId);
    }

    /**
     * 药品入库
     */
    @Transactional
    public void drugInbound(Long drugId, Map<String, Object> inboundInfo) {
        Integer quantity = (Integer) inboundInfo.get("quantity");
        String location = (String) inboundInfo.get("location");
        String batchNumber = (String) inboundInfo.get("batchNumber");
        java.time.LocalDate productionDate = inboundInfo.get("productionDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("productionDate").toString())
            : null;
        java.time.LocalDate expiryDate = inboundInfo.get("expiryDate") != null
            ? java.time.LocalDate.parse(inboundInfo.get("expiryDate").toString())
            : null;

        // 创建批次库存记录
        DrugStock drugStock = new DrugStock();
        drugStock.setDrugId(drugId);
        drugStock.setQuantity(quantity);
        drugStock.setLocation(location);
        drugStock.setBatchNumber(batchNumber);
        drugStock.setProductionDate(productionDate);
        drugStock.setExpiryDate(expiryDate);
        drugStock.setStatus(1);
        drugStockMapper.insert(drugStock);

        // 增加药品库存总量
        drugInfoMapper.increaseStock(drugId, quantity);

        log.info("药品入库成功 | drugId={}, quantity={}, batchNumber={}", drugId, quantity, batchNumber);
    }

    /**
     * 更新库存（直接设置，用于初始化或修正）
     */
    @Transactional
    public void updateStock(Long drugId, Map<String, Object> stockInfo) {
        Integer quantity = (Integer) stockInfo.get("quantity");
        String location = (String) stockInfo.get("location");
        String batchNumber = (String) stockInfo.get("batchNumber");

        DrugStock drugStock = new DrugStock();
        drugStock.setDrugId(drugId);
        drugStock.setQuantity(quantity);
        drugStock.setLocation(location);
        drugStock.setBatchNumber(batchNumber);
        drugStock.setStatus(1);
        drugStockMapper.insert(drugStock);

        // 直接设置药品库存总量
        drugInfoMapper.updateStock(drugId, quantity);
    }

    // ==================== 发药 ====================

    /**
     * 获取待发药处方列表
     */
    public List<Map<String, Object>> getPendingDispensing(Long registrationId) {
        List<Prescription> prescriptions;
        if (registrationId != null) {
            prescriptions = prescriptionMapper.selectByRegisterIdAndStatus(registrationId, 0);
        } else {
            prescriptions = prescriptionMapper.selectPending();
        }
        return prescriptions.stream().map(this::toPrescriptionMap).toList();
    }

    /**
     * 获取处方详情（包含明细）
     */
    public Map<String, Object> getPrescriptionDetails(Long prescriptionId) {
        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            throw new BusinessException(404, "处方不存在");
        }
        List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(prescriptionId);

        Map<String, Object> result = new HashMap<>();
        result.put("prescription", toPrescriptionMap(prescription));
        result.put("details", details.stream().map(this::toDetailMap).toList());
        return result;
    }

    /**
     * 发药
     */
    @Transactional
    public Map<String, Object> dispense(Long registerId, Map<String, Object> dispensingInfo) {
        log.info("发药操作 | registerId={}", registerId);

        Long pharmacistId = dispensingInfo.get("pharmacistId") != null
            ? ((Number) dispensingInfo.get("pharmacistId")).longValue()
            : null;
        String pharmacistName = (String) dispensingInfo.get("pharmacistName");

        // 1. 查询该挂号的所有待发药处方
        List<Prescription> prescriptions = prescriptionMapper.selectByRegisterIdAndStatus(registerId, 0);
        if (prescriptions.isEmpty()) {
            throw new BusinessException(400, "没有待发药的处方");
        }

        // 2. 校验和扣减库存
        LocalDateTime now = LocalDateTime.now();
        int totalItemCount = 0;

        for (Prescription prescription : prescriptions) {
            List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(prescription.getId());
            for (PrescriptionDetail detail : details) {
                // 校验库存
                DrugInfo drug = drugInfoMapper.selectById(detail.getDrugId());
                if (drug == null) {
                    throw new BusinessException(400, "药品不存在: " + detail.getDrugName());
                }
                if (drug.getStockQuantity() < detail.getQuantity()) {
                    throw new BusinessException(400, "药品库存不足: " + drug.getName() + "，当前库存: " + drug.getStockQuantity());
                }

                // 扣减库存
                drugInfoMapper.decreaseStock(detail.getDrugId(), detail.getQuantity());

                // 记录交易
                PharmacyTransaction transaction = new PharmacyTransaction();
                transaction.setType("发放");
                transaction.setDrugId(detail.getDrugId());
                transaction.setDrugName(detail.getDrugName());
                transaction.setPrescriptionId(prescription.getId());
                transaction.setRegisterId(registerId);
                transaction.setQuantity(-detail.getQuantity());
                transaction.setUnitPrice(detail.getUnitPrice());
                transaction.setTotalAmount(detail.getTotalAmount().negate());
                transaction.setOperatorId(pharmacistId);
                transaction.setOperatorName(pharmacistName);
                transaction.setTransactionTime(now);
                pharmacyTransactionMapper.insert(transaction);

                totalItemCount++;
            }

            // 3. 更新处方状态为已发药
            prescription.setDispensationStatus(1);
            prescription.setDispensationTime(now);
            prescription.setPharmacist(pharmacistName);
            prescriptionMapper.update(prescription);
        }

        List<Long> followUpPlanIds = new ArrayList<>();
        List<Long> followUpFailedPrescriptionIds = new ArrayList<>();
        for (Prescription prescription : prescriptions) {
            if (prescription.getPatientId() == null) {
                followUpFailedPrescriptionIds.add(prescription.getId());
                log.warn("自动创建随访计划失败，处方缺少 patientId | registerId={}, prescriptionId={}", registerId, prescription.getId());
                continue;
            }
            try {
                Map<String, Object> followUpResult = aiPharmacyClient.createFollowUpPlan(
                    prescription.getPatientId(),
                    registerId,
                    prescription.getId()
                );
                if (followUpResult.get("planId") instanceof Number planId) {
                    followUpPlanIds.add(planId.longValue());
                }
            } catch (Exception e) {
                followUpFailedPrescriptionIds.add(prescription.getId());
                log.warn("自动创建随访计划失败 | registerId={}, prescriptionId={}", registerId, prescription.getId(), e);
            }
        }

        log.info("发药成功 | registerId={}, prescriptionCount={}, itemCount={}, followUpCreatedCount={}, followUpFailedCount={}",
            registerId, prescriptions.size(), totalItemCount, followUpPlanIds.size(), followUpFailedPrescriptionIds.size());

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionCount", prescriptions.size());
        result.put("itemCount", totalItemCount);
        result.put("dispensationTime", now);
        result.put("pharmacist", pharmacistName);
        result.put("followUpCreatedCount", followUpPlanIds.size());
        result.put("followUpFailedCount", followUpFailedPrescriptionIds.size());
        result.put("followUpPlanIds", followUpPlanIds);
        result.put("followUpFailedPrescriptionIds", followUpFailedPrescriptionIds);
        return result;
    }

    // ==================== 退药 ====================

    /**
     * 退药
     */
    @Transactional
    public void returnDrug(Long registerId, Map<String, Object> returnInfo) {
        log.info("退药操作 | registerId={}", registerId);

        Long pharmacistId = returnInfo.get("pharmacistId") != null
            ? ((Number) returnInfo.get("pharmacistId")).longValue()
            : null;
        String pharmacistName = (String) returnInfo.get("pharmacistName");
        String reason = (String) returnInfo.getOrDefault("reason", "患者申请退药");

        // 查询已发药的处方
        List<Prescription> prescriptions = prescriptionMapper.selectByRegisterIdAndStatus(registerId, 1);
        if (prescriptions.isEmpty()) {
            throw new BusinessException(400, "没有已发药的处方可以退药");
        }

        LocalDateTime now = LocalDateTime.now();

        for (Prescription prescription : prescriptions) {
            List<PrescriptionDetail> details = prescriptionDetailMapper.selectByPrescriptionId(prescription.getId());

            // 恢复库存并记录
            for (PrescriptionDetail detail : details) {
                // 增加药品库存
                drugInfoMapper.increaseStock(detail.getDrugId(), detail.getQuantity());

                PharmacyTransaction transaction = new PharmacyTransaction();
                transaction.setType("退回");
                transaction.setDrugId(detail.getDrugId());
                transaction.setDrugName(detail.getDrugName());
                transaction.setPrescriptionId(prescription.getId());
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

            // 更新处方状态为已退药
            prescription.setDispensationStatus(2);
            prescriptionMapper.update(prescription);
        }

        log.info("退药成功 | registerId={}, prescriptionCount={}", registerId, prescriptions.size());
    }

    // ==================== 交易记录 ====================

    /**
     * 查询交易记录
     */
    public List<Map<String, Object>> getTransactions(Long drugId, String type,
            LocalDateTime startDate, LocalDateTime endDate) {
        List<PharmacyTransaction> transactions;

        if (drugId != null) {
            transactions = pharmacyTransactionMapper.selectByDrugId(drugId);
        } else if (type != null) {
            transactions = pharmacyTransactionMapper.selectByType(type);
        } else if (startDate != null && endDate != null) {
            transactions = pharmacyTransactionMapper.selectByDateRange(startDate, endDate);
        } else {
            // 默认查询最近的记录
            transactions = pharmacyTransactionMapper.selectByDateRange(
                LocalDateTime.now().minusDays(30), LocalDateTime.now());
        }

        return transactions.stream().map(this::toTransactionMap).toList();
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
}
