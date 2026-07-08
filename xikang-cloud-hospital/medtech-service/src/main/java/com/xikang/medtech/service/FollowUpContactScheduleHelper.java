package com.xikang.medtech.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 随访联系日计算：首次入队（看诊后工作日）与按病情间隔的后续联系日。
 */
public final class FollowUpContactScheduleHelper {

    public static final int FIRST_CONTACT_BUSINESS_DAYS_AFTER_VISIT = 14;

    private FollowUpContactScheduleHelper() {
    }

    public static int interviewIntervalDays(String priority, Object configured) {
        if (configured instanceof Number number) {
            int value = number.intValue();
            if (value > 0) {
                return value;
            }
        }
        if (configured != null) {
            try {
                int value = Integer.parseInt(String.valueOf(configured));
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return switch (priority != null ? priority.toLowerCase() : "normal") {
            case "critical" -> 3;
            case "high" -> 7;
            default -> 14;
        };
    }

    public static LocalDate firstContactAfterVisit(LocalDate visitEndedDate) {
        LocalDate start = visitEndedDate != null ? visitEndedDate : LocalDate.now();
        return addBusinessDays(start, FIRST_CONTACT_BUSINESS_DAYS_AFTER_VISIT);
    }

    public static LocalDate addBusinessDays(LocalDate start, int businessDays) {
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

    public static boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    public static List<LocalDate> schedulableDatesInMonth(YearMonth yearMonth) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(yearMonth.atEndOfMonth()); date = date.plusDays(1)) {
            if (isWeekday(date)) {
                dates.add(date);
            }
        }
        return dates;
    }

    /**
     * 从上次联系（或锚点日）起，按间隔生成本月内应联系日期。
     */
    public static List<LocalDate> contactDatesInMonth(
        LocalDate lastContactDate,
        LocalDate anchorDate,
        int intervalDays,
        YearMonth yearMonth
    ) {
        int interval = Math.max(1, intervalDays);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        LocalDate cursor;
        if (lastContactDate != null) {
            cursor = lastContactDate.plusDays(interval);
        } else if (anchorDate != null) {
            cursor = anchorDate;
        } else {
            cursor = monthStart;
        }

        while (cursor.isBefore(monthStart)) {
            cursor = cursor.plusDays(interval);
        }

        List<LocalDate> result = new ArrayList<>();
        while (!cursor.isAfter(monthEnd)) {
            if (isWeekday(cursor)) {
                result.add(cursor);
            }
            cursor = cursor.plusDays(interval);
        }
        return result;
    }

    public static int computeContactScore(
        String priority,
        LocalDate lastContactDate,
        LocalDate deadlineDate,
        LocalDate referenceDate
    ) {
        String pr = priority != null ? priority.toLowerCase() : "normal";
        int score = switch (pr) {
            case "critical" -> 100;
            case "high" -> 60;
            default -> 20;
        };

        if (deadlineDate != null && referenceDate != null) {
            long daysToDeadline = ChronoUnit.DAYS.between(referenceDate, deadlineDate);
            if (daysToDeadline <= 30) {
                score += 80;
            } else if (daysToDeadline <= 60) {
                score += 50;
            } else if (daysToDeadline <= 90) {
                score += 30;
            }
        }

        if (lastContactDate == null) {
            score += 40;
        } else if (referenceDate != null) {
            long daysSince = ChronoUnit.DAYS.between(lastContactDate, referenceDate);
            if (daysSince >= 3) {
                score += 30;
            } else if (daysSince >= 1) {
                score += 10;
            }
        }
        return score;
    }

    public static Map<String, Object> toScoredPatientPayload(
        Map<String, Object> patient,
        String month,
        Map<String, Object> rules
    ) {
        String priority = patient.get("priority") != null
            ? String.valueOf(patient.get("priority"))
            : "normal";
        LocalDate lastContact = parseDate(patient.get("lastContactDate"));
        LocalDate deadline = parseDate(patient.get("deadlineDate"));
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate reference = yearMonth.atDay(15);

        int intervalDays = interviewIntervalDays(priority, patient.get("interviewIntervalDays"));
        int score = computeContactScore(priority, lastContact, deadline, reference);

        Map<String, Object> row = new LinkedHashMap<>();
        Object registerId = patient.get("registerId");
        row.put("registerId", registerId);
        row.put("register_id", registerId);
        row.put("priority", priority);
        row.put("monitorEmployeeId", patient.get("monitorEmployeeId"));
        row.put("monitor_employee_id", patient.get("monitorEmployeeId"));
        row.put("lastContactDate", lastContact != null ? lastContact.toString() : null);
        row.put("deadlineDate", deadline != null ? deadline.toString() : null);
        row.put("interview_interval_days", intervalDays);
        row.put("contact_score", score);
        if (lastContact != null && reference != null) {
            row.put("days_since_contact", ChronoUnit.DAYS.between(lastContact, reference));
        } else {
            row.put("days_since_contact", 999);
        }
        if (deadline != null && reference != null) {
            row.put("days_to_deadline", ChronoUnit.DAYS.between(reference, deadline));
        }
        int minInterval = rules != null && rules.get("min_contact_interval_days") instanceof Number number
            ? Math.max(1, number.intValue())
            : 1;
        row.put("min_contact_interval_days", Math.max(minInterval, intervalDays));
        return row;
    }

    public static LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
    }
}
