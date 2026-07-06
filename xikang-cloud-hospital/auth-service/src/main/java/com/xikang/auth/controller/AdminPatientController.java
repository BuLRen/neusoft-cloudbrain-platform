package com.xikang.auth.controller;

import com.xikang.auth.dto.PatientAdminView;
import com.xikang.auth.service.PatientAdminService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 患者档案管理员 API（供 registration-service Feign 调用）
 */
@RestController
@RequestMapping("/api/patient/admin/patients")
@RequiredArgsConstructor
public class AdminPatientController {

    private final PatientAdminService patientAdminService;

    @GetMapping
    public Result<Map<String, Object>> listPatients(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(patientAdminService.listPatients(keyword, includeDisabled, page, size));
    }

    @GetMapping("/{id:\\d+}")
    public Result<PatientAdminView> getPatient(@PathVariable Integer id) {
        return Result.success(patientAdminService.getPatient(id));
    }

    @PostMapping
    public Result<PatientAdminView> createPatient(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", patientAdminService.createPatient(request));
    }

    @PutMapping("/{id:\\d+}")
    public Result<PatientAdminView> updatePatient(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", patientAdminService.updatePatient(id, request));
    }

    @PostMapping("/{id:\\d+}/status")
    public Result<PatientAdminView> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        Object delmarkObj = request.get("delmark");
        Integer delmark = delmarkObj instanceof Number number ? number.intValue() : null;
        return Result.success("状态已更新", patientAdminService.updateStatus(id, delmark));
    }
}
