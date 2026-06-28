package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.dto.PhysicianAdminView;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPhysicianService {

    private final EmployeeMapper employeeMapper;
    private final UserAccountMapper userAccountMapper;
    private final AdminEmployeeAccountHelper accountHelper;

    public Map<String, Object> listPhysicians(Long departmentId, String keyword, Boolean includeDisabled, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        int offset = (currentPage - 1) * pageSize;
        long total = employeeMapper.countClinicalPhysicians(departmentId, keyword, includeDisabled);
        var records = employeeMapper.selectClinicalPhysicianPage(
            departmentId, keyword, includeDisabled, offset, pageSize
        );
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public PhysicianAdminView getPhysician(Long id) {
        PhysicianAdminView view = employeeMapper.selectClinicalPhysicianById(id);
        if (view == null) {
            throw new BusinessException(404, "诊疗医生不存在");
        }
        return view;
    }

    @Transactional
    public PhysicianAdminView createPhysician(Map<String, Object> request) {
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        Long registLevelId = longValue(request.get("registLevelId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "医生姓名不能为空");
        }
        if (deptmentId == null || deptmentId < 1 || deptmentId > 20) {
            throw new BusinessException(400, "请选择临床科室");
        }
        if (registLevelId == null) {
            throw new BusinessException(400, "请选择挂号级别");
        }

        Employee employee = accountHelper.insertPhysicianEmployee(realname, deptmentId, registLevelId);

        if (Boolean.TRUE.equals(request.get("createAccount"))) {
            createAccountForEmployee(employee.getId(), realname.trim(), request);
        }
        return getPhysician(employee.getId());
    }

    @Transactional
    public PhysicianAdminView updatePhysician(Long id, Map<String, Object> request) {
        PhysicianAdminView existing = getPhysician(id);
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        Long registLevelId = longValue(request.get("registLevelId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "医生姓名不能为空");
        }
        if (deptmentId == null || deptmentId < 1 || deptmentId > 20) {
            throw new BusinessException(400, "请选择临床科室");
        }
        if (registLevelId == null) {
            throw new BusinessException(400, "请选择挂号级别");
        }

        Employee employee = new Employee();
        employee.setId(id);
        employee.setRealname(realname.trim());
        employee.setDeptmentId(deptmentId);
        employee.setRegistLevelId(registLevelId);
        employeeMapper.update(employee);
        userAccountMapper.updateRealNameByEmployeeId(id, realname.trim());

        if (Boolean.TRUE.equals(request.get("createAccount")) && existing.getUserId() == null) {
            createAccountForEmployee(id, realname.trim(), request);
        }
        return getPhysician(id);
    }

    @Transactional
    public PhysicianAdminView updatePhysicianStatus(Long id, Map<String, Object> request) {
        getPhysician(id);
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        if (!enabled) {
            if (employeeMapper.countActiveRegistersByEmployeeId(id) > 0) {
                throw new BusinessException(409, "该医生仍有未完成就诊，无法停用");
            }
            employeeMapper.updateDelmark(id, 1);
        } else {
            employeeMapper.updateDelmark(id, 0);
        }
        return getPhysician(id);
    }

    @Transactional
    public void createAccount(Long id, Map<String, Object> request) {
        PhysicianAdminView physician = getPhysician(id);
        if (physician.getUserId() != null) {
            throw new BusinessException(409, "该医生已有登录账号");
        }
        createAccountForEmployee(id, physician.getRealname(), request);
    }

    @Transactional
    public void resetAccountPassword(Long id, Map<String, Object> request) {
        PhysicianAdminView physician = getPhysician(id);
        if (physician.getUserId() == null) {
            throw new BusinessException(404, "该医生尚未创建登录账号");
        }
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = AdminEmployeeAccountHelper.PHYSICIAN_DEFAULT_PASSWORD;
        }
        userAccountMapper.updatePassword(physician.getUserId(), password);
    }

    @Transactional
    public void updateAccountStatus(Long id, Map<String, Object> request) {
        PhysicianAdminView physician = getPhysician(id);
        if (physician.getUserId() == null) {
            throw new BusinessException(404, "该医生尚未创建登录账号");
        }
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        userAccountMapper.updateStatus(physician.getUserId(), enabled ? 1 : 0);
    }

    private void createAccountForEmployee(Long employeeId, String realname, Map<String, Object> request) {
        String username = stringValue(request.get("username"));
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = AdminEmployeeAccountHelper.PHYSICIAN_DEFAULT_PASSWORD;
        }
        accountHelper.createPhysicianAccount(employeeId, realname, username, password);
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
