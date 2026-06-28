package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.dto.MedtechAdminView;
import com.xikang.registration.dto.PersonnelImportResult;
import com.xikang.registration.dto.PhysicianAdminView;
import com.xikang.registration.service.AdminMedtechService;
import com.xikang.registration.service.AdminPersonnelExcelService;
import com.xikang.registration.service.AdminPhysicianService;
import com.xikang.registration.util.PersonnelExcelHttpSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/physicians")
@RequiredArgsConstructor
public class AdminPhysicianController {

    private final AdminPhysicianService adminPhysicianService;
    private final AdminPersonnelExcelService personnelExcelService;

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

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportPhysicians(
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled
    ) {
        byte[] body = personnelExcelService.exportPhysicians(departmentId, keyword, includeDisabled);
        return PersonnelExcelHttpSupport.attachment(
            body,
            personnelExcelService.physicianExportAsciiFilename(),
            personnelExcelService.physicianExportFilename()
        );
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadPhysicianTemplate() {
        byte[] body = personnelExcelService.physicianTemplate();
        return PersonnelExcelHttpSupport.attachment(
            body,
            personnelExcelService.physicianTemplateAsciiFilename(),
            personnelExcelService.physicianTemplateFilename()
        );
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PersonnelImportResult> importPhysicians(@RequestParam("file") MultipartFile file) {
        return Result.success("导入完成", personnelExcelService.importPhysicians(file));
    }

    @GetMapping("/{id:\\d+}")
    public Result<PhysicianAdminView> getPhysician(@PathVariable Long id) {
        return Result.success(adminPhysicianService.getPhysician(id));
    }

    @PostMapping
    public Result<PhysicianAdminView> createPhysician(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminPhysicianService.createPhysician(request));
    }

    @PutMapping("/{id:\\d+}")
    public Result<PhysicianAdminView> updatePhysician(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminPhysicianService.updatePhysician(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public Result<PhysicianAdminView> updatePhysicianStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("状态已更新", adminPhysicianService.updatePhysicianStatus(id, request));
    }

    @PostMapping("/{id:\\d+}/account")
    public Result<Void> createAccount(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.createAccount(id, request);
        return Result.success("账号创建成功", null);
    }

    @PutMapping("/{id:\\d+}/account/password")
    public Result<Void> resetAccountPassword(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.resetAccountPassword(id, request);
        return Result.success("密码已重置", null);
    }

    @PatchMapping("/{id:\\d+}/account/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminPhysicianService.updateAccountStatus(id, request);
        return Result.success("账号状态已更新", null);
    }
}
