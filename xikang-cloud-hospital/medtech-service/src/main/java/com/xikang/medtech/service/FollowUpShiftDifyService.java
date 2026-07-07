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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
                parsed.put("source", SOURCE_DIFY);
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
            }
        } else {
            log.info("Dify 随访排班未启用，使用规则排班 dept={} month={}", departmentId, month);
        }

        Map<String, Object> fallback = buildRuleBasedShifts(month, staff, patients);
        fallback.put("source", SOURCE_RULE_BASED);
        return fallback;
    }

    private Map<String, Object> buildWorkflowInputs(
        Long departmentId,
        String departmentName,
        String month,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("department_id", String.valueOf(departmentId));
        inputs.put("department_name", departmentName != null ? departmentName : "");
        inputs.put("month", month);
        inputs.put("staff_json", toJson(staff.stream().map(this::toStaffPayload).toList()));
        inputs.put("patients_json", toJson(patients));
        inputs.put("rules_json", toJson(Map.of(
            "workdays_per_week", 5,
            "min_contact_interval_days", 1,
            "deadline_days", 180,
            "max_patients_per_day", 8,
            "max_shift_imbalance", 2
        )));
        inputs.put("holidays_json", "[]");
        return inputs;
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

    private Map<String, Object> buildRuleBasedShifts(
        String month,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        YearMonth yearMonth = YearMonth.parse(month, MONTH);
        List<Map<String, Object>> shifts = new ArrayList<>();
        int staffIndex = 0;
        int patientIndex = 0;

        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(yearMonth.atEndOfMonth()); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            Map<String, Object> member = staff.get(staffIndex % staff.size());
            staffIndex++;

            List<Map<String, Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 5 && !patients.isEmpty(); i++) {
                Map<String, Object> patient = patients.get(patientIndex % patients.size());
                patientIndex++;
                tasks.add(Map.of(
                    "register_id", patient.get("registerId"),
                    "priority", patient.getOrDefault("priority", "normal")
                ));
            }

            Map<String, Object> shift = new LinkedHashMap<>();
            shift.put("employee_id", member.get("id"));
            shift.put("work_date", date.toString());
            shift.put("shift_type", "full");
            shift.put("contact_tasks", tasks);
            shifts.add(shift);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shifts", shifts);
        result.put("summary", month + " 规则排班共 " + shifts.size() + " 个班次");
        return result;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("JSON 序列化失败");
        }
    }
}
