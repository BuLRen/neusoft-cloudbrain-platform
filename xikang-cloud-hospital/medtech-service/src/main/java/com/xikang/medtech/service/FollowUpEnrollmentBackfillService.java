package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpEnrollmentBackfillService {

    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpPriorityScorer priorityScorer;
    private final FollowUpEnrollmentSyncService enrollmentSyncService;
    private final FollowUpClinicalSnapshotService clinicalSnapshotService;
    private final FollowUpProperties followUpProperties;

    public Map<String, Object> backfillEnrollment(Integer batchSize, Integer maxBatches) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可执行随访回填");
        }

        int size = batchSize == null || batchSize < 1 ? 50 : Math.min(batchSize, 200);
        int batches = maxBatches == null || maxBatches < 1 ? 100 : Math.min(maxBatches, 1000);

        int processed = 0;
        int enrolled = 0;
        int snapshots = 0;
        int offset = 0;

        for (int batch = 0; batch < batches; batch++) {
            List<Long> registerIds = dashboardMapper.selectEligibleRegisterIdsNotEnrolled(size, offset);
            if (registerIds.isEmpty()) {
                break;
            }
            for (Long registerId : registerIds) {
                processed++;
                if (processRegister(registerId)) {
                    enrolled++;
                    snapshots++;
                }
            }
            if (registerIds.size() < size) {
                break;
            }
            offset += size;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", processed);
        result.put("enrolled", enrolled);
        result.put("snapshotsSynced", snapshots);
        result.put("remainingEligible", dashboardMapper.countEligibleRegisterIdsNotEnrolled());
        return result;
    }

    @Transactional
    public boolean processRegister(Long registerId) {
        if (!dashboardMapper.isEligiblePatient(registerId)) {
            return false;
        }

        Long departmentId = dashboardMapper.selectRegisterDepartmentId(registerId);
        if (departmentId == null) {
            log.warn("回填跳过：无法确定科室 registerId={}", registerId);
            return false;
        }

        FollowUpPriorityResult priority = priorityScorer.score(registerId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("priorityLevel", priority.getPriorityLevel());
        payload.put("interviewIntervalDays", priority.getInterviewIntervalDays());
        payload.put("observationIntervalDays", priority.getObservationIntervalDays());
        payload.put("enrolledBy", MedtechAuthContext.employeeIdOrNull());

        dashboardMapper.upsertPatientProfile(payload);
        if (followUpProperties.preferEnrollmentTable()) {
            enrollmentSyncService.trySyncEnrollment(payload);
        }

        try {
            clinicalSnapshotService.syncFromClinical(registerId);
        } catch (Exception ex) {
            log.warn("回填快照失败 registerId={}: {}", registerId, ex.getMessage());
        }
        return true;
    }
}
