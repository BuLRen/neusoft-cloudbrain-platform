package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.dto.FollowUpAdminView;
import com.xikang.registration.service.AdminFollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/follow-up-employees")
@RequiredArgsConstructor
public class AdminFollowUpController {

    private final AdminFollowUpService adminFollowUpService;

    @GetMapping
    public Result<Map<String, Object>> listFollowUpEmployees(
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return Result.success(adminFollowUpService.listFollowUpEmployees(departmentId, keyword, includeDisabled, page, size));
    }

    @GetMapping("/{id:\\d+}")
    public Result<FollowUpAdminView> getFollowUpEmployee(@PathVariable Long id) {
        return Result.success(adminFollowUpService.getFollowUpEmployee(id));
    }

    @PostMapping
    public Result<FollowUpAdminView> createFollowUpEmployee(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminFollowUpService.createFollowUpEmployee(request));
    }

    @PutMapping("/{id:\\d+}")
    public Result<FollowUpAdminView> updateFollowUpEmployee(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminFollowUpService.updateFollowUpEmployee(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public Result<FollowUpAdminView> updateFollowUpEmployeeStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("状态已更新", adminFollowUpService.updateFollowUpEmployeeStatus(id, request));
    }

    @PostMapping("/{id:\\d+}/account")
    public Result<Void> createAccount(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminFollowUpService.createAccount(id, request);
        return Result.success("账号创建成功", null);
    }

    @PutMapping("/{id:\\d+}/account/password")
    public Result<Void> resetAccountPassword(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminFollowUpService.resetAccountPassword(id, request);
        return Result.success("密码已重置", null);
    }

    @PatchMapping("/{id:\\d+}/account/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminFollowUpService.updateAccountStatus(id, request);
        return Result.success("账号状态已更新", null);
    }
}
