package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.AdminPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/patients")
@RequiredArgsConstructor
public class AdminPatientController {

    private final AdminPatientService adminPatientService;

    @GetMapping
    public Result<Map<String, Object>> listPatients(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminPatientService.listPatients(keyword, includeDisabled, page, size));
    }

    @GetMapping("/{id:\\d+}")
    public Result<Map<String, Object>> getPatient(@PathVariable Integer id) {
        return Result.success(adminPatientService.getPatient(id));
    }

    @PostMapping
    public Result<Map<String, Object>> createPatient(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminPatientService.createPatient(request));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Map<String, Object>> updatePatient(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminPatientService.updatePatient(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        Object delmarkObj = request.get("delmark");
        Integer delmark = delmarkObj instanceof Number number ? number.intValue() : null;
        return Result.success("状态已更新", adminPatientService.updateStatus(id, delmark));
    }
}
