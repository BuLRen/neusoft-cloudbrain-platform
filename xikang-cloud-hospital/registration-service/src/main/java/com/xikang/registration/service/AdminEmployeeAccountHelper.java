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

    public String createPhysicianAccount(Long employeeId, String realname, String username, String password) {
        return createAccount(employeeId, realname, username, password, true);
    }

    public String createMedtechAccount(Long employeeId, String realname, String username, String password) {
        return createAccount(employeeId, realname, username, password, false);
    }

    public String createPhysicianAccountWithPrefix(Long employeeId, String realname, String password) {
        return createAccountWithPrefix(employeeId, realname, "doc", password, true);
    }

    public String createMedtechAccountWithPrefix(Long employeeId, String realname, String password) {
        return createAccountWithPrefix(employeeId, realname, "tech", password, false);
    }

    private String createAccountWithPrefix(
        Long employeeId,
        String realname,
        String prefix,
        String password,
        boolean physician
    ) {
        String username = prefix + "_" + employeeId;
        return createAccount(employeeId, realname, username, password, physician);
    }

    private String createAccount(
        Long employeeId,
        String realname,
        String username,
        String password,
        boolean physician
    ) {
        if (username == null || username.isBlank()) {
            username = (physician ? "doc_" : "tech_") + employeeId;
        }
        username = username.trim();
        if (userAccountMapper.countByUsername(username) > 0) {
            throw new BusinessException(409, "用户名已存在：" + username);
        }
        if (password == null || password.isBlank()) {
            password = physician ? PHYSICIAN_DEFAULT_PASSWORD : MEDTECH_DEFAULT_PASSWORD;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("username", username);
        row.put("password", password);
        row.put("realName", realname);
        row.put("employeeId", employeeId);
        if (physician) {
            userAccountMapper.insertPhysicianAccount(row);
        } else {
            userAccountMapper.insertMedtechAccount(row);
        }
        return username;
    }
}
