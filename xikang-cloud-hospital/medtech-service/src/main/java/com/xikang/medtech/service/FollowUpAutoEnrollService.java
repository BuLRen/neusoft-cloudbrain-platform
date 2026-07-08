package com.xikang.medtech.service;

import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpAutoEnrollService {

    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpPriorityScorer priorityScorer;
    private final FollowUpEnrollmentSyncService enrollmentSyncService;
    private final FollowUpClinicalSnapshotService clinicalSnapshotService;
    private final FollowUpShiftEnqueueService shiftEnqueueService;
    private final FollowUpProperties followUpProperties;
    private final FollowUpMonitoringService monitoringService;

    @Transactional
    public Map<String, Object> handleVisitEnded(
        Long registerId,
        LocalDateTime visitEndedAt,
        Long employeeId,
        Long departmentId
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);

        if (!dashboardMapper.isEligiblePatient(registerId)) {
            result.put("skipped", true);
            result.put("reason", "not_eligible");
            return result;
        }

        Long managingDepartmentId = departmentId != null ? departmentId : dashboardMapper.selectRegisterDepartmentId(registerId);
        if (managingDepartmentId == null) {
            result.put("skipped", true);
            result.put("reason", "missing_department");
            return result;
        }

        Map<String, Object> enrollment = dashboardMapper.selectEnrollmentByRegisterId(registerId);
        boolean alreadyEnrolled = dashboardMapper.isEnrolledInFollowUpPool(registerId);

        FollowUpPriorityResult priority = priorityScorer.score(registerId);
        if (!alreadyEnrolled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("registerId", registerId);
            payload.put("departmentId", managingDepartmentId);
            payload.put("priorityLevel", priority.getPriorityLevel());
            payload.put("interviewIntervalDays", priority.getInterviewIntervalDays());
            payload.put("observationIntervalDays", priority.getObservationIntervalDays());
            payload.put("enrolledBy", employeeId);
            dashboardMapper.upsertPatientProfile(payload);
            if (followUpProperties.preferEnrollmentTable()) {
                enrollmentSyncService.trySyncEnrollment(payload);
            }
            result.put("enrolled", true);
        } else {
            result.put("enrolled", false);
            result.put("alreadyEnrolled", true);
        }

        Map<String, Object> autoAssign = monitoringService.autoAssignIfEligible(registerId, managingDepartmentId);
        if (Boolean.TRUE.equals(autoAssign.get("assigned"))) {
            result.put("monitoringAutoAssigned", true);
            result.put("monitoringEmployeeId", autoAssign.get("monitoringEmployeeId"));
        }

        try {
            clinicalSnapshotService.syncFromClinical(registerId);
            result.put("snapshotSynced", true);
        } catch (Exception ex) {
            log.warn("visit-ended 快照同步失败 registerId={}: {}", registerId, ex.getMessage());
            result.put("snapshotSynced", false);
        }

        LocalDateTime endedAt = visitEndedAt != null ? visitEndedAt : LocalDateTime.now();
        Map<String, Object> enrollmentAfter = dashboardMapper.selectEnrollmentByRegisterId(registerId);
        Long preferMonitor = toLong(enrollmentAfter != null ? enrollmentAfter.get("monitoringEmployeeId") : null);
        shiftEnqueueService.enqueueAsync(
            registerId,
            endedAt,
            managingDepartmentId,
            priority.getPriorityLevel(),
            preferMonitor
        );
        result.put("enqueueSubmitted", true);
        result.put("priorityLevel", priority.getPriorityLevel());
        return result;
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
