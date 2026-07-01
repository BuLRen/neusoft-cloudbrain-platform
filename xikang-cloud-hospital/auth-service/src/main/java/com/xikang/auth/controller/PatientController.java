package com.xikang.auth.controller;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.entity.PatientBalanceTransaction;
import com.xikang.auth.service.PatientService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Patient Controller - 患者管理控制器
 */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /**
     * 获取当前用户管理的患者列表（本人+家人）
     */
    @GetMapping("/list")
    public Result<List<Patient>> getPatientList(@RequestParam Long userId) {
        List<Patient> patients = patientService.getPatientsByUserId(userId);
        return Result.success(patients);
    }

    @GetMapping("/search")
    public Result<List<Patient>> searchPatients(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(patientService.searchPatients(keyword, limit));
    }

    /**
     * 获取指定患者信息
     */
    @GetMapping("/{patientId}")
    public Result<Patient> getPatient(@PathVariable Integer patientId) {
        Patient patient = patientService.getPatientById(patientId);
        return Result.success(patient);
    }

    @GetMapping("/{patientId}/balance")
    public Result<Map<String, Object>> getBalance(@PathVariable Integer patientId) {
        return Result.success(Map.of(
                "patientId", patientId,
                "accountBalance", patientService.getBalance(patientId)
        ));
    }

    @PostMapping("/{patientId}/balance/recharge")
    public Result<Map<String, Object>> rechargeBalance(
            @PathVariable Integer patientId,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(String.valueOf(request.get("amount")));
        return Result.success(patientService.rechargeBalance(
                patientId,
                amount,
                strOrNull(request.get("businessType")),
                longOrNull(request.get("businessId")),
                longOrNull(request.get("operatorId")),
                strOrNull(request.get("operatorName")),
                strOrNull(request.get("remark"))
        ));
    }

    @PostMapping("/{patientId}/balance/refund")
    public Result<Map<String, Object>> refundBalance(
            @PathVariable Integer patientId,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(String.valueOf(request.get("amount")));
        return Result.success(patientService.refundBalance(
                patientId,
                amount,
                strOrNull(request.get("businessType")),
                longOrNull(request.get("businessId")),
                longOrNull(request.get("operatorId")),
                strOrNull(request.get("operatorName")),
                strOrNull(request.get("remark"))
        ));
    }

    @PostMapping("/{patientId}/balance/deduct")
    public Result<Map<String, Object>> deductBalance(
            @PathVariable Integer patientId,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(String.valueOf(request.get("amount")));
        return Result.success(patientService.deductBalance(
                patientId,
                amount,
                strOrNull(request.get("businessType")),
                longOrNull(request.get("businessId")),
                longOrNull(request.get("operatorId")),
                strOrNull(request.get("operatorName")),
                strOrNull(request.get("remark"))
        ));
    }

    /**
     * 查询患者钱包流水（type 可选：RECHARGE / DEDUCT / REFUND）
     */
    @GetMapping("/{patientId}/balance/transactions")
    public Result<List<PatientBalanceTransaction>> listBalanceTransactions(
            @PathVariable Integer patientId,
            @RequestParam(value = "type", required = false) String transactionType) {
        return Result.success(patientService.listBalanceTransactions(patientId, transactionType));
    }

    /**
     * 添加家人（创建患者档案并建立关联）
     */
    @PostMapping("/family")
    public Result<Void> addFamilyMember(
            @RequestParam Long userId,
            @RequestBody Patient patient,
            @RequestParam String relation
    ) {
        patientService.createPatientWithRelation(userId, patient, relation);
        return Result.success();
    }

    /**
     * 更新患者档案（支持部分字段更新）
     */
    @PutMapping("/{patientId}")
    public Result<Void> updatePatient(@PathVariable Integer patientId, @RequestBody Patient patient) {
        patient.setId(patientId);
        patientService.updatePatient(patient);
        return Result.success();
    }

    /**
     * 删除患者档案
     */
    @DeleteMapping("/{patientId}")
    public Result<Void> deletePatient(@PathVariable Integer patientId) {
        patientService.deletePatient(patientId);
        return Result.success();
    }

    private static String strOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        return Long.parseLong(text);
    }
}
