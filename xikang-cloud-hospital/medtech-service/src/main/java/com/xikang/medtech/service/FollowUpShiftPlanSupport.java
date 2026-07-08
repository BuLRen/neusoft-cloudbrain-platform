package com.xikang.medtech.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归一化 Dify / 规则排班输出，并在联系任务稀疏时按患者池回填。
 */
final class FollowUpShiftPlanSupport {

    private static final int DEFAULT_MAX_PATIENTS_PER_DAY = 8;
    private static final int SPARSE_EMPTY_RATIO_PERCENT = 30;

    private FollowUpShiftPlanSupport() {
    }

    static void normalizeShiftsOnly(
        Map<String, Object> payload,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        normalizeAndFillContactTasks(payload, staff, patients, false);
    }

    @SuppressWarnings("unchecked")
    static void normalizeAndFillContactTasks(
        Map<String, Object> payload,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        normalizeAndFillContactTasks(payload, staff, patients, true);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeAndFillContactTasks(
        Map<String, Object> payload,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients,
        boolean fillSparse
    ) {
        if (payload == null) {
            return;
        }
        Object rawShifts = payload.get("shifts");
        if (!(rawShifts instanceof List<?> list)) {
            payload.put("shifts", List.of());
            return;
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> shiftMap) {
                normalized.add(normalizeShift((Map<String, Object>) shiftMap));
            }
        }
        if (fillSparse) {
            fillSparseContactTasks(normalized, staff, patients);
        }
        payload.put("shifts", normalized);
    }

    static Map<String, Object> normalizeShift(Map<String, Object> shift) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("employee_id", firstValue(shift, "employee_id", "employeeId"));
        normalized.put("work_date", firstValue(shift, "work_date", "workDate"));
        normalized.put(
            "shift_type",
            firstValue(shift, "shift_type", "shiftType") != null
                ? firstValue(shift, "shift_type", "shiftType")
                : "full"
        );
        normalized.put("contact_tasks", normalizeTasks(shift.get("contact_tasks"), shift.get("contactTasks")));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeTasks(Object snakeTasks, Object camelTasks) {
        Object raw = snakeTasks != null ? snakeTasks : camelTasks;
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> taskMap)) {
                continue;
            }
            Map<String, Object> task = (Map<String, Object>) taskMap;
            Long registerId = toLong(firstValue(task, "register_id", "registerId"));
            if (registerId == null) {
                continue;
            }
            Object priority = firstValue(task, "priority", "priorityLevel");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("register_id", registerId);
            row.put("priority", priority != null ? String.valueOf(priority) : "normal");
            tasks.add(row);
        }
        return tasks;
    }

    private static void fillSparseContactTasks(
        List<Map<String, Object>> shifts,
        List<Map<String, Object>> staff,
        List<Map<String, Object>> patients
    ) {
        if (shifts.isEmpty() || patients == null || patients.isEmpty()) {
            return;
        }

        int emptyCount = 0;
        int totalTasks = 0;
        for (Map<String, Object> shift : shifts) {
            List<Map<String, Object>> tasks = getTasks(shift);
            totalTasks += tasks.size();
            if (tasks.isEmpty()) {
                emptyCount++;
            }
        }
        int emptyRatio = emptyCount * 100 / shifts.size();
        if (totalTasks > 0 && emptyRatio < SPARSE_EMPTY_RATIO_PERCENT) {
            return;
        }

        int maxPerDay = resolveMaxPatientsPerDay(staff);
        List<List<Map<String, Object>>> taskLists = new ArrayList<>();
        Map<Long, List<Integer>> shiftIndexesByEmployee = new HashMap<>();
        for (int i = 0; i < shifts.size(); i++) {
            taskLists.add(new ArrayList<>(getTasks(shifts.get(i))));
            Long employeeId = toLong(shifts.get(i).get("employee_id"));
            if (employeeId != null) {
                shiftIndexesByEmployee.computeIfAbsent(employeeId, ignored -> new ArrayList<>()).add(i);
            }
        }

        List<Map<String, Object>> sortedPatients = new ArrayList<>(patients);
        sortedPatients.sort(Comparator.comparingInt(FollowUpShiftPlanSupport::priorityRank).reversed());

        int roundRobin = 0;
        for (Map<String, Object> patient : sortedPatients) {
            Long registerId = toLong(firstValue(patient, "registerId", "register_id"));
            if (registerId == null) {
                continue;
            }
            Long monitorEmployeeId = toLong(firstValue(patient, "monitorEmployeeId", "monitor_employee_id"));
            String priority = String.valueOf(patient.getOrDefault("priority", "normal"));

            Integer slotIndex = findAssignableSlot(
                shifts,
                taskLists,
                shiftIndexesByEmployee,
                monitorEmployeeId,
                registerId,
                maxPerDay,
                roundRobin
            );
            if (slotIndex == null) {
                slotIndex = findAssignableSlot(
                    shifts,
                    taskLists,
                    shiftIndexesByEmployee,
                    null,
                    registerId,
                    maxPerDay,
                    roundRobin
                );
            }
            if (slotIndex == null) {
                continue;
            }

            Map<String, Object> task = new LinkedHashMap<>();
            task.put("register_id", registerId);
            task.put("priority", priority);
            taskLists.get(slotIndex).add(task);
            roundRobin = slotIndex + 1;
        }

        for (int i = 0; i < shifts.size(); i++) {
            shifts.get(i).put("contact_tasks", taskLists.get(i));
        }
    }

    private static Integer findAssignableSlot(
        List<Map<String, Object>> shifts,
        List<List<Map<String, Object>>> taskLists,
        Map<Long, List<Integer>> shiftIndexesByEmployee,
        Long preferredEmployeeId,
        Long registerId,
        int maxPerDay,
        int roundRobin
    ) {
        List<Integer> candidates = new ArrayList<>();
        if (preferredEmployeeId != null && shiftIndexesByEmployee.containsKey(preferredEmployeeId)) {
            candidates.addAll(shiftIndexesByEmployee.get(preferredEmployeeId));
        } else {
            for (int i = 0; i < shifts.size(); i++) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        for (int offset = 0; offset < candidates.size(); offset++) {
            int index = candidates.get((roundRobin + offset) % candidates.size());
            if (canAssign(taskLists.get(index), registerId, maxPerDay)) {
                return index;
            }
        }
        return null;
    }

    private static boolean canAssign(List<Map<String, Object>> tasks, Long registerId, int maxPerDay) {
        if (tasks.size() >= maxPerDay) {
            return false;
        }
        for (Map<String, Object> task : tasks) {
            if (registerId.equals(toLong(task.get("register_id")))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getTasks(Map<String, Object> shift) {
        Object tasks = shift.get("contact_tasks");
        if (tasks instanceof List<?> list) {
            return (List<Map<String, Object>>) tasks;
        }
        return List.of();
    }

    private static int resolveMaxPatientsPerDay(List<Map<String, Object>> staff) {
        if (staff == null || staff.isEmpty()) {
            return DEFAULT_MAX_PATIENTS_PER_DAY;
        }
        Object configured = staff.get(0).get("max_patients_per_day");
        if (configured == null) {
            configured = staff.get(0).get("maxPatientsPerDay");
        }
        Long value = toLong(configured);
        return value != null && value > 0 ? value.intValue() : DEFAULT_MAX_PATIENTS_PER_DAY;
    }

    private static int priorityRank(Map<String, Object> patient) {
        String priority = String.valueOf(patient.getOrDefault("priority", "normal"));
        return switch (priority) {
            case "critical" -> 3;
            case "high" -> 2;
            default -> 1;
        };
    }

    private static Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
