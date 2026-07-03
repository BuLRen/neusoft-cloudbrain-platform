package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminEmployeeAccountHelper {

    public static final String PHYSICIAN_DEFAULT_PASSWORD = "doctor123";
    public static final String MEDTECH_DEFAULT_PASSWORD = "medtech123";
    public static final String FOLLOWUP_DEFAULT_PASSWORD = "followup123";

    private final EmployeeMapper employeeMapper;
    private final UserAccountMapper userAccountMapper;

    public Employee insertPhysicianEmployee(String realname, Long deptmentId, Long registLevelId) {
        Employee employee = new Employee();
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(registLevelId);
        employee.setDelmark(0);
        employeeMapper.insert(employee);
        return employee;
    }

    public Employee insertMedtechEmployee(String realname, Long deptmentId) {
        Employee employee = new Employee();
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(null);
        employee.setDelmark(0);
        employeeMapper.insert(employee);
        return employee;
    }

    public Employee insertFollowUpEmployee(String realname, Long deptmentId) {
        Employee employee = new Employee();
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(null);
        employee.setDelmark(0);
        employeeMapper.insert(employee);
        return employee;
    }

    public String createPhysicianAccount(Long employeeId, String realname, String username, String password) {
        return createAccount(employeeId, realname, username, password, "physician");
    }

    public String createMedtechAccount(Long employeeId, String realname, String username, String password) {
        return createAccount(employeeId, realname, username, password, "medtech");
    }

    public String createFollowUpAccount(Long employeeId, String realname, String username, String password) {
        return createAccount(employeeId, realname, username, password, "followup");
    }

    public String createPhysicianAccountWithPrefix(Long employeeId, String realname, String password) {
        return createAccountWithPrefix(employeeId, realname, "doc", password, "physician");
    }

    public String createMedtechAccountWithPrefix(Long employeeId, String realname, String password) {
        return createAccountWithPrefix(employeeId, realname, "tech", password, "medtech");
    }

    public String createFollowUpAccountWithPrefix(Long employeeId, String realname, String password) {
        return createAccountWithPrefix(employeeId, realname, "followup", password, "followup");
    }

    private String createAccountWithPrefix(
        Long employeeId,
        String realname,
        String prefix,
        String password,
        String accountType
    ) {
        String username = prefix + "_" + employeeId;
        return createAccount(employeeId, realname, username, password, accountType);
    }

    private String createAccount(
        Long employeeId,
        String realname,
        String username,
        String password,
        String accountType
    ) {
        if (username == null || username.isBlank()) {
            username = switch (accountType) {
                case "physician" -> "doc_" + employeeId;
                case "followup" -> "followup_" + employeeId;
                default -> "tech_" + employeeId;
            };
        }
        username = username.trim();
        if (userAccountMapper.countByUsername(username) > 0) {
            throw new BusinessException(409, "用户名已存在：" + username);
        }
        if (password == null || password.isBlank()) {
            password = switch (accountType) {
                case "physician" -> PHYSICIAN_DEFAULT_PASSWORD;
                case "followup" -> FOLLOWUP_DEFAULT_PASSWORD;
                default -> MEDTECH_DEFAULT_PASSWORD;
            };
        }
        Map<String, Object> row = new HashMap<>();
        row.put("username", username);
        row.put("password", password);
        row.put("realName", realname);
        row.put("employeeId", employeeId);
        switch (accountType) {
            case "physician" -> userAccountMapper.insertPhysicianAccount(row);
            case "followup" -> userAccountMapper.insertFollowUpAccount(row);
            default -> userAccountMapper.insertMedtechAccount(row);
        }
        return username;
    }
}
