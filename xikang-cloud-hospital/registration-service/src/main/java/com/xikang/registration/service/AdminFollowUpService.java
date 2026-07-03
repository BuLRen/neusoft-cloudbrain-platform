package com.xikang.registration.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.registration.dto.FollowUpAdminView;
import com.xikang.registration.entity.Department;
import com.xikang.registration.entity.Employee;
import com.xikang.registration.mapper.AdminFollowUpMapper;
import com.xikang.registration.mapper.DepartmentMapper;
import com.xikang.registration.mapper.EmployeeMapper;
import com.xikang.registration.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminFollowUpService {

    private final AdminFollowUpMapper adminFollowUpMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final UserAccountMapper userAccountMapper;
    private final AdminEmployeeAccountHelper accountHelper;

    public Map<String, Object> listFollowUpEmployees(
        Long departmentId,
        String keyword,
        Boolean includeDisabled,
        Integer page,
        Integer size
    ) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        int offset = (currentPage - 1) * pageSize;
        long total = adminFollowUpMapper.countFollowUpEmployees(departmentId, keyword, includeDisabled);
        List<FollowUpAdminView> records = adminFollowUpMapper.selectFollowUpEmployeePage(
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

    public FollowUpAdminView getFollowUpEmployee(Long id) {
        FollowUpAdminView view = adminFollowUpMapper.selectFollowUpEmployeeById(id);
        if (view == null) {
            throw new BusinessException(404, "随访人员不存在");
        }
        return view;
    }

    @Transactional
    public FollowUpAdminView createFollowUpEmployee(Map<String, Object> request) {
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "随访人员姓名不能为空");
        }
        assertClinicalDepartment(deptmentId);

        Employee employee = accountHelper.insertFollowUpEmployee(realname, deptmentId);

        if (Boolean.TRUE.equals(request.get("createAccount"))) {
            createAccountForEmployee(employee.getId(), realname.trim(), request);
        }
        return getFollowUpEmployee(employee.getId());
    }

    @Transactional
    public FollowUpAdminView updateFollowUpEmployee(Long id, Map<String, Object> request) {
        FollowUpAdminView existing = getFollowUpEmployee(id);
        String realname = stringValue(request.get("realname"));
        Long deptmentId = longValue(request.get("deptmentId"));
        if (realname == null || realname.isBlank()) {
            throw new BusinessException(400, "随访人员姓名不能为空");
        }
        assertClinicalDepartment(deptmentId);

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
        return getFollowUpEmployee(id);
    }

    @Transactional
    public FollowUpAdminView updateFollowUpEmployeeStatus(Long id, Map<String, Object> request) {
        getFollowUpEmployee(id);
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        employeeMapper.updateDelmark(id, enabled ? 0 : 1);
        return getFollowUpEmployee(id);
    }

    @Transactional
    public void createAccount(Long id, Map<String, Object> request) {
        FollowUpAdminView employee = getFollowUpEmployee(id);
        if (employee.getUserId() != null) {
            throw new BusinessException(409, "该随访人员已有登录账号");
        }
        createAccountForEmployee(id, employee.getRealname(), request);
    }

    @Transactional
    public void resetAccountPassword(Long id, Map<String, Object> request) {
        FollowUpAdminView employee = getFollowUpEmployee(id);
        if (employee.getUserId() == null) {
            throw new BusinessException(404, "该随访人员尚未创建登录账号");
        }
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = AdminEmployeeAccountHelper.FOLLOWUP_DEFAULT_PASSWORD;
        }
        userAccountMapper.updatePassword(employee.getUserId(), password);
    }

    @Transactional
    public void updateAccountStatus(Long id, Map<String, Object> request) {
        FollowUpAdminView employee = getFollowUpEmployee(id);
        if (employee.getUserId() == null) {
            throw new BusinessException(404, "该随访人员尚未创建登录账号");
        }
        Boolean enabled = request.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        userAccountMapper.updateStatus(employee.getUserId(), enabled ? 1 : 0);
    }

    private void assertClinicalDepartment(Long deptmentId) {
        if (deptmentId == null) {
            throw new BusinessException(400, "请选择临床科室");
        }
        Department department = departmentMapper.selectById(deptmentId);
        if (department == null || department.getDelmark() != null && department.getDelmark() != 0) {
            throw new BusinessException(400, "科室不存在或已停用");
        }
        if (!"临床科室".equals(department.getType())) {
            throw new BusinessException(400, "请选择临床科室");
        }
    }

    private void createAccountForEmployee(Long employeeId, String realname, Map<String, Object> request) {
        String username = stringValue(request.get("username"));
        String password = stringValue(request.get("password"));
        if (password == null || password.isBlank()) {
            password = AdminEmployeeAccountHelper.FOLLOWUP_DEFAULT_PASSWORD;
        }
        accountHelper.createFollowUpAccount(employeeId, realname, username, password);
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
