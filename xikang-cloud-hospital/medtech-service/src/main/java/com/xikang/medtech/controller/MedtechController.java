package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.entity.MedicalTechnology;
import com.xikang.medtech.service.MedtechService;
import com.xikang.medtech.service.ResultFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MedTech Controller - 医技检查/检验/处置控制器
 */
@RestController
@RequestMapping("/api/medtech")
@RequiredArgsConstructor
public class MedtechController {

    private final MedtechService medtechService;
    private final ResultFormService resultFormService;

    // ==================== 检查相关接口 ====================

    /**
     * 获取待检查患者列表
     */
    @GetMapping("/check/applications")
    public Result<List<Map<String, Object>>> getCheckApplications(
            @RequestParam(required = false) Long registrationId,
            @RequestParam(required = false) String checkState) {
        List<Map<String, Object>> applications = medtechService.getCheckApplications(registrationId, checkState);
        return Result.success(applications);
    }

    /**
     * 开始检查
     */
    @PutMapping("/check/start/{id}")
    public Result<Void> startCheck(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> operatorInfo) {
        medtechService.startCheck(id, operatorInfo);
        return Result.success();
    }

    /**
     * 提交检查结果
     */
    @PutMapping("/check/result/{id}")
    public Result<Map<String, Object>> submitCheckResult(
            @PathVariable Long id,
            @RequestBody Map<String, Object> result) {
        Map<String, Object> response = medtechService.submitCheckResult(id, result);
        return Result.success("检查结果提交成功", response);
    }

    /**
     * 获取检查报告
     */
    @GetMapping("/check/report/{id}")
    public Result<Map<String, Object>> getCheckReport(@PathVariable Long id) {
        Map<String, Object> report = medtechService.getCheckReport(id);
        return Result.success(report);
    }

    /**
     * 解析检查结果录入表单 schema
     */
    @GetMapping("/check/result-form/resolve")
    public Result<Map<String, Object>> resolveCheckResultForm(
            @RequestParam(required = false) Long checkRequestId,
            @RequestParam(required = false) Long medicalTechnologyId) {
        if (checkRequestId != null) {
            return Result.success(resultFormService.resolveByCheckRequestId(checkRequestId));
        }
        if (medicalTechnologyId != null) {
            return Result.success(resultFormService.resolveByMedicalTechnologyId(medicalTechnologyId));
        }
        return Result.error("请提供 checkRequestId 或 medicalTechnologyId");
    }

    /**
     * 获取检查结果表单分类列表
     */
    @GetMapping("/result-form/categories")
    public Result<List<Map<String, Object>>> listResultFormCategories() {
        return Result.success(resultFormService.listCategories());
    }

    /**
     * 获取分类通用字段
     */
    @GetMapping("/result-form/categories/{code}/fields")
    public Result<List<Map<String, Object>>> listCategoryResultFormFields(@PathVariable String code) {
        return Result.success(resultFormService.listCategoryFields(code));
    }

    /**
     * 保存分类通用字段
     */
    @PutMapping("/result-form/categories/{code}/fields")
    public Result<Void> saveCategoryResultFormFields(
            @PathVariable String code,
            @RequestBody List<Map<String, Object>> fields) {
        resultFormService.saveCategoryFields(code, fields);
        return Result.success("分类表单已保存", null);
    }

    /**
     * 获取检查项目扩展字段上下文
     */
    @GetMapping("/result-form/tech/{techId}/extensions")
    public Result<Map<String, Object>> getTechResultFormExtensions(@PathVariable Long techId) {
        return Result.success(resultFormService.getTechExtensionContext(techId));
    }

    /**
     * 保存检查项目扩展字段
     */
    @PutMapping("/result-form/tech/{techId}/extensions")
    public Result<Void> saveTechResultFormExtensions(
            @PathVariable Long techId,
            @RequestBody List<Map<String, Object>> fields) {
        resultFormService.saveTechExtensions(techId, fields);
        return Result.success("项目扩展字段已保存", null);
    }

    // ==================== 检验相关接口 ====================

