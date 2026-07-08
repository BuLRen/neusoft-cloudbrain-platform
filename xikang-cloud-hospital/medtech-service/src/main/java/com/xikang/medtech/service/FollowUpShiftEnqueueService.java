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
        String taskPriority = planned.get("priority") != null
            ? String.valueOf(planned.get("priority"))
            : priorityLevel;

        Map<String, Object> persisted = persistContactTask(
            registerId,
            departmentId,
            employeeId,
            workDate,
            taskPriority
        );
        if (persisted != null) {
            Map<String, Object> result = new LinkedHashMap<>(planned);
            result.putAll(persisted);
            result.put("persisted", true);
            return result;
        }

        return savePending(registerId, departmentId, priorityLevel, employeeId, workDate, visitEndedAt, planned);
    }

    @Transactional
    public int flushPendingForDepartment(Long departmentId, String month) {
        if (departmentId == null || month == null || month.isBlank()) {
            return 0;
        }
        YearMonth yearMonth = YearMonth.parse(month);
        List<Map<String, Object>> pendingRows = pendingScheduleMapper.selectPendingByDepartment(
            departmentId,
            yearMonth.atDay(1),
            yearMonth.atEndOfMonth(),
            "pending"
        );
        int applied = 0;
        for (Map<String, Object> row : pendingRows) {
            if (applyPendingRow(row)) {
                Long pendingId = toLong(row.get("id"));
                if (pendingId != null) {
                    pendingScheduleMapper.markApplied(pendingId);
                }
                applied++;
            }
        }
        return applied;
    }

    @Transactional
    public boolean applyPendingRow(Map<String, Object> pending) {
        Long registerId = toLong(pending.get("registerId"));
        Long departmentId = toLong(pending.get("departmentId"));
        Long employeeId = toLong(pending.get("employeeId"));
        LocalDate workDate = parseDate(pending.get("workDate"));
        String priorityLevel = pending.get("priorityLevel") != null
            ? String.valueOf(pending.get("priorityLevel"))
            : "normal";

        if (registerId == null || departmentId == null || employeeId == null || workDate == null) {
            return false;
        }

        Map<String, Object> existingTask = shiftMapper.selectContactTaskByRegisterAndWorkDate(registerId, workDate);
        if (existingTask != null && !existingTask.isEmpty()) {
            return true;
        }

        Map<String, Object> persisted = persistContactTask(
            registerId,
            departmentId,
            employeeId,
            workDate,
            priorityLevel
        );
        return persisted != null;
    }

    private Map<String, Object> persistContactTask(
        Long registerId,
        Long departmentId,
        Long employeeId,
        LocalDate workDate,
        String priorityLevel
    ) {
        String month = YearMonth.from(workDate).format(MONTH);
        Map<String, Object> plan = shiftMapper.selectPlanByDeptMonth(departmentId, month);
        if (plan == null || plan.isEmpty() || !isWritablePlanStatus(plan.get("status"))) {
            return null;
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
        taskPayload.put("priorityLevel", priorityLevel);
        shiftMapper.insertContactTask(taskPayload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shiftId", shiftId);
        result.put("planId", planId);
        return result;
    }

    private boolean isWritablePlanStatus(Object status) {
        String value = status != null ? String.valueOf(status) : "";
        return "draft".equals(value) || "published".equals(value);
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

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
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
