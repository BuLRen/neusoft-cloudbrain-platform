package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowUpShiftScheduleService {

    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpShiftEnqueueService shiftEnqueueService;

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
        List<Map<String, Object>> preservedTasks = List.of();
        if (existing != null && !existing.isEmpty()) {
            planId = toLong(existing.get("id"));
            preservedTasks = shiftMapper.selectContactTasksByPlanId(planId);
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

        List<Map<String, Object>> planningPatients = shiftMapper.selectPatientsForShiftPlanning(departmentId);
        List<Map<String, Object>> shifts = (List<Map<String, Object>>) generated.get("shifts");
        if (shifts == null) {
            shifts = List.of();
        }
        int shiftCount = 0;
        int taskCount = 0;
        for (Map<String, Object> rawShift : shifts) {
            Map<String, Object> shift = FollowUpShiftPlanSupport.normalizeShift(rawShift, planningPatients);
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

        int restoredCount = restorePreservedTasks(planId, departmentId, preservedTasks, shifts, planningPatients);
        int flushedPending = shiftEnqueueService.flushPendingForDepartment(departmentId, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("shiftCount", shiftCount);
        result.put("taskCount", taskCount);
        result.put("restoredTaskCount", restoredCount);
        result.put("flushedPendingCount", flushedPending);
        result.put("summary", generated.get("summary"));
        if (generated.get("patientCount") != null) {
            result.put("patientCount", generated.get("patientCount"));
        }
        if (generated.get("scheduleNote") != null) {
            result.put("scheduleNote", generated.get("scheduleNote"));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> publishPlan(Long planId) {
        Map<String, Object> plan = shiftMapper.selectPlanById(planId);
        if (plan == null || plan.isEmpty()) {
            throw new BusinessException("排班计划不存在");
        }
        shiftMapper.updatePlanStatus(planId, "published");
        Long departmentId = toLong(plan.get("departmentId"));
        String month = plan.get("month") != null ? String.valueOf(plan.get("month")) : null;
        if (departmentId != null && month != null) {
            shiftEnqueueService.flushPendingForDepartment(departmentId, month);
        }
        return shiftMapper.selectPlanById(planId);
    }

    private int restorePreservedTasks(
        Long planId,
        Long departmentId,
        List<Map<String, Object>> preservedTasks,
        List<Map<String, Object>> aiShifts,
        List<Map<String, Object>> planningPatients
    ) {
        if (preservedTasks == null || preservedTasks.isEmpty()) {
            return 0;
        }
        FollowUpShiftPlanSupport.PatientPool pool = FollowUpShiftPlanSupport.PatientPool.from(planningPatients);
        Set<String> aiTaskKeys = buildAiTaskKeys(aiShifts);
        int restored = 0;
        for (Map<String, Object> task : preservedTasks) {
            Long registerId = toLong(task.get("registerId"));
            LocalDate workDate = parseWorkDate(task.get("workDate"));
            if (registerId == null || workDate == null) {
                continue;
            }
            if (!pool.contains(registerId)) {
                continue;
            }
            String key = registerId + "|" + workDate;
            if (aiTaskKeys.contains(key)) {
                continue;
            }

            Long employeeId = toLong(task.get("employeeId"));
            if (employeeId == null) {
                continue;
            }
            Long monitorEmployeeId = pool.monitorEmployeeId(registerId);
            if (monitorEmployeeId != null && !monitorEmployeeId.equals(employeeId)) {
                continue;
            }

            Map<String, Object> shift = shiftMapper.selectShiftInPlanByEmployeeAndDate(planId, employeeId, workDate);
            Long shiftId;
            if (shift == null || shift.isEmpty()) {
                Map<String, Object> shiftPayload = new HashMap<>();
                shiftPayload.put("planId", planId);
                shiftPayload.put("employeeId", employeeId);
                shiftPayload.put("departmentId", departmentId);
                shiftPayload.put("workDate", workDate);
                shiftPayload.put("shiftType", "full");
                shiftPayload.put("status", "planned");
                shiftMapper.insertStaffShift(shiftPayload);
                shiftId = toLong(shiftPayload.get("id"));
            } else {
                shiftId = toLong(shift.get("id"));
            }

            Map<String, Object> taskPayload = new HashMap<>();
            taskPayload.put("shiftId", shiftId);
            taskPayload.put("registerId", registerId);
            taskPayload.put("priorityLevel", task.getOrDefault("priorityLevel", "normal"));
            shiftMapper.insertContactTask(taskPayload);
            restored++;
        }
        return restored;
    }

    @SuppressWarnings("unchecked")
    private Set<String> buildAiTaskKeys(List<Map<String, Object>> aiShifts) {
        Set<String> keys = new HashSet<>();
        if (aiShifts == null) {
            return keys;
        }
        for (Map<String, Object> shift : aiShifts) {
            String workDate = String.valueOf(shift.get("work_date"));
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) shift.get("contact_tasks");
            if (tasks == null) {
                continue;
            }
            for (Map<String, Object> task : tasks) {
                Long registerId = toLong(task.get("register_id"));
                if (registerId != null) {
                    keys.add(registerId + "|" + workDate);
                }
            }
        }
        return keys;
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

    private LocalDate parseWorkDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(String.valueOf(value));
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
