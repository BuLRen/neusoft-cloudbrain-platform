package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.dto.PersonnelImportResult;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.entity.RegistLevel;
import com.xikang.registration.mapper.AdminFollowUpMapper;
import com.xikang.registration.mapper.AdminMedtechMapper;
import com.xikang.registration.mapper.DepartmentMapper;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.RegistLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPersonnelImportExecutor {

    private final EmployeeMapper employeeMapper;
    private final AdminMedtechMapper adminMedtechMapper;
    private final AdminFollowUpMapper adminFollowUpMapper;
    private final DepartmentMapper departmentMapper;
    private final RegistLevelMapper registLevelMapper;
    private final AdminEmployeeAccountHelper accountHelper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowOutcome importPhysicianRow(Map<String, String> row) {
        String realname = requiredValue(row, "姓名");
        String deptName = requiredValue(row, "科室");
        String registName = requiredValue(row, "挂号级别");

        Department department = resolveClinicalDepartment(deptName);
        RegistLevel registLevel = resolveRegistLevel(registName);

        if (employeeMapper.existsActiveClinicalPhysician(realname, department.getId(), registLevel.getId()) > 0) {
            return ImportRowOutcome.skipped("相同在职档案已存在，已跳过");
        }

        Employee employee = accountHelper.insertPhysicianEmployee(realname, department.getId(), registLevel.getId());
        String username = accountHelper.createPhysicianAccountWithPrefix(
            employee.getId(),
            realname,
            AdminEmployeeAccountHelper.PHYSICIAN_DEFAULT_PASSWORD
        );
        return ImportRowOutcome.success(employee.getId(), username);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowOutcome importMedtechRow(Map<String, String> row) {
        String realname = requiredValue(row, "姓名");
        String deptName = requiredValue(row, "医技科室");

        Department department = resolveMedtechDepartment(deptName);

        if (adminMedtechMapper.existsActiveMedtechEmployee(realname, department.getId()) > 0) {
            return ImportRowOutcome.skipped("相同在职档案已存在，已跳过");
        }

        Employee employee = accountHelper.insertMedtechEmployee(realname, department.getId());
        String username = accountHelper.createMedtechAccountWithPrefix(
            employee.getId(),
            realname,
            AdminEmployeeAccountHelper.MEDTECH_DEFAULT_PASSWORD
        );
        return ImportRowOutcome.success(employee.getId(), username);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowOutcome importFollowUpRow(Map<String, String> row) {
        String realname = requiredValue(row, "姓名");
        String deptName = requiredValue(row, "临床科室");

        Department department = resolveClinicalDepartment(deptName);

        if (adminFollowUpMapper.existsActiveFollowUpEmployee(realname, department.getId()) > 0) {
            return ImportRowOutcome.skipped("相同在职随访档案已存在，已跳过");
        }

        Employee employee = accountHelper.insertFollowUpEmployee(realname, department.getId());
        String username = accountHelper.createFollowUpAccountWithPrefix(
            employee.getId(),
            realname,
            AdminEmployeeAccountHelper.FOLLOWUP_DEFAULT_PASSWORD
        );
        return ImportRowOutcome.success(employee.getId(), username);
    }

    private Department resolveClinicalDepartment(String deptName) {
        Department department = departmentMapper.selectByName(deptName);
        if (department == null || !"临床科室".equals(department.getType())) {
            throw new BusinessException(400, "科室「" + deptName + "」不存在或不是临床科室");
        }
        return department;
    }

    private Department resolveMedtechDepartment(String deptName) {
        Department department = departmentMapper.selectByName(deptName);
        if (department == null || !"医技科室".equals(department.getType())) {
            throw new BusinessException(400, "医技科室「" + deptName + "」不存在");
        }
        return department;
    }

    private RegistLevel resolveRegistLevel(String registName) {
        RegistLevel registLevel = registLevelMapper.selectByName(registName);
        if (registLevel == null) {
            throw new BusinessException(400, "挂号级别「" + registName + "」不存在");
        }
        return registLevel;
    }

    private String requiredValue(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, key + "不能为空");
        }
        return value.trim();
    }

    public record ImportRowOutcome(String status, String message, Long employeeId, String username) {
        static ImportRowOutcome success(Long employeeId, String username) {
            return new ImportRowOutcome("success", "导入成功", employeeId, username);
        }

        static ImportRowOutcome skipped(String message) {
            return new ImportRowOutcome("skipped", message, null, null);
        }
    }
}
