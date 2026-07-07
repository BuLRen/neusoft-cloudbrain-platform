package com.xikang.medtech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.mapper.FollowUpClinicalMapper;
import com.xikang.medtech.mapper.FollowUpPendingScheduleMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpShiftEnqueueService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FollowUpEnqueueDifyService enqueueDifyService;
    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpPendingScheduleMapper pendingScheduleMapper;
    private final FollowUpClinicalMapper clinicalMapper;

    public void enqueueAsync(
        Long registerId,
        LocalDateTime visitEndedAt,
        Long departmentId,
        String priorityLevel,
        Long preferMonitorEmployeeId
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                applyEnqueue(registerId, visitEndedAt, departmentId, priorityLevel, preferMonitorEmployeeId);
            } catch (Exception ex) {
                log.warn("异步入队失败 registerId={}: {}", registerId, ex.getMessage());
            }
        });
    }

    @Transactional
    public Map<String, Object> applyEnqueue(
        Long registerId,
        LocalDateTime visitEndedAt,
        Long departmentId,
        String priorityLevel,
        Long preferMonitorEmployeeId
    ) {
        LocalDate visitDate = visitEndedAt != null ? visitEndedAt.toLocalDate() : LocalDate.now();
        String departmentName = resolveDepartmentName(departmentId);

        Map<String, Object> planned = enqueueDifyService.planEnqueue(
            registerId,
            visitDate,
            departmentId,
            departmentName,
            priorityLevel,
            preferMonitorEmployeeId
        );

        LocalDate workDate = LocalDate.parse(String.valueOf(planned.get("work_date")));
        Map<String, Object> existingTask = shiftMapper.selectContactTaskByRegisterAndWorkDate(registerId, workDate);
        if (existingTask != null && !existingTask.isEmpty()) {
            Map<String, Object> skipped = new LinkedHashMap<>(planned);
            skipped.put("skipped", true);
            skipped.put("reason", "duplicate_register_work_date");
            return skipped;
        }

        Long employeeId = toLong(planned.get("employee_id"));
        String month = YearMonth.from(workDate).format(MONTH);
        Map<String, Object> plan = shiftMapper.selectPlanByDeptMonth(departmentId, month);

        if (plan == null || plan.isEmpty() || !"draft".equals(String.valueOf(plan.get("status")))) {
            return savePending(registerId, departmentId, priorityLevel, employeeId, workDate, visitEndedAt, planned);
        }

        Long planId = toLong(plan.get("id"));
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
        taskPayload.put("priorityLevel", planned.get("priority") != null ? planned.get("priority") : priorityLevel);
        shiftMapper.insertContactTask(taskPayload);

        Map<String, Object> result = new LinkedHashMap<>(planned);
        result.put("shiftId", shiftId);
        result.put("planId", planId);
        result.put("persisted", true);
        return result;
    }

    private Map<String, Object> savePending(
        Long registerId,
        Long departmentId,
        String priorityLevel,
        Long employeeId,
        LocalDate workDate,
        LocalDateTime visitEndedAt,
        Map<String, Object> planned
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("priorityLevel", priorityLevel);
        payload.put("employeeId", employeeId);
        payload.put("workDate", workDate);
        payload.put("visitEndedAt", visitEndedAt);
        payload.put("enqueuePayloadJson", toJson(planned));
        pendingScheduleMapper.insertPending(payload);

        Map<String, Object> result = new LinkedHashMap<>(planned);
        result.put("pending", true);
        result.put("persisted", true);
        return result;
    }

    private String resolveDepartmentName(Long departmentId) {
        if (departmentId == null) {
            return "";
        }
        Map<String, Object> dept = clinicalMapper.selectRegisterDepartmentBriefByDepartmentId(departmentId);
        if (dept != null && dept.get("departmentName") != null) {
            return String.valueOf(dept.get("departmentName"));
        }
        return "";
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
