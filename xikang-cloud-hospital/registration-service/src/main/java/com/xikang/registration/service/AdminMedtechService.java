package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.dto.MedtechAdminView;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.mapper.AdminMedtechMapper;
import com.xikang.registration.mapper.DepartmentMapper;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMedtechService {

    private static final String DEFAULT_PASSWORD = "medtech123";

    private final AdminMedtechMapper adminMedtechMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final UserAccountMapper userAccountMapper;

    public Map<String, Object> listMedtechEmployees(
        Long departmentId,
        String keyword,
        Boolean includeDisabled,
        Integer page,
        Integer size
    ) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        int offset = (currentPage - 1) * pageSize;
        long total = adminMedtechMapper.countMedtechEmployees(departmentId, keyword, includeDisabled);
        List<MedtechAdminView> records = adminMedtechMapper.selectMedtechEmployeePage(
            departmentId, keyword, includeDisabled, offset, pageSize
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public MedtechAdminView getMedtechEmployee(Long id) {
        MedtechAdminView view = adminMedtechMapper.selectMedtechEmployeeById(id);
        if (view == null) {
            throw new BusinessException(404, "医技人员不存在");
        }
        return view;
    }

    @Transactional
    public MedtechAdminView createMedtechEmployee(Map<String, Object> request) {
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "医技人员姓名不能为空");
        }
        assertMedtechDepartment(deptmentId);

        Employee employee = new Employee();
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(null);
        employee.setDelmark(0);
        employeeMapper.insert(employee);

        if (Boolean.TRUE.equals(request.get("createAccount"))) {
            createAccountForEmployee(employee.getId(), realname.trim(), request);
        }
        return getMedtechEmployee(employee.getId());
    }

    @Transactional
    public MedtechAdminView updateMedtechEmployee(Long id, Map<String, Object> request) {
        MedtechAdminView existing = getMedtechEmployee(id);
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "医技人员姓名不能为空");
        }
        assertMedtechDepartment(deptmentId);

        Employee employee = new Employee();
        employee.setId(id);
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(null);
        employeeMapper.update(employee);
        userAccountMapper.updateRealNameByEmployeeId(id, realname.trim());

        if (Boolean.TRUE.equals(request.get("createAccount")) && existing.getUserId() == null) {
            createAccountForEmployee(id, realname.trim(), request);
        }
        return getMedtechEmployee(id);
    }

    @Transactional
    public MedtechAdminView updateMedtechEmployeeStatus(Long id, Map<String, Object> request) {
        getMedtechEmployee(id);
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        employeeMapper.updateDelmark(id, enabled ? 0 : 1);
        return getMedtechEmployee(id);
    }

    @Transactional
    public void createAccount(Long id, Map<String, Object> request) {
        MedtechAdminView employee = getMedtechEmployee(id);
        if (employee.getUserId() != null) {
            throw new BusinessException(409, "该医技人员已有登录账号");
        }
        createAccountForEmployee(id, employee.getRealname(), request);
    }

    @Transactional
    public void resetAccountPassword(Long id, Map<String, Object> request) {
        MedtechAdminView employee = getMedtechEmployee(id);
        if (employee.getUserId() == null) {
            throw new BusinessException(404, "该医技人员尚未创建登录账号");
        }
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = DEFAULT_PASSWORD;
        }
        userAccountMapper.updatePassword(employee.getUserId(), password);
    }

    @Transactional
    public void updateAccountStatus(Long id, Map<String, Object> request) {
        MedtechAdminView employee = getMedtechEmployee(id);
        if (employee.getUserId() == null) {
            throw new BusinessException(404, "该医技人员尚未创建登录账号");
        }
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        userAccountMapper.updateStatus(employee.getUserId(), enabled ? 1 : 0);
    }

    private void assertMedtechDepartment(Long deptmentId) {
        if (deptmentId == null) {
            throw new BusinessException(400, "请选择医技科室");
        }
        Department department = departmentMapper.selectById(deptmentId);
        if (department == null || department.getDelmark() != null && department.getDelmark() != 0) {
            throw new BusinessException(400, "科室不存在或已停用");
        }
        if (!"医技科室".equals(department.getType())) {
            throw new BusinessException(400, "请选择医技科室");
        }
    }

    private void createAccountForEmployee(Long employeeId, String realname, Map<String, Object> request) {
        String username = stringValue(request.get("username"));
        if (username == null || username.isBlank()) {
            username = "tech_" + employeeId;
        }
        if (userAccountMapper.countByUsername(username) > 0) {
            throw new BusinessException(409, "用户名已存在：" + username);
        }
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = DEFAULT_PASSWORD;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("username", username.trim());
        row.put("password", password);
        row.put("realName", realname);
        row.put("employeeId", employeeId);
        userAccountMapper.insertMedtechAccount(row);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }
}
