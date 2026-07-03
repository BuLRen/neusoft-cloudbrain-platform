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
            "max_patients_per_day", 8
        )));
        inputs.put("holidays_json", "[]");

        if (difyAiProperties.isFollowUpShiftScheduleEnabled()) {
            try {
                DifyWorkflowRunResult result = difyWorkflowClient.runFollowUpShiftScheduleBlocking(
                    inputs,
                    "medtech-shift-" + departmentId,
                    "shift-" + month
                );
                return parseDifyOutput(result.getOutputs());
            } catch (Exception ex) {
                log.warn("Dify shift schedule failed, fallback to rule-based: {}", ex.getMessage());
            }
        }
        return buildRuleBasedShifts(month, staff, patients);
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
            raw = outputs.get("text");
        }
        if (raw instanceof String text) {
            return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
        }
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new RuntimeException("无法解析 Dify 排班输出");
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
