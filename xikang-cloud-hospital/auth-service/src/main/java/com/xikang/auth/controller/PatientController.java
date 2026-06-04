package com.xikang.auth.controller;

import com.xikang.auth.entity.Patient;
import com.xikang.auth.service.PatientService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 获取指定患者信息
     */
    @GetMapping("/{patientId}")
    public Result<Patient> getPatient(@PathVariable Integer patientId) {
        Patient patient = patientService.getPatientById(patientId);
        return Result.success(patient);
    }

    /**
     * 创建患者档案
     */
    @PostMapping
    public Result<Patient> createPatient(@RequestBody Patient patient) {
        Patient created = patientService.createPatient(patient);
        return Result.success(created);
    }

    /**
     * 更新患者档案
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
}