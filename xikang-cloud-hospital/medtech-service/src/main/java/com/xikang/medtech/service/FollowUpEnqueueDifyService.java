package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.ai.DifyAiProperties;
import com.xikang.medtech.ai.DifyWorkflowClient;
import com.xikang.medtech.ai.DifyWorkflowRunResult;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpEnqueueDifyService {

    public static final String SOURCE_DIFY = "dify";
    public static final String SOURCE_RULE_BASED = "rule_based";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DifyWorkflowClient difyWorkflowClient;
    private final DifyAiProperties difyAiProperties;
    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpClinicalSnapshotService clinicalSnapshotService;

    public Map<String, Object> planEnqueue(
        Long registerId,
        LocalDate visitEndedDate,
        Long departmentId,
        String departmentName,
        String priorityLevel,
        Long preferMonitorEmployeeId
    ) {
        Map<String, Object> inputs = buildWorkflowInputs(
            registerId,
            visitEndedDate,
            departmentId,
            departmentName,
            priorityLevel,
            preferMonitorEmployeeId
        );

        if (difyAiProperties.isFollowUpEnqueueEnabled()) {
            try {
                log.info(
                    "调用 Dify 随访入队工作流 registerId={} dept={} priority={}",
                    registerId,
                    departmentId,
                    priorityLevel
                );
                DifyWorkflowRunResult result = difyWorkflowClient.runFollowUpEnqueueBlocking(
                    inputs,
                    "medtech-enqueue-" + registerId,
                    "enqueue-" + registerId
                );
                Map<String, Object> parsed = parseEnqueueOutput(result.getOutputs());
                validateEnqueuePayload(parsed, registerId);
                parsed.put("source", SOURCE_DIFY);
                parsed.put("difyWorkflowRunId", result.getWorkflowRunId());
                log.info(
                    "Dify 随访入队成功 registerId={} workDate={} employeeId={} runId={}",
                    registerId,
                    parsed.get("work_date"),
                    parsed.get("employee_id"),
                    result.getWorkflowRunId()
                );
                return parsed;
            } catch (Exception ex) {
                log.warn("Dify enqueue failed registerId={}, fallback: {}", registerId, ex.getMessage());
            }
        }

        Map<String, Object> fallback = buildRuleBasedEnqueue(
            registerId,
            visitEndedDate,
            departmentId,
            priorityLevel,
            preferMonitorEmployeeId
        );
        fallback.put("source", SOURCE_RULE_BASED);
        return fallback;
    }

    private Map<String, Object> buildWorkflowInputs(
        Long registerId,
        LocalDate visitEndedDate,
        Long departmentId,
        String departmentName,
        String priorityLevel,
        Long preferMonitorEmployeeId
    ) {
        Map<String, Object> patientBrief = dashboardMapper.selectMonitoringByRegisterId(registerId);
        Map<String, Object> snapshot = clinicalSnapshotService.getOrSyncLastVisit(registerId);
        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        LocalDate from = YearMonth.from(visitEndedDate).atDay(1);
        LocalDate to = YearMonth.from(visitEndedDate).plusMonths(1).atEndOfMonth();
        List<Map<String, Object>> existingShifts = shiftMapper.selectShiftsWithTaskCounts(departmentId, from, to);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("register_id", String.valueOf(registerId));
        inputs.put("visit_ended_at", visitEndedDate + "T12:00:00");
        inputs.put("department_id", String.valueOf(departmentId));
        inputs.put("department_name", departmentName != null ? departmentName : "");
        inputs.put("priority_level", priorityLevel != null ? priorityLevel : "normal");
        inputs.put("patient_json", toJson(buildPatientJson(registerId, patientBrief, snapshot)));
        inputs.put("prescription_json", toJson(snapshot.get("prescriptionSummary")));
        inputs.put("staff_json", toJson(staff));
        inputs.put("existing_shifts_json", toJson(existingShifts));
        inputs.put("rules_json", toJson(Map.of(
            "min_days_after_visit", 14,
            "max_patients_per_day", 8,
            "prefer_monitor_employee_id", preferMonitorEmployeeId != null ? preferMonitorEmployeeId : ""
        )));
        return inputs;
    }

    private Map<String, Object> buildPatientJson(
        Long registerId,
        Map<String, Object> brief,
        Map<String, Object> snapshot
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("register_id", registerId);
        row.put("diagnosis_summary", snapshot.get("diagnosisSummary"));
        row.put("chief_complaint", snapshot.get("chiefComplaint"));
        if (brief != null) {
            row.put("monitoring_employee_id", brief.get("monitoringEmployeeId"));
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEnqueueOutput(Map<String, Object> outputs) throws Exception {
        if (outputs == null || outputs.isEmpty()) {
            throw new RuntimeException("Dify 未返回有效输出");
        }
        Object raw = outputs.get("enqueue_result_json");
        if (raw == null) {
            raw = outputs.get("structured_output");
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> parsed = MAPPER.convertValue(map, new TypeReference<>() {});
            if (parsed.containsKey("register_id") || parsed.containsKey("registerId")) {
                return normalizeEnqueueKeys(parsed);
            }
        }
        if (raw instanceof String text && text.trim().startsWith("{")) {
            Map<String, Object> parsed = MAPPER.readValue(text, new TypeReference<>() {});
            return normalizeEnqueueKeys(parsed);
        }
        return normalizeEnqueueKeys(outputs);
    }

    private Map<String, Object> normalizeEnqueueKeys(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("register_id", raw.get("register_id") != null ? raw.get("register_id") : raw.get("registerId"));
        row.put("employee_id", raw.get("employee_id") != null ? raw.get("employee_id") : raw.get("employeeId"));
        row.put("work_date", raw.get("work_date") != null ? raw.get("work_date") : raw.get("workDate"));
        row.put("priority", raw.get("priority") != null ? raw.get("priority") : raw.get("priorityLevel"));
        row.put("shift_plan_id", raw.get("shift_plan_id") != null ? raw.get("shift_plan_id") : raw.get("shiftPlanId"));
        row.put("summary", raw.get("summary"));
        return row;
    }

    private void validateEnqueuePayload(Map<String, Object> payload, Long registerId) {
        if (payload.get("register_id") == null) {
            payload.put("register_id", registerId);
        }
        if (payload.get("employee_id") == null || payload.get("work_date") == null) {
            throw new RuntimeException("Dify enqueue 输出缺少 employee_id 或 work_date");
        }
    }

    private Map<String, Object> buildRuleBasedEnqueue(
        Long registerId,
        LocalDate visitEndedDate,
        Long departmentId,
        String priorityLevel,
        Long preferMonitorEmployeeId
    ) {
        LocalDate earliest = addBusinessDays(visitEndedDate, 14);
        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        Long employeeId = preferMonitorEmployeeId;
        if (employeeId == null && !staff.isEmpty()) {
            employeeId = toLong(staff.get(0).get("id"));
        }
        if (employeeId == null) {
            throw new RuntimeException("科室无随访护士，无法规则入队");
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("register_id", registerId);
        row.put("employee_id", employeeId);
        row.put("work_date", earliest.toString());
        row.put("priority", priorityLevel != null ? priorityLevel : "normal");
        row.put("summary", "看诊后第14个工作日首次联系（规则降级）");
        return row;
    }

    static LocalDate addBusinessDays(LocalDate start, int businessDays) {
        LocalDate date = start;
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            if (date.getDayOfWeek().getValue() <= 5) {
                added++;
            }
        }
        return date;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value != null ? value : List.of());
        } catch (Exception ex) {
            return "[]";
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
