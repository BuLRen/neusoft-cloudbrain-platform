package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.dto.MedtechAdminView;
import com.xikang.registration.service.AdminMedtechService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/medtech-employees")
@RequiredArgsConstructor
public class AdminMedtechController {

    private final AdminMedtechService adminMedtechService;

    @GetMapping
    public Result<Map<String, Object>> listMedtechEmployees(
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminMedtechService.listMedtechEmployees(departmentId, keyword, includeDisabled, page, size));
    }

    @GetMapping("/{id}")
    public Result<MedtechAdminView> getMedtechEmployee(@PathVariable Long id) {
        return Result.success(adminMedtechService.getMedtechEmployee(id));
    }

    @PostMapping
    public Result<MedtechAdminView> createMedtechEmployee(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminMedtechService.createMedtechEmployee(request));
    }

    @PutMapping("/{id}")
    public Result<MedtechAdminView> updateMedtechEmployee(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminMedtechService.updateMedtechEmployee(id, request));
    }

    @PatchMapping("/{id}/status")
    public Result<MedtechAdminView> updateMedtechEmployeeStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("状态已更新", adminMedtechService.updateMedtechEmployeeStatus(id, request));
    }

    @PostMapping("/{id}/account")
    public Result<Void> createAccount(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.createAccount(id, request);
        return Result.success("账号创建成功", null);
    }

    @PutMapping("/{id}/account/password")
    public Result<Void> resetAccountPassword(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.resetAccountPassword(id, request);
        return Result.success("密码已重置", null);
    }

    @PatchMapping("/{id}/account/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.updateAccountStatus(id, request);
        return Result.success("账号状态已更新", null);
    }
}
