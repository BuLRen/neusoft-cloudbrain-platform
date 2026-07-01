package com.xikang.pharmacy.controller;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.pharmacy.entity.DrugInfo;
import com.xikang.pharmacy.entity.DrugStock;
import com.xikang.pharmacy.entity.MedicationGuide;
import com.xikang.pharmacy.service.PharmacyPdfRenderer;
import com.xikang.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final PharmacyPdfRenderer pdfRenderer;

    // ==================== 药品管理接口 ====================

    /**
     * 获取药品列表（支持组合查询：keyword + dosageForm + category）。
     * <p>双模式（照搬 physician-service）：传了 page/pageSize 走分页返回 {list,total,page,pageSize}；
     * 没传走全量（Mapper 已加 LIMIT 200 兜底防 OOM）。</p>
     */
    @GetMapping("/drugs")
    public Result<?> getDrugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        if (page != null || pageSize != null) {
            return Result.success(pharmacyService.getDrugsPage(keyword, dosageForm, category, page, pageSize));
        }
        // 兼容旧调用（无分页）—— Mapper 内 LIMIT 200 兜底
        boolean hasCategory = category != null && !category.isEmpty();
        boolean hasKeywordAndForm = (keyword != null && !keyword.isEmpty())
                && (dosageForm != null && !dosageForm.isEmpty());
        if (hasCategory || hasKeywordAndForm) {
            return Result.success(pharmacyService.getDrugsByConditions(keyword, dosageForm, category));
        }
        return Result.success(pharmacyService.getDrugs(keyword, dosageForm));
    }

    /**
     * 查询所有已用药品分类（drug_type：西药/中成药/生物制品）
     */
    @GetMapping("/drugs/categories")
    public Result<List<String>> getCategories() {
        return Result.success(pharmacyService.getCategories());
    }

    /**
     * 查询所有已用药品剂型（drug_dosage），供前端动态下拉。
     * 真实表 drug_dosage 取值有 30+ 种（片剂/注射剂/胶囊剂/丸剂(大蜜丸)/...），
     * 前端写死枚举无法覆盖，改为后端动态返回。
     */
    @GetMapping("/drugs/dosage-forms")
    public Result<List<String>> getDosageForms() {
        return Result.success(pharmacyService.getDosageForms());
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
     * 获取低库存药品。双模式：传 page/pageSize 走分页，否则全量（LIMIT 200 兜底）。
     */
    @GetMapping("/drugs/low-stock")
    public Result<?> getLowStockDrugs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        if (page != null || pageSize != null) {
            return Result.success(pharmacyService.getLowStockDrugsPage(page, pageSize));
        }
        return Result.success(pharmacyService.getLowStockDrugs());
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
     * 近效期批次查询。双模式：传 page/pageSize 走分页，否则全量（LIMIT 200 兜底）。
     */
    @GetMapping("/inventory/expiring")
    public Result<?> getExpiringStock(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        if (page != null || pageSize != null) {
            return Result.success(pharmacyService.getExpiringStockPage(days, page, pageSize));
        }
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
     * 历史处方组合查询（处方追溯）：按 patientId / 状态 / 日期范围 / 挂号号。
     */
    @GetMapping("/prescriptions")
    public Result<List<Map<String, Object>>> queryPrescriptions(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long registerId) {
        return Result.success(pharmacyService.queryPrescriptions(patientId, status, startDate, endDate, registerId));
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

    // ==================== 用药指导单接口 ====================

    /**
     * 查询最新一条用药指导单状态（前端按钮探测用）。
     */
    @GetMapping("/medication-guide/{registerId}")
    public Result<MedicationGuide> getMedicationGuide(@PathVariable Long registerId) {
        return Result.success(pharmacyService.getLatestMedicationGuide(registerId));
    }

    /**
     * 手动重试生成用药指导单数据。
     */
    @PostMapping("/medication-guide/{registerId}/retry")
    public Result<MedicationGuide> retryMedicationGuide(@PathVariable Long registerId) {
        return Result.success("已重新生成", pharmacyService.retryMedicationGuide(registerId));
    }

    /**
     * 下载用药指导单 PDF（实时渲染，不落盘）。
     * <p>用户点击按钮 → 后端取最新一条 medication_guide → JSON 转 PDF → 流式返回。
     * 浏览器识别 Content-Disposition: attachment 自动弹下载。</p>
     */
    @GetMapping("/medication-guide/{registerId}/pdf")
    public ResponseEntity<byte[]> downloadMedicationGuidePdf(@PathVariable Long registerId) {
        MedicationGuide guide = pharmacyService.getLatestMedicationGuide(registerId);
        if (guide == null || !"success".equals(guide.getStatus())) {
            throw new BusinessException(404, "用药指导单尚未就绪，请稍后重试或点击重新生成");
        }
        byte[] pdf = pdfRenderer.render(guide);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
            "medication-guide-" + registerId + ".pdf");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
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

    // ==================== 患者端：处方查看（含出账） ====================

    /**
     * 患者端「我的处方」：返回该患者所有处方（按挂号聚合），
     * 并对每个挂号幂等生成药品费 expense_record 行。
     * 该端点允许 patient 角色访问（gateway 路由 + JWT 角色校验）。
     */
    @GetMapping("/patient/{patientId}/prescriptions")
    public Result<List<Map<String, Object>>> getPatientPrescriptions(@PathVariable Long patientId) {
        return Result.success(pharmacyService.getPatientPrescriptions(patientId));
    }
}
