package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.dto.PhysicianAdminView;
import com.xikang.registration.service.AdminPhysicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/physicians")
@RequiredArgsConstructor
public class AdminPhysicianController {

    private final AdminPhysicianService adminPhysicianService;

    @GetMapping
    public Result<Map<String, Object>> listPhysicians(
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminPhysicianService.listPhysicians(departmentId, keyword, includeDisabled, page, size));
    }

    @GetMapping("/{id}")
    public Result<PhysicianAdminView> getPhysician(@PathVariable Long id) {
        return Result.success(adminPhysicianService.getPhysician(id));
    }

    @PostMapping
    public Result<PhysicianAdminView> createPhysician(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminPhysicianService.createPhysician(request));
    }

    @PutMapping("/{id}")
    public Result<PhysicianAdminView> updatePhysician(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminPhysicianService.updatePhysician(id, request));
    }

    @PatchMapping("/{id}/status")
    public Result<PhysicianAdminView> updatePhysicianStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("状态已更新", adminPhysicianService.updatePhysicianStatus(id, request));
    }

    @PostMapping("/{id}/account")
    public Result<Void> createAccount(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.createAccount(id, request);
        return Result.success("账号创建成功", null);
    }

    @PutMapping("/{id}/account/password")
    public Result<Void> resetAccountPassword(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.resetAccountPassword(id, request);
        return Result.success("密码已重置", null);
    }

    @PatchMapping("/{id}/account/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.updateAccountStatus(id, request);
        return Result.success("账号状态已更新", null);
    }
}
