package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.dto.MedtechAdminView;
import com.xikang.registration.dto.PersonnelImportResult;
import com.xikang.registration.dto.PersonnelImportRowResult;
import com.xikang.registration.dto.PhysicianAdminView;
import com.xikang.registration.entity.Department;
import com.xikang.registration.mapper.AdminMedtechMapper;
import com.xikang.registration.mapper.DepartmentMapper;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.RegistLevelMapper;
import com.xikang.registration.util.PersonnelExcelReader;
import com.xikang.registration.util.PersonnelExcelWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPersonnelExcelService {

    private static final List<String> PHYSICIAN_IMPORT_HEADERS = List.of("姓名", "科室", "挂号级别");
    private static final List<String> MEDTECH_IMPORT_HEADERS = List.of("姓名", "医技科室");

    private final EmployeeMapper employeeMapper;
    private final AdminMedtechMapper adminMedtechMapper;
    private final DepartmentMapper departmentMapper;
    private final RegistLevelMapper registLevelMapper;
    private final AdminPersonnelImportExecutor importExecutor;

    public byte[] physicianTemplate() {
        List<Department> departments = departmentMapper.selectByType("临床科室");
        return PersonnelExcelWriter.writePhysicianTemplate(departments, registLevelMapper.selectAll());
    }

    public byte[] medtechTemplate() {
        return PersonnelExcelWriter.writeMedtechTemplate(departmentMapper.selectByType("医技科室"));
    }

    public byte[] exportPhysicians(Long departmentId, String keyword, Boolean includeDisabled) {
        List<PhysicianAdminView> records = employeeMapper.selectClinicalPhysicianAll(departmentId, keyword, includeDisabled);
        List<String[]> rows = records.stream()
            .map(record -> new String[] {
                String.valueOf(record.getId()),
                nullToEmpty(record.getRealname()),
                nullToEmpty(record.getDeptName()),
                nullToEmpty(record.getRegistName()),
                employeeStatusLabel(record.getDelmark()),
                record.getUsername() == null ? "" : record.getUsername(),
                accountStatusLabel(record.getUserId(), record.getAccountStatus()),
            })
            .collect(Collectors.toList());
        return PersonnelExcelWriter.writePhysicianExport(rows);
    }

    public byte[] exportMedtechEmployees(Long departmentId, String keyword, Boolean includeDisabled) {
        List<MedtechAdminView> records = adminMedtechMapper.selectMedtechEmployeeAll(departmentId, keyword, includeDisabled);
        List<String[]> rows = records.stream()
            .map(record -> new String[] {
                String.valueOf(record.getId()),
                nullToEmpty(record.getRealname()),
                nullToEmpty(record.getDeptName()),
                employeeStatusLabel(record.getDelmark()),
                record.getUsername() == null ? "" : record.getUsername(),
                accountStatusLabel(record.getUserId(), record.getAccountStatus()),
            })
            .collect(Collectors.toList());
        return PersonnelExcelWriter.writeMedtechExport(rows);
    }

    public PersonnelImportResult importPhysicians(MultipartFile file) {
        validateXlsx(file);
        List<Map<String, String>> rows = readRows(file, PHYSICIAN_IMPORT_HEADERS);
        PersonnelImportResult result = new PersonnelImportResult();
        result.setTotalRows(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);
            try {
                AdminPersonnelImportExecutor.ImportRowOutcome outcome = importExecutor.importPhysicianRow(row);
                addRowResult(result, rowNumber, outcome.status(), outcome.message(), outcome.employeeId(), outcome.username());
            } catch (BusinessException ex) {
                addRowResult(result, rowNumber, "failed", ex.getMessage(), null, null);
            } catch (Exception ex) {
                addRowResult(result, rowNumber, "failed", "导入失败：" + ex.getMessage(), null, null);
            }
        }
        return result;
    }

    public PersonnelImportResult importMedtechEmployees(MultipartFile file) {
        validateXlsx(file);
        List<Map<String, String>> rows = readRows(file, MEDTECH_IMPORT_HEADERS);
        PersonnelImportResult result = new PersonnelImportResult();
        result.setTotalRows(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);
            try {
                AdminPersonnelImportExecutor.ImportRowOutcome outcome = importExecutor.importMedtechRow(row);
                addRowResult(result, rowNumber, outcome.status(), outcome.message(), outcome.employeeId(), outcome.username());
            } catch (BusinessException ex) {
                addRowResult(result, rowNumber, "failed", ex.getMessage(), null, null);
            } catch (Exception ex) {
                addRowResult(result, rowNumber, "failed", "导入失败：" + ex.getMessage(), null, null);
            }
        }
        return result;
    }

    public String physicianExportFilename() {
        return "诊疗医生_" + today() + ".xlsx";
    }

    public String physicianExportAsciiFilename() {
        return "physicians_" + today() + ".xlsx";
    }

    public String medtechExportFilename() {
        return "医技人员_" + today() + ".xlsx";
    }

    public String medtechExportAsciiFilename() {
        return "medtech_employees_" + today() + ".xlsx";
    }

    public String physicianTemplateFilename() {
        return "诊疗医生导入模板.xlsx";
    }

    public String physicianTemplateAsciiFilename() {
        return "physician_import_template.xlsx";
    }

    public String medtechTemplateFilename() {
        return "医技人员导入模板.xlsx";
    }

    public String medtechTemplateAsciiFilename() {
        return "medtech_import_template.xlsx";
    }

    private List<Map<String, String>> readRows(MultipartFile file, List<String> headers) {
        try {
            return PersonnelExcelReader.readDataRows(file.getInputStream(), headers);
        } catch (IOException ex) {
            throw new BusinessException(400, "无法读取上传文件");
        }
    }

    private void validateXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) {
            throw new BusinessException(400, "仅支持 .xlsx 文件");
        }
    }

    private void addRowResult(
        PersonnelImportResult result,
        int rowNumber,
        String status,
        String message,
        Long employeeId,
        String username
    ) {
        PersonnelImportRowResult rowResult = new PersonnelImportRowResult();
        rowResult.setRowNumber(rowNumber);
        rowResult.setStatus(status);
        rowResult.setMessage(message);
        rowResult.setEmployeeId(employeeId);
        rowResult.setUsername(username);
        result.getRows().add(rowResult);
        switch (status) {
            case "success" -> result.setSuccessCount(result.getSuccessCount() + 1);
            case "skipped" -> result.setSkippedCount(result.getSkippedCount() + 1);
            case "failed" -> result.setFailedCount(result.getFailedCount() + 1);
            default -> {
            }
        }
    }

    private String employeeStatusLabel(Integer delmark) {
        return delmark != null && delmark == 0 ? "在职" : "已停用";
    }

    private String accountStatusLabel(Long userId, Integer accountStatus) {
        if (userId == null) {
            return "未创建";
        }
        return accountStatus != null && accountStatus == 1 ? "启用" : "停用";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
