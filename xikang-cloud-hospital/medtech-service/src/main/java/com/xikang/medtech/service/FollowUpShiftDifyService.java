package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.ai.DifyAiProperties;
import com.xikang.medtech.ai.DifyWorkflowClient;
import com.xikang.medtech.ai.DifyWorkflowRunResult;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpShiftDifyService {

    public static final String SOURCE_DIFY = "dify";
    public static final String SOURCE_RULE_BASED = "rule_based";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DifyWorkflowClient difyWorkflowClient;
    private final DifyAiProperties difyAiProperties;
    private final FollowUpShiftMapper shiftMapper;

    public Map<String, Object> generateShifts(Long departmentId, String departmentName, String month) {
        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        if (staff.isEmpty()) {
            throw new RuntimeException("当前科室暂无随访人员");
        }
        List<Map<String, Object>> patients = shiftMapper.selectPatientsForShiftPlanning(departmentId);

        Map<String, Object> inputs = buildWorkflowInputs(departmentId, departmentName, month, staff, patients);
        String scheduleNote = difyAiProperties.describeFollowUpShiftScheduleDisabledReason();

        if (difyAiProperties.isFollowUpShiftScheduleEnabled()) {
            try {
                log.info(
                    "调用 Dify 随访排班工作流 dept={} month={} staff={} patients={}",
                    departmentId,
                    month,
                    staff.size(),
                    patients.size()
                );
                DifyWorkflowRunResult result = difyWorkflowClient.runFollowUpShiftScheduleBlocking(
                    inputs,
                    "medtech-shift-" + departmentId,
                    "shift-" + departmentId + "-" + month
                );
                Map<String, Object> parsed = parseDifyOutput(result.getOutputs());
                validateShiftPayload(parsed);
                normalizeAndFill(staff, patients, parsed);
                parsed.put("source", SOURCE_DIFY);
                attachPlanningMeta(parsed, patients.size(), "");
                log.info(
                    "Dify 随访排班成功 dept={} month={} shifts={} runId={}",
                    departmentId,
                    month,
                    countShifts(parsed),
                    result.getWorkflowRunId()
                );
                return parsed;
            } catch (Exception ex) {
                log.warn("Dify shift schedule failed, fallback to rule-based: {}", ex.getMessage());
                scheduleNote = scheduleNote.isBlank()
                    ? "Dify 调用失败: " + ex.getMessage()
                    : scheduleNote + "；调用失败: " + ex.getMessage();
            }
        } else {
            log.info(
                "Dify 随访排班未启用（{}），使用规则排班 dept={} month={} patients={}",
                scheduleNote.isBlank() ? "未知原因" : scheduleNote,
                departmentId,
                month,
                patients.size()
            );
        }

        Map<String, Object> rules = defaultRules();
        Map<String, Object> fallback = FollowUpIntervalScheduleEngine.buildShifts(month, staff, patients, rules);
        FollowUpShiftPlanSupport.normalizeShiftsOnly(fallback, staff, patients);
        fallback.put("source", SOURCE_RULE_BASED);
        attachPlanningMeta(fallback, patients.size(), scheduleNote);
        return fallback;
    }

    private void attachPlanningMeta(Map<String, Object> payload, int patientCount, String scheduleNote) {
        payload.put("patientCount", patientCount);
        if (scheduleNote != null && !scheduleNote.isBlank()) {
            payload.put("scheduleNote", scheduleNote);
        }
    }

    private void normalizeAndFill(
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients,
        Map<String, Object> payload
    ) {
        FollowUpShiftPlanSupport.normalizeAndFillContactTasks(payload, staff, patients);
        if (patients.isEmpty()) {
            log.warn("随访排班患者池为空 dept month 将只生成空班次，请先在随访工作台纳入患者并分配监视医生");
        } else {
            log.info(
                "随访排班归一化完成 shifts={} patients={} totalTasks={}",
                countShifts(payload),
                patients.size(),
                countTasks(payload)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private int countTasks(Map<String, Object> payload) {
        Object shifts = payload.get("shifts");
        if (!(shifts instanceof List<?> list)) {
            return 0;
        }
        int total = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> shift) {
                Object tasks = ((Map<String, Object>) shift).get("contact_tasks");
                if (tasks instanceof List<?> taskList) {
                    total += taskList.size();
                }
            }
        }
        return total;
    }

    private Map<String, Object> buildWorkflowInputs(
        Long departmentId,
        String departmentName,
        String month,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        Map<String, Object> rules = defaultRules();
        YearMonth yearMonth = YearMonth.parse(month, MONTH);
        List<String> workDates = FollowUpContactScheduleHelper.schedulableDatesInMonth(yearMonth).stream()
            .map(LocalDate::toString)
            .toList();
        List<Map<String, Object>> staffList = staff.stream().map(this::toStaffPayload).toList();
        List<Map<String, Object>> scoredPatients = patients.stream()
            .map(patient -> FollowUpContactScheduleHelper.toScoredPatientPayload(patient, month, rules))
            .toList();

        Map<String, Object> planningContext = new LinkedHashMap<>();
        planningContext.put("department_id", departmentId);
        planningContext.put("department_name", departmentName != null ? departmentName : "");
        planningContext.put("month", month);
        planningContext.put("staff_count", staff.size());
        planningContext.put("patient_count", patients.size());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("department_id", String.valueOf(departmentId));
        inputs.put("department_name", departmentName != null ? departmentName : "");
        inputs.put("month", month);
        inputs.put("staff_json", toJson(staffList));
        inputs.put("patients_json", toJson(patients));
        inputs.put("rules_json", toJson(rules));
        inputs.put("holidays_json", "[]");
        inputs.put("planning_context_json", toJson(planningContext));
        inputs.put("work_dates_json", toJson(workDates));
        inputs.put("schedulable_dates_json", toJson(workDates));
        inputs.put("staff_list_json", toJson(staffList));
        inputs.put("patients_list_json", toJson(patients));
        inputs.put("rules_obj_json", toJson(rules));
        inputs.put("scored_patients_json", toJson(scoredPatients));
        return inputs;
    }

    private Map<String, Object> defaultRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("workdays_per_week", 5);
        rules.put("min_contact_interval_days", 1);
        rules.put("deadline_days", 180);
        rules.put("max_patients_per_day", 8);
        rules.put("max_shift_imbalance", 2);
        return rules;
    }

    private Map<String, Object> toStaffPayload(Map<String, Object> staff) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", staff.get("id"));
        row.put("name", staff.get("name"));
        row.put("max_patients_per_day", 8);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDifyOutput(Map<String, Object> outputs) throws Exception {
        if (outputs == null || outputs.isEmpty()) {
            throw new RuntimeException("Dify 未返回有效输出");
        }

        Object raw = outputs.get("validated_shifts_json");
        if (raw == null) {
            raw = outputs.get("validatedShiftsJson");
        }
        if (raw == null) {
            raw = outputs.get("text");
        }

        Map<String, Object> parsed;
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                throw new RuntimeException("Dify 返回空的 validated_shifts_json");
            }
            parsed = MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
        } else if (raw instanceof Map<?, ?> map) {
            parsed = (Map<String, Object>) map;
        } else {
            throw new RuntimeException("无法解析 Dify 排班输出，outputs keys=" + outputs.keySet());
        }

        if (parsed.containsKey("shifts")) {
            return parsed;
        }

        Object nested = parsed.get("validated_shifts_json");
        if (nested == null) {
            nested = parsed.get("validatedShiftsJson");
        }
        if (nested instanceof String nestedText) {
            return MAPPER.readValue(nestedText.trim(), new TypeReference<Map<String, Object>>() {});
        }
        if (nested instanceof Map<?, ?> nestedMap) {
            return (Map<String, Object>) nestedMap;
        }

        throw new RuntimeException("Dify 输出缺少 shifts 数组");
    }

    @SuppressWarnings("unchecked")
    private void validateShiftPayload(Map<String, Object> payload) {
        Object shifts = payload.get("shifts");
        if (!(shifts instanceof List<?> list) || list.isEmpty()) {
            throw new RuntimeException("Dify 返回的 shifts 为空");
        }
    }

    @SuppressWarnings("unchecked")
    private int countShifts(Map<String, Object> payload) {
        Object shifts = payload.get("shifts");
        return shifts instanceof List<?> list ? list.size() : 0;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("JSON 序列化失败");
        }
    }
}
