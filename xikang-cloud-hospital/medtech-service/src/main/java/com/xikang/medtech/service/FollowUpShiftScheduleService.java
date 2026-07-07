package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpShiftScheduleService {

    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpDashboardMapper dashboardMapper;

    public Map<String, Object> getPlan(Long departmentId, String month) {
        return shiftMapper.selectPlanByDeptMonth(departmentId, month);
    }

    public List<Map<String, Object>> listMyShifts(LocalDate from, LocalDate to) {
        Long employeeId = requireEmployeeId();
        List<Map<String, Object>> shifts = shiftMapper.selectStaffShifts(employeeId, null, from, to);
        enrichShiftsWithTasks(shifts);
        return shifts;
    }

    public List<Map<String, Object>> listDepartmentShifts(Long departmentId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> shifts = shiftMapper.selectStaffShifts(null, departmentId, from, to);
        enrichShiftsWithTasks(shifts);
        return shifts;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> persistGeneratedPlan(
        Long departmentId,
        String month,
        Map<String, Object> generated,
        boolean aiGenerated
    ) {
        Map<String, Object> existing = shiftMapper.selectPlanByDeptMonth(departmentId, month);
        Long planId;
        if (existing != null && !existing.isEmpty()) {
            planId = toLong(existing.get("id"));
            shiftMapper.deleteShiftsByPlanId(planId);
        } else {
            Map<String, Object> planPayload = new HashMap<>();
            planPayload.put("departmentId", departmentId);
            planPayload.put("month", month);
            planPayload.put("status", "draft");
            planPayload.put("aiGenerated", aiGenerated);
            planPayload.put("aiSummary", generated.get("summary"));
            planPayload.put("createdBy", MedtechAuthContext.employeeIdOrNull());
            shiftMapper.insertPlan(planPayload);
            planId = toLong(planPayload.get("id"));
        }

        List<Map<String, Object>> shifts = (List<Map<String, Object>>) generated.get("shifts");
        if (shifts == null) {
            shifts = List.of();
        }
        int shiftCount = 0;
        int taskCount = 0;
        for (Map<String, Object> shift : shifts) {
            Map<String, Object> shiftPayload = new HashMap<>();
            shiftPayload.put("planId", planId);
            shiftPayload.put("employeeId", toLong(shift.get("employee_id")));
            shiftPayload.put("departmentId", departmentId);
            shiftPayload.put("workDate", LocalDate.parse(String.valueOf(shift.get("work_date"))));
            shiftPayload.put("shiftType", shift.getOrDefault("shift_type", "full"));
            shiftPayload.put("status", "planned");
            shiftMapper.insertStaffShift(shiftPayload);
            Long shiftId = toLong(shiftPayload.get("id"));
            shiftCount++;

            List<Map<String, Object>> tasks = (List<Map<String, Object>>) shift.get("contact_tasks");
            if (tasks != null) {
                for (Map<String, Object> task : tasks) {
                    Map<String, Object> taskPayload = new HashMap<>();
                    taskPayload.put("shiftId", shiftId);
                    taskPayload.put("registerId", toLong(task.get("register_id")));
                    taskPayload.put("priorityLevel", task.getOrDefault("priority", "normal"));
                    shiftMapper.insertContactTask(taskPayload);
                    taskCount++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("shiftCount", shiftCount);
        result.put("taskCount", taskCount);
        result.put("summary", generated.get("summary"));
        return result;
    }

    @Transactional
    public Map<String, Object> publishPlan(Long planId) {
        Map<String, Object> plan = shiftMapper.selectPlanById(planId);
        if (plan == null || plan.isEmpty()) {
            throw new BusinessException("排班计划不存在");
        }
        shiftMapper.updatePlanStatus(planId, "published");
        return shiftMapper.selectPlanById(planId);
    }

    private void enrichShiftsWithTasks(List<Map<String, Object>> shifts) {
        for (Map<String, Object> shift : shifts) {
            Long shiftId = toLong(shift.get("id"));
            if (shiftId != null) {
                shift.put("contactTasks", shiftMapper.selectContactTasksByShiftId(shiftId));
            }
        }
    }

    public String resolveDepartmentName(Long departmentId) {
        Map<String, Object> brief = dashboardMapper.selectEmployeeBrief(
            MedtechAuthContext.employeeIdOrNull() != null
                ? MedtechAuthContext.employeeIdOrNull()
                : 1L
        );
        if (brief != null && brief.get("departmentName") != null) {
            return String.valueOf(brief.get("departmentName"));
        }
        return "科室";
    }

    private Long requireEmployeeId() {
        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        if (employeeId == null) {
            throw new BusinessException(403, "当前账号未绑定员工");
        }
        return employeeId;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
