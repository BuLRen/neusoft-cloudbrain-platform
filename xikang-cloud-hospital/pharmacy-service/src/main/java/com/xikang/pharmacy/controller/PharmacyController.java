package com.xikang.pharmacy.controller;

import com.xikang.common.result.Result;
import com.xikang.pharmacy.entity.DrugInfo;
import com.xikang.pharmacy.entity.DrugStock;
import com.xikang.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy Controller - 药房管理控制器
 */
@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    // ==================== 药品管理接口 ====================

    /**
     * 获取药品列表（支持组合查询：keyword + dosageForm + category）
     */
    @GetMapping("/drugs")
    public Result<List<DrugInfo>> getDrugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) String category) {
        // 优先走组合查询（P1-4.3）：带 category，或同时带 keyword + dosageForm
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasKeywordAndForm = (keyword != null && !keyword.isEmpty())
                && (dosageForm != null && !dosageForm.isEmpty());
        if (hasCategory || hasKeywordAndForm) {
            return Result.success(pharmacyService.getDrugsByConditions(keyword, dosageForm, category));
        }
        // 兼容旧调用（keyword 与 dosageForm 互斥）
        return Result.success(pharmacyService.getDrugs(keyword, dosageForm));
    }

    /**
     * P1-4.3 查询所有已用药品分类
     */
    @GetMapping("/drugs/categories")
    public Result<List<String>> getCategories() {
        return Result.success(pharmacyService.getCategories());
    }

    /**
     * 获取药品详情
     */
    @GetMapping("/drugs/{id}")
    public Result<DrugInfo> getDrug(@PathVariable Long id) {
        DrugInfo drug = pharmacyService.getDrug(id);
        return Result.success(drug);
    }

    /**
     * 添加药品
     */
    @PostMapping("/drugs")
    public Result<DrugInfo> addDrug(@RequestBody DrugInfo drug) {
        DrugInfo created = pharmacyService.addDrug(drug);
        return Result.success("药品添加成功", created);
    }

    /**
     * 更新药品
     */
    @PutMapping("/drugs/{id}")
    public Result<Void> updateDrug(@PathVariable Long id, @RequestBody DrugInfo drug) {
        pharmacyService.updateDrug(id, drug);
        return Result.success();
    }

    /**
     * 删除药品
     */
    @DeleteMapping("/drugs/{id}")
    public Result<Void> deleteDrug(@PathVariable Long id) {
        pharmacyService.deleteDrug(id);
        return Result.success();
    }

    /**
     * 获取低库存药品
     */
    @GetMapping("/drugs/low-stock")
    public Result<List<DrugInfo>> getLowStockDrugs() {
        List<DrugInfo> drugs = pharmacyService.getLowStockDrugs();
        return Result.success(drugs);
    }

    // ==================== 库存管理接口 ====================

    /**
     * 获取药品库存
     */
    @GetMapping("/inventory/{drugId}")
    public Result<List<DrugStock>> getInventory(@PathVariable Long drugId) {
        List<DrugStock> stocks = pharmacyService.getDrugStock(drugId);
        return Result.success(stocks);
    }

    /**
     * 药品入库
     */
    @PostMapping("/inventory/{drugId}/inbound")
    public Result<Void> drugInbound(
            @PathVariable Long drugId,
            @RequestBody Map<String, Object> inboundInfo) {
        pharmacyService.drugInbound(drugId, inboundInfo);
        return Result.success();
    }

    /**
     * 更新库存
     */
    @PutMapping("/inventory/{drugId}")
    public Result<Void> updateStock(
            @PathVariable Long drugId,
            @RequestBody Map<String, Object> stock) {
        pharmacyService.updateStock(drugId, stock);
        return Result.success();
    }

    /**
     * P1-4.2 近效期批次查询
     */
    @GetMapping("/inventory/expiring")
    public Result<List<Map<String, Object>>> getExpiringStock(
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(pharmacyService.getExpiringStock(days));
    }

    // ==================== AI 联动接口 ====================

    /**
     * P1-6.1 生成用药指导
     */
    @PostMapping("/drugs/{drugId}/guide")
    public Result<Map<String, Object>> generateMedicationGuide(@PathVariable Long drugId) {
        return Result.success(pharmacyService.generateMedicationGuide(drugId));
    }

    /**
     * P1-6.2 查询患者随访计划
     */
    @GetMapping("/followup/patient/{patientId}")
    public Result<List<Map<String, Object>>> getPatientFollowUpPlans(@PathVariable Long patientId) {
        return Result.success(pharmacyService.getPatientFollowUpPlans(patientId));
    }

    /**
     * P2-6.3 重试创建随访计划
     */
    @PostMapping("/followup/retry/{prescriptionId}")
    public Result<Map<String, Object>> retryFollowUp(@PathVariable Long prescriptionId) {
        return Result.success(pharmacyService.retryFollowUp(prescriptionId));
    }

    /**
     * P2-6.4 录入随访反馈
     */
    @PostMapping("/followup/{planId}/feedback")
    public Result<Void> submitFollowUpFeedback(
            @PathVariable Long planId,
            @RequestBody Map<String, Object> feedback) {
        pharmacyService.submitFollowUpFeedback(planId, feedback);
        return Result.success();
    }

    /**
     * P2-4.6 按挂号查询发药单
     */
    @GetMapping("/dispensing/{registerId}")
    public Result<List<Map<String, Object>>> getDispensingByRegister(@PathVariable Long registerId) {
        return Result.success(pharmacyService.getDispensingByRegister(registerId));
    }

    // ==================== 批量入库接口 ====================

    /**
     * 批量入库：单事务，任一行校验失败全回滚
     */
    @PostMapping("/inventory/batch-inbound")
    public Result<Map<String, Object>> batchInbound(
            @RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        Map<String, Object> result = pharmacyService.batchInbound(items);
        return Result.success("批量入库成功", result);
    }

    // ==================== 报损接口 ====================

    /**
     * 药品报损
     */
    @PostMapping("/inventory/{drugId}/loss")
    public Result<Void> reportLoss(
            @PathVariable Long drugId,
            @RequestBody Map<String, Object> lossInfo) {
        pharmacyService.reportLoss(drugId, lossInfo);
        return Result.success();
    }

    // ==================== 批次冻结/解冻接口 ====================

    /**
     * 冻结批次（status → 0）
     */
    @PutMapping("/inventory/batch/{batchId}/freeze")
    public Result<Void> freezeBatch(@PathVariable Long batchId) {
        pharmacyService.freezeBatch(batchId);
        return Result.success();
    }

    /**
     * 解冻批次（status → 1）
     */
    @PutMapping("/inventory/batch/{batchId}/unfreeze")
    public Result<Void> unfreezeBatch(@PathVariable Long batchId) {
        pharmacyService.unfreezeBatch(batchId);
        return Result.success();
    }

    // ==================== 发药接口 ====================

    /**
     * 获取待发药患者列表
     */
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> getPendingDispensing(
            @RequestParam(required = false) Long registrationId) {
        List<Map<String, Object>> pending = pharmacyService.getPendingDispensing(registrationId);
        return Result.success(pending);
    }

    /**
     * 历史处方组合查询（处方追溯）：按 patientId / 状态 / 日期范围。
     */
    @GetMapping("/prescriptions")
    public Result<List<Map<String, Object>>> queryPrescriptions(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return Result.success(pharmacyService.queryPrescriptions(patientId, status, startDate, endDate));
    }

    /**
     * 获取处方详情
     */
    @GetMapping("/prescription/{prescriptionId}")
    public Result<Map<String, Object>> getPrescriptionDetails(@PathVariable Long prescriptionId) {
        Map<String, Object> details = pharmacyService.getPrescriptionDetails(prescriptionId);
        return Result.success(details);
    }

    /**
     * 确认发药
     */
    @PutMapping("/dispense/{registerId}")
    public Result<Map<String, Object>> dispense(
            @PathVariable Long registerId,
            @RequestBody Map<String, Object> dispensingInfo) {
        Map<String, Object> result = pharmacyService.dispense(registerId, dispensingInfo);
        return Result.success("发药成功", result);
    }

    /**
     * P1-4.1 发药前审核
     */
    @PostMapping("/dispense/{registerId}/review")
    public Result<Map<String, Object>> reviewDispense(@PathVariable Long registerId) {
        return Result.success(pharmacyService.reviewDispense(registerId));
    }

    // ==================== 退药接口 ====================

    /**
     * 退药
     */
    @PutMapping("/return/{registerId}")
    public Result<Void> returnDrug(
            @PathVariable Long registerId,
            @RequestBody Map<String, Object> returnInfo) {
        pharmacyService.returnDrug(registerId, returnInfo);
        return Result.success();
    }

    // ==================== 交易记录接口 ====================

    /**
     * 获取交易记录
     */
    @GetMapping("/transactions")
    public Result<List<Map<String, Object>>> getTransactions(
            @RequestParam(required = false) Long drugId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Map<String, Object>> transactions = pharmacyService.getTransactions(drugId, type, startDate, endDate);
        return Result.success(transactions);
    }

    /**
     * 药房工作量与药品消耗统计（按时间范围）。
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int topLimit) {
        return Result.success(pharmacyService.getStatistics(startDate, endDate, topLimit));
    }
}
