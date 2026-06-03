package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.service.PhysicianService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Physician Controller
 */
@RestController
@RequestMapping("/api/physician")
public class PhysicianController {

    private final PhysicianService physicianService;

    public PhysicianController(PhysicianService physicianService) {
        this.physicianService = physicianService;
    }

    @GetMapping("/patients")
    public Result<Map<String, Object>> getPatients(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        return Result.success(physicianService.getPatients(keyword, page, size));
    }

    @GetMapping("/patient-stats")
    public Result<Map<String, Object>> getPatientStats() {
        return Result.success(physicianService.getPatientStats());
    }

    @PostMapping("/medical-record")
    public Result<Map<String, Object>> createMedicalRecord(@RequestBody Map<String, Object> request) {
        return Result.success("病历创建成功", physicianService.createMedicalRecord(request));
    }

    @PutMapping("/medical-record/{id}")
    public Result<Void> updateMedicalRecord(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        physicianService.updateMedicalRecord(id, request);
        return Result.success("病历更新成功", null);
    }

    @GetMapping("/medical-record")
    public Result<Map<String, Object>> getMedicalRecord(@RequestParam Long registerId) {
        return Result.success(physicianService.getMedicalRecord(registerId));
    }

    @PostMapping("/medical-record/preliminary")
    public Result<Void> savePreliminaryDiagnosis(@RequestBody Map<String, Object> request) {
        physicianService.savePreliminaryDiagnosis(request);
        return Result.success("初步诊断已保存", null);
    }

    @GetMapping("/medical-technologies")
    public Result<List<Map<String, Object>>> getMedicalTechnologies(
        @RequestParam(required = false) String techType,
        @RequestParam(required = false) String keyword
    ) {
        return Result.success(physicianService.getMedicalTechnologies(techType, keyword));
    }

    @PostMapping("/check-request")
    public Result<Map<String, Object>> createCheckRequest(@RequestBody Map<String, Object> request) {
        return Result.success("检查申请提交成功", physicianService.createCheckRequest(request));
    }

    @PostMapping("/inspection-request")
    public Result<Map<String, Object>> createInspectionRequest(@RequestBody Map<String, Object> request) {
        return Result.success("检验申请提交成功", physicianService.createInspectionRequest(request));
    }

    @PostMapping("/disposal-request")
    public Result<Map<String, Object>> createDisposalRequest(@RequestBody Map<String, Object> request) {
        return Result.success("处置申请提交成功", physicianService.createDisposalRequest(request));
    }

    @GetMapping("/check-results")
    public Result<List<Map<String, Object>>> getCheckResults(@RequestParam Long registerId) {
        return Result.success(physicianService.getCheckResults(registerId));
    }

    @GetMapping("/inspection-results")
    public Result<List<Map<String, Object>>> getInspectionResults(@RequestParam Long registerId) {
        return Result.success(physicianService.getInspectionResults(registerId));
    }

    @GetMapping("/diseases")
    public Result<List<Map<String, Object>>> getDiseases(@RequestParam(required = false) String keyword) {
        return Result.success(physicianService.getDiseases(keyword));
    }

    @PostMapping("/diagnosis")
    public Result<Void> submitDiagnosis(@RequestBody Map<String, Object> request) {
        physicianService.submitDiagnosis(request);
        return Result.success("确诊提交成功", null);
    }

    @GetMapping("/diagnosis/{registrationId}")
    public Result<Object> getDiagnosisList(@PathVariable Long registrationId) {
        return Result.success(physicianService.getDiagnosisList(registrationId));
    }

    @GetMapping("/ai/exam-suggestions")
    public Result<List<Map<String, Object>>> getExamSuggestions(@RequestParam Long registerId) {
        return Result.success(physicianService.getExamSuggestions(registerId));
    }

    @GetMapping("/ai/diagnosis-suggestions")
    public Result<List<Map<String, Object>>> getDiagnosisSuggestions(@RequestParam Long registerId) {
        return Result.success(physicianService.getDiagnosisSuggestions(registerId));
    }

    @GetMapping("/drugs")
    public Result<List<Map<String, Object>>> getDrugs(@RequestParam(required = false) String keyword) {
        return Result.success(physicianService.getDrugs(keyword));
    }

    @GetMapping("/drugs/{id}")
    public Result<Map<String, Object>> getDrug(@PathVariable Long id) {
        return Result.success(physicianService.getDrug(id));
    }

    @GetMapping("/prescription/{registrationId}")
    public Result<Object> getPrescriptionList(@PathVariable Long registrationId) {
        return Result.success(physicianService.getPrescriptionList(registrationId));
    }

    @PostMapping("/prescription")
    public Result<Map<String, Object>> createPrescription(@RequestBody Map<String, Object> prescriptionRequest) {
        Map<String, Object> result = physicianService.createPrescription(prescriptionRequest);
        return Result.success("处方开立成功", result);
    }

    @DeleteMapping("/prescription/{id}")
    public Result<Void> deletePrescription(@PathVariable Long id) {
        physicianService.deletePrescription(id);
        return Result.success("删除成功", null);
    }

}