    /**
     * 获取待检验患者列表
     */
    @GetMapping("/inspection/applications")
    public Result<List<Map<String, Object>>> getInspectionApplications(
            @RequestParam(required = false) Long registrationId,
            @RequestParam(required = false) Integer status) {
        List<Map<String, Object>> applications = medtechService.getInspectionApplications(registrationId, status);
        return Result.success(applications);
    }

    /**
     * 开始检验
     */
    @PutMapping("/inspection/start/{id}")
    public Result<Void> startInspection(@PathVariable Long id) {
        medtechService.startInspection(id);
        return Result.success();
    }

    /**
     * 记录采样
     */
    @PutMapping("/inspection/specimen/{id}")
    public Result<Void> recordSpecimen(
            @PathVariable Long id,
            @RequestBody Map<String, Object> specimenInfo) {
        medtechService.recordSpecimen(id, specimenInfo);
        return Result.success();
    }

    /**
     * 提交检验结果
     */
    @PutMapping("/inspection/result/{id}")
    public Result<Map<String, Object>> submitInspectionResult(
            @PathVariable Long id,
            @RequestBody Map<String, Object> result) {
        Map<String, Object> response = medtechService.submitInspectionResult(id, result);
        return Result.success("检验结果提交成功", response);
    }

    // ==================== 处置相关接口 ====================

    /**
     * 获取待处置患者列表
     */
    @GetMapping("/disposal/applications")
    public Result<List<Map<String, Object>>> getDisposalApplications(
            @RequestParam(required = false) Long registrationId,
            @RequestParam(required = false) Integer status) {
        List<Map<String, Object>> applications = medtechService.getDisposalApplications(registrationId, status);
        return Result.success(applications);
    }

    /**
     * 开始处置
     */
    @PutMapping("/disposal/start/{id}")
    public Result<Void> startDisposal(@PathVariable Long id) {
        medtechService.startDisposal(id);
        return Result.success();
    }

    /**
     * 提交处置结果
     */
    @PutMapping("/disposal/result/{id}")
    public Result<Void> submitDisposalResult(
            @PathVariable Long id,
            @RequestBody Map<String, Object> result) {
        medtechService.submitDisposalResult(id, result);
        return Result.success();
    }

    // ==================== 基础数据接口 ====================

    /**
     * 科室下拉（维护医技项目执行科室）
     */
    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> listDepartments() {
        return Result.success(medtechService.listDepartments());
    }

    /**
     * 获取医技项目列表（医生开单可选的全部项目：不传 type 则返回全部）
     */
    @GetMapping("/medical-technologies")
    public Result<Map<String, Object>> pageMedicalTechnologies(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(medtechService.pageMedicalTechnologies(type, keyword, page, size));
    }

    /**
     * 获取医技项目详情
     */
    @GetMapping("/medical-technologies/{id}")
    public Result<MedicalTechnology> getMedicalTechnology(@PathVariable Long id) {
        MedicalTechnology technology = medtechService.getMedicalTechnology(id);
        return Result.success(technology);
    }

    /**
     * 新增医技项目
     */
    @PostMapping("/medical-technologies")
    public Result<MedicalTechnology> createMedicalTechnology(@RequestBody MedicalTechnology request) {
        MedicalTechnology created = medtechService.createMedicalTechnology(request);
        return Result.success("医技项目创建成功", created);
    }

    /**
     * 更新医技项目
     */
    @PutMapping("/medical-technologies/{id}")
    public Result<MedicalTechnology> updateMedicalTechnology(
            @PathVariable Long id,
            @RequestBody MedicalTechnology request) {
        MedicalTechnology updated = medtechService.updateMedicalTechnology(id, request);
        return Result.success("医技项目更新成功", updated);
    }

    /**
     * 删除医技项目
     */
    @DeleteMapping("/medical-technologies/{id}")
    public Result<Void> deleteMedicalTechnology(@PathVariable Long id) {
        medtechService.deleteMedicalTechnology(id);
        return Result.success("医技项目已删除", null);
    }
}
