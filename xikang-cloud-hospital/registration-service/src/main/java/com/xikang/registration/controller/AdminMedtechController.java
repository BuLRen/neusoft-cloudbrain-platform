package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.dto.MedtechAdminView;
import com.xikang.registration.dto.PersonnelImportResult;
import com.xikang.registration.service.AdminMedtechService;
import com.xikang.registration.service.AdminPersonnelExcelService;
import com.xikang.registration.util.PersonnelExcelHttpSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/medtech-employees")
@RequiredArgsConstructor
public class AdminMedtechController {

    private final AdminMedtechService adminMedtechService;
    private final AdminPersonnelExcelService personnelExcelService;

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

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMedtechEmployees(
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean includeDisabled
    ) {
        byte[] body = personnelExcelService.exportMedtechEmployees(departmentId, keyword, includeDisabled);
        return PersonnelExcelHttpSupport.attachment(
            body,
            personnelExcelService.medtechExportAsciiFilename(),
            personnelExcelService.medtechExportFilename()
        );
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadMedtechTemplate() {
        byte[] body = personnelExcelService.medtechTemplate();
        return PersonnelExcelHttpSupport.attachment(
            body,
            personnelExcelService.medtechTemplateAsciiFilename(),
            personnelExcelService.medtechTemplateFilename()
        );
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PersonnelImportResult> importMedtechEmployees(@RequestParam("file") MultipartFile file) {
        return Result.success("导入完成", personnelExcelService.importMedtechEmployees(file));
    }

    @GetMapping("/{id:\\d+}")
    public Result<MedtechAdminView> getMedtechEmployee(@PathVariable Long id) {
        return Result.success(adminMedtechService.getMedtechEmployee(id));
    }

    @PostMapping
    public Result<MedtechAdminView> createMedtechEmployee(@RequestBody Map<String, Object> request) {
        return Result.success("创建成功", adminMedtechService.createMedtechEmployee(request));
    }

    @PutMapping("/{id:\\d+}")
    public Result<MedtechAdminView> updateMedtechEmployee(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("更新成功", adminMedtechService.updateMedtechEmployee(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public Result<MedtechAdminView> updateMedtechEmployeeStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return Result.success("状态已更新", adminMedtechService.updateMedtechEmployeeStatus(id, request));
    }

    @PostMapping("/{id:\\d+}/account")
    public Result<Void> createAccount(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.createAccount(id, request);
        return Result.success("账号创建成功", null);
    }

    @PutMapping("/{id:\\d+}/account/password")
    public Result<Void> resetAccountPassword(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.resetAccountPassword(id, request);
        return Result.success("密码已重置", null);
    }

    @PatchMapping("/{id:\\d+}/account/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        adminMedtechService.updateAccountStatus(id, request);
        return Result.success("账号状态已更新", null);
    }
}
