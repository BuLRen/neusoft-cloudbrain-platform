package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpDashboardService {

    private final FollowUpDashboardMapper followUpDashboardMapper;
    private final FollowUpOutcomeMapper followUpOutcomeMapper;
    private final MedtechService medtechService;
    private final FollowUpProperties followUpProperties;
    private final FollowUpEnrollmentSyncService followUpEnrollmentSyncService;
    private final FollowUpHistoryService historyService;
    private final FollowUpShiftEnqueueService shiftEnqueueService;
    private final FollowUpPriorityScorer priorityScorer;

    public Map<String, Object> getContext(LocalDate targetDate, Long departmentIdOverride) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        Long departmentId = resolveDepartmentId(departmentIdOverride);

        Map<String, Object> context = new LinkedHashMap<>(medtechService.getCurrentProfile());
        context.put("targetDate", date.toString());
        context.put("effectiveDepartmentId", departmentId);
        context.put("dataSource", followUpProperties.getDataSource());

        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        if (employeeId != null) {
            Map<String, Object> employee = followUpDashboardMapper.selectEmployeeBrief(employeeId);
            if (employee != null) {
                context.put("employeeRealName", employee.get("realName"));
            }
        }

        Map<String, Object> stats = followUpDashboardMapper.selectDashboardStats(
            departmentId,
            date,
            followUpProperties.includeUnenrolledEligible(),
            followUpProperties.isDemoMode()
        );
        if (stats != null) {
            context.put("stats", stats);
        }
        return context;
    }

    public List<Map<String, Object>> listPatients(LocalDate targetDate, Long departmentIdOverride) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        Long departmentId = resolveDepartmentId(departmentIdOverride);
        Long currentEmployeeId = MedtechAuthContext.employeeIdOrNull();

        List<Map<String, Object>> patients = followUpDashboardMapper.selectDashboardPatients(
            departmentId,
            date,
            followUpProperties.includeUnenrolledEligible(),
            followUpProperties.isDemoMode()
        );
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> patient : patients) {
            enriched.add(enrichPatient(patient, date, currentEmployeeId));
        }
        return enriched;
    }

    public List<Map<String, Object>> listMyMonitoredPatients(LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        if (employeeId == null) {
            throw new BusinessException(403, "当前账号未绑定员工");
        }
        List<Map<String, Object>> patients = followUpDashboardMapper.selectMyMonitoredDashboardPatients(
            employeeId,
            date
        );
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> patient : patients) {
            Map<String, Object> row = enrichPatient(patient, date, employeeId);
            row.put("isMine", true);
            enriched.add(row);
        }
        return enriched;
    }

    public Map<String, Object> claimMonitoring(Long registerId) {
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        throw new BusinessException(
            "监视医生须由管理员分配；如需调换负责医生，请在工作台提交「申请调换监视」"
        );
    }

    @Transactional
    public Map<String, Object> releaseMonitoring(Long registerId) {
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        boolean forceRelease = MedtechAuthContext.isAdminAllAccess();
        if (employeeId == null && !forceRelease) {
            throw new BusinessException(403, "当前账号未绑定员工");
        }
        followUpDashboardMapper.releaseMonitoring(registerId, employeeId, forceRelease);
        followUpDashboardMapper.releaseMonitoringProfile(registerId, employeeId, forceRelease);
        return Map.of("registerId", registerId, "released", true);
    }

    @Transactional
    public Map<String, Object> enrollPatient(Map<String, Object> request) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可手动纳入随访；看诊结束患者由系统自动纳入");
        }
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }

        if (!followUpDashboardMapper.isEligiblePatient(registerId)) {
            throw new BusinessException("该挂号记录不符合随访纳入条件（需看诊结束且有诊断或随访数据）");
        }

        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        if (profile == null || profile.isEmpty()) {
            throw new BusinessException("未找到该挂号患者");
        }

        Long departmentId = resolveDepartmentId(toLong(request.get("departmentId")));
        if (departmentId == null) {
            throw new BusinessException("无法确定在管科室");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("priorityLevel", request.get("priorityLevel"));
        payload.put("interviewIntervalDays", request.get("interviewIntervalDays"));
        payload.put("observationIntervalDays", request.get("observationIntervalDays"));
        payload.put("enrolledBy", MedtechAuthContext.employeeIdOrNull());

        followUpDashboardMapper.upsertPatientProfile(payload);

        if (followUpProperties.preferEnrollmentTable()) {
            followUpEnrollmentSyncService.trySyncEnrollment(payload);
        }

        Map<String, Object> result = new LinkedHashMap<>(followUpDashboardMapper.selectEnrollmentByRegisterId(registerId));
        result.put("realName", profile.get("realName"));
        result.put("caseNumber", profile.get("caseNumber"));
        result.put("enrolled", true);

        FollowUpPriorityResult priority = priorityScorer.score(registerId);
        Long preferMonitor = toLong(result.get("monitoringEmployeeId"));
        shiftEnqueueService.enqueueAsync(
            registerId,
            LocalDateTime.now(),
            departmentId,
            priority.getPriorityLevel(),
            preferMonitor
        );
        result.put("enqueueSubmitted", true);
        return result;
    }

    public List<Map<String, Object>> listSchedules(LocalDate from, LocalDate to, Long departmentIdOverride) {
        if (from == null || to == null) {
            throw new BusinessException("from 与 to 不能为空");
        }
        Long departmentId = resolveDepartmentId(departmentIdOverride);
        return followUpDashboardMapper.selectDaySchedules(departmentId, from, to);
    }

    @Transactional
    public Map<String, Object> createSchedule(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        LocalDate scheduleDate = parseDate(request.get("scheduleDate"));
        String itemType = request.get("itemType") != null ? String.valueOf(request.get("itemType")) : "interview";
        String title = request.get("title") != null ? String.valueOf(request.get("title")).trim() : null;

        if (scheduleDate == null) {
            throw new BusinessException("scheduleDate 不能为空");
        }

        Long departmentId = resolveDepartmentId(toLong(request.get("departmentId")));
        if (departmentId == null && registerId != null) {
            departmentId = followUpDashboardMapper.selectRegisterDepartmentId(registerId);
        }
        if (departmentId == null) {
            throw new BusinessException("无法确定科室");
        }

        if ("interview".equals(itemType) && registerId != null) {
            Map<String, Object> existing = followUpDashboardMapper.selectExistingInterviewSchedule(registerId, scheduleDate);
            if (existing != null && !existing.isEmpty()) {
                throw new BusinessException("该患者当日已有访谈安排");
            }
            if (title == null || title.isEmpty()) {
                Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
                String name = profile != null ? String.valueOf(profile.getOrDefault("realName", "患者")) : "患者";
                title = name + " · 随访访谈";
            }
        }

        if (title == null || title.isEmpty()) {
            title = "自定义事项";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("scheduleDate", scheduleDate);
        payload.put("itemType", itemType);
        payload.put("title", title);
        payload.put("status", "planned");
        payload.put("createdBy", MedtechAuthContext.employeeIdOrNull());

        followUpDashboardMapper.insertDaySchedule(payload);
        return followUpDashboardMapper.selectDayScheduleById(toLong(payload.get("id")));
    }

    @Transactional
    public Map<String, Object> updateScheduleStatus(Long id, String status) {
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        if (!List.of("planned", "completed", "cancelled").contains(status)) {
            throw new BusinessException("无效的状态");
        }
        Map<String, Object> existing = followUpDashboardMapper.selectDayScheduleById(id);
        if (existing == null || existing.isEmpty()) {
            throw new BusinessException("日程不存在");
        }
        followUpDashboardMapper.updateDayScheduleStatus(id, status);
        return followUpDashboardMapper.selectDayScheduleById(id);
    }

    @Transactional
    public Map<String, Object> confirmObservation(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        LocalDate observationDate = parseDate(request.get("observationDate"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        if (observationDate == null) {
            observationDate = LocalDate.now();
        }

        Map<String, Object> existing = followUpDashboardMapper.selectDailyObservation(registerId, observationDate);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("observationDate", observationDate);
        payload.put("observedBy", MedtechAuthContext.employeeIdOrNull());
        payload.put("note", request.get("note"));

        followUpDashboardMapper.insertDailyObservation(payload);
        historyService.recordObservationConfirmed(
            registerId,
            MedtechAuthContext.employeeIdOrNull(),
            request.get("note") != null ? String.valueOf(request.get("note")) : null
        );
        return followUpDashboardMapper.selectDailyObservation(registerId, observationDate);
    }

    public Map<String, Object> getObservationStatus(Long registerId, LocalDate observationDate) {
        LocalDate date = observationDate != null ? observationDate : LocalDate.now();
        Map<String, Object> row = followUpDashboardMapper.selectDailyObservation(registerId, date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("observationDate", date.toString());
        result.put("observed", row != null && !row.isEmpty());
        if (row != null) {
            result.putAll(row);
        }
        return result;
    }

    private Map<String, Object> enrichPatient(Map<String, Object> patient, LocalDate targetDate, Long currentEmployeeId) {
        Map<String, Object> row = new LinkedHashMap<>(patient);
        boolean observedToday = toBoolean(patient.get("observedToday"));
        boolean interviewScheduledToday = toBoolean(patient.get("interviewScheduledToday"));
        boolean contactedToday = toBoolean(patient.get("contactedToday"));
        int intervalDays = toInt(patient.get("interviewIntervalDays"), 7);
        LocalDate lastInterview = parseDate(patient.get("lastInterviewDate"));
        LocalDate lastContact = parseDate(patient.get("lastContactDate"));
        LocalDate deadline = parseDate(patient.get("followUpDeadline"));

        Long monitoringEmployeeId = toLong(patient.get("monitoringEmployeeId"));
        row.put("isMine", currentEmployeeId != null && currentEmployeeId.equals(monitoringEmployeeId));

        if (deadline == null && toBoolean(patient.get("enrolled"))) {
            deadline = targetDate.plusDays(180);
            row.put("followUpDeadline", deadline.toString());
        }
        if (deadline != null) {
            long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(targetDate, deadline);
            row.put("daysUntilDeadline", daysUntil);
        }

        String contactStatus = resolveContactStatus(contactedToday, lastContact, deadline, targetDate);
        row.put("contactStatus", contactStatus);
        if (lastContact != null) {
            row.put("daysSinceLastContact", java.time.temporal.ChronoUnit.DAYS.between(lastContact, targetDate));
        }

        boolean interviewDueToday = interviewScheduledToday;
        if (!interviewDueToday && lastInterview != null) {
            interviewDueToday = !lastInterview.plusDays(intervalDays).isAfter(targetDate);
        } else if (!interviewDueToday && lastInterview == null) {
            interviewDueToday = toBoolean(patient.get("enrolled"));
        }

        row.put("observedToday", observedToday);
        row.put("interviewDueToday", interviewDueToday);
        row.put("observationDueToday", toBoolean(patient.get("enrolled")) && !observedToday);
        row.put("contactedToday", contactedToday);

        Long registerId = toLong(patient.get("registerId"));
        if (registerId != null) {
            LocalDate from = targetDate.minusDays(30);
            row.put("trackedDates", followUpDashboardMapper.selectTrackedDates(registerId, from, targetDate));
            row.put("diseases", followUpOutcomeMapper.selectPatientDiseases(registerId));
        }
        return row;
    }

    private String resolveContactStatus(boolean contactedToday, LocalDate lastContact, LocalDate deadline, LocalDate targetDate) {
        if (contactedToday) {
            return "contacted_today";
        }
        if (deadline != null && targetDate.isAfter(deadline)) {
            return "overdue";
        }
        if (lastContact != null && java.time.temporal.ChronoUnit.DAYS.between(lastContact, targetDate) > 1) {
            return "due";
        }
        if (lastContact == null && deadline != null) {
            return "due";
        }
        return "within_limit";
    }

    private Long resolveDepartmentId(Long override) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return override;
        }
        Long departmentId = MedtechAuthContext.departmentIdOrNull();
        if (departmentId == null) {
            throw new BusinessException(403, "当前账号未绑定科室");
        }
        return departmentId;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text);
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

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
