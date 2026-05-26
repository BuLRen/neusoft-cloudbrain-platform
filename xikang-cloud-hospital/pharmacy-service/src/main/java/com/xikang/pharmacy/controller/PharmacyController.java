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
     * 获取药品列表
     */
    @GetMapping("/drugs")
    public Result<List<DrugInfo>> getDrugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dosageForm) {
        List<DrugInfo> drugs = pharmacyService.getDrugs(keyword, dosageForm);
        return Result.success(drugs);
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
}
