package com.xikang.medtech.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 按患者病情间隔与监视医生生成不规则月度联系排班（规则降级）。
 */
public final class FollowUpIntervalScheduleEngine {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_MAX_PATIENTS_PER_DAY = 8;

    private FollowUpIntervalScheduleEngine() {
    }

    public static Map<String, Object> buildShifts(
        String month,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients,
        Map<String, Object> rules
    ) {
        YearMonth yearMonth = YearMonth.parse(month, MONTH);
        int maxPerDay = resolveMaxPatientsPerDay(staff, rules);
        List<LocalDate> schedulableDates = FollowUpContactScheduleHelper.schedulableDatesInMonth(yearMonth);

        Map<String, Map<String, Object>> shiftIndex = new TreeMap<>();
        Map<String, Integer> dayLoad = new HashMap<>();

        List<Map<String, Object>> assignedPatients = patients.stream()
            .filter(p -> toLong(p.get("monitorEmployeeId")) != null)
            .sorted(Comparator.comparingInt(FollowUpIntervalScheduleEngine::priorityRank).reversed())
            .toList();

        for (Map<String, Object> patient : assignedPatients) {
            Long registerId = toLong(patient.get("registerId"));
            Long monitorId = toLong(patient.get("monitorEmployeeId"));
            if (registerId == null || monitorId == null) {
                continue;
            }
            String priority = patient.get("priority") != null
                ? String.valueOf(patient.get("priority"))
                : "normal";
            int intervalDays = FollowUpContactScheduleHelper.interviewIntervalDays(
                priority,
                patient.get("interviewIntervalDays")
            );
            LocalDate lastContact = FollowUpContactScheduleHelper.parseDate(patient.get("lastContactDate"));
            LocalDate anchor = FollowUpContactScheduleHelper.parseDate(patient.get("enrolledAt"));
            if (anchor == null) {
                anchor = FollowUpContactScheduleHelper.firstContactAfterVisit(
                    FollowUpContactScheduleHelper.parseDate(patient.get("visitEndedAt"))
                );
            }

            List<LocalDate> contactDates = FollowUpContactScheduleHelper.contactDatesInMonth(
                lastContact,
                anchor,
                intervalDays,
                yearMonth
            );
            for (LocalDate workDate : contactDates) {
                LocalDate assignedDate = workDate;
                String shiftKey = monitorId + "|" + assignedDate;
                if (dayLoad.getOrDefault(shiftKey, 0) >= maxPerDay) {
                    LocalDate deferred = deferToNextSlot(monitorId, assignedDate, schedulableDates, dayLoad, maxPerDay);
                    if (deferred == null) {
                        continue;
                    }
                    assignedDate = deferred;
                    shiftKey = monitorId + "|" + assignedDate;
                }
                final LocalDate slotDate = assignedDate;
                Map<String, Object> shift = shiftIndex.computeIfAbsent(shiftKey, key -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employee_id", monitorId);
                    row.put("work_date", slotDate.toString());
                    row.put("shift_type", "full");
                    row.put("contact_tasks", new ArrayList<Map<String, Object>>());
                    return row;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) shift.get("contact_tasks");
                if (tasks.stream().anyMatch(t -> registerId.equals(toLong(t.get("register_id"))))) {
                    continue;
                }
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("register_id", registerId);
                task.put("priority", priority);
                tasks.add(task);
                dayLoad.merge(shiftKey, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> shifts = new ArrayList<>(shiftIndex.values());
        shifts.sort(Comparator.comparing(s -> String.valueOf(s.get("work_date"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shifts", shifts);
        result.put(
            "summary",
            month + " 间隔排班共 " + shifts.size() + " 个班次，覆盖 " + assignedPatients.size() + " 名已分配监视患者"
        );
        return result;
    }

    private static LocalDate deferToNextSlot(
        Long monitorId,
        LocalDate from,
        List<LocalDate> schedulableDates,
        Map<String, Integer> dayLoad,
        int maxPerDay
    ) {
        for (LocalDate date : schedulableDates) {
            if (date.isBefore(from)) {
                continue;
            }
            String key = monitorId + "|" + date;
            if (dayLoad.getOrDefault(key, 0) < maxPerDay) {
                return date;
            }
        }
        return null;
    }

    private static int resolveMaxPatientsPerDay(List<Map<String, Object>> staff, Map<String, Object> rules) {
        if (rules != null && rules.get("max_patients_per_day") instanceof Number number) {
            int value = number.intValue();
            if (value > 0) {
                return value;
            }
        }
        if (staff != null && !staff.isEmpty()) {
            Object configured = staff.get(0).get("max_patients_per_day");
            if (configured == null) {
                configured = staff.get(0).get("maxPatientsPerDay");
            }
            Long value = toLong(configured);
            if (value != null && value > 0) {
                return value.intValue();
            }
        }
        return DEFAULT_MAX_PATIENTS_PER_DAY;
    }

    private static int priorityRank(Map<String, Object> patient) {
        String priority = patient.get("priority") != null
            ? String.valueOf(patient.get("priority"))
            : "normal";
        return switch (priority.toLowerCase()) {
            case "critical" -> 3;
            case "high" -> 2;
            default -> 1;
        };
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
