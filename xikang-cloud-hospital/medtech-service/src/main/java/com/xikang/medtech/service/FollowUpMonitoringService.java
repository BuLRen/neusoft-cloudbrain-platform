package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.config.FollowUpProperties;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpMonitoringMapper;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpMonitoringService {

    private record EnqueueAfterCommitTask(
        Long registerId,
        Long departmentId,
        String priorityLevel,
        Long employeeId
    ) {}

    private final FollowUpMonitoringMapper monitoringMapper;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpShiftMapper shiftMapper;
    private final FollowUpHistoryService historyService;
    private final FollowUpShiftEnqueueService shiftEnqueueService;
    private final FollowUpEnrollmentBackfillService enrollmentBackfillService;
    private final FollowUpEnrollmentSyncService enrollmentSyncService;
    private final FollowUpProperties followUpProperties;

    @Transactional
    public Map<String, Object> assignMonitoring(Long registerId, Long employeeId, Long departmentIdOverride) {
        if (registerId == null || employeeId == null) {
            throw new BusinessException("registerId 与 employeeId 不能为空");
        }
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可分配监视医生");
        }

        Map<String, Object> existing = dashboardMapper.selectMonitoringByRegisterId(registerId);
        if (existing == null || existing.isEmpty()) {
            throw new BusinessException("患者不存在");
        }

        if (!dashboardMapper.isEnrolledInFollowUpPool(registerId)) {
            if (!dashboardMapper.isEligiblePatient(registerId)) {
                throw new BusinessException("患者尚未纳入随访，且不符合自动纳入条件");
            }
            enrollmentBackfillService.processRegister(registerId);
        }

        Map<String, Object> result = assignMonitoringInternal(registerId, employeeId);
        result.put("assigned", true);
        return result;
    }

    @Transactional
    public Map<String, Object> assignMonitoringInternal(Long registerId, Long employeeId) {
        return assignMonitoringInternal(registerId, employeeId, null);
    }

    private Map<String, Object> assignMonitoringInternal(
        Long registerId,
        Long employeeId,
        List<EnqueueAfterCommitTask> batchEnqueueTasks
    ) {
        if (registerId == null || employeeId == null) {
            throw new BusinessException("registerId 与 employeeId 不能为空");
        }

        Map<String, Object> enrollment = dashboardMapper.selectEnrollmentByRegisterId(registerId);
        if (enrollment == null || enrollment.isEmpty()) {
            throw new BusinessException("患者尚未纳入随访");
        }

        int updatedEnrollment = monitoringMapper.assignMonitoring(registerId, employeeId);
        int updatedProfile = monitoringMapper.assignMonitoringProfile(registerId, employeeId);
        if (updatedEnrollment == 0 && updatedProfile == 0) {
            throw new BusinessException("分配失败，患者可能未在管");
        }

        if (updatedEnrollment == 0) {
            syncMonitoringEnrollment(registerId, employeeId, enrollment);
        }

        historyService.recordMonitoringAssigned(registerId, employeeId);
        enrollment.put("monitoringEmployeeId", employeeId);
        if (batchEnqueueTasks != null) {
            appendEnqueueTask(batchEnqueueTasks, registerId, employeeId, enrollment);
        } else {
            scheduleEnqueueAfterCommit(registerId, employeeId, enrollment);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("monitoringEmployeeId", employeeId);
        return result;
    }

    private void appendEnqueueTask(
        List<EnqueueAfterCommitTask> batchEnqueueTasks,
        Long registerId,
        Long employeeId,
        Map<String, Object> enrollment
    ) {
        EnqueueAfterCommitTask task = buildEnqueueTask(registerId, employeeId, enrollment);
        if (task != null) {
            batchEnqueueTasks.add(task);
        }
    }

    private void scheduleEnqueueAfterCommit(Long registerId, Long employeeId, Map<String, Object> enrollment) {
        EnqueueAfterCommitTask task = buildEnqueueTask(registerId, employeeId, enrollment);
        if (task == null) {
            return;
        }
        scheduleBatchEnqueueAfterCommit(List.of(task));
    }

    private EnqueueAfterCommitTask buildEnqueueTask(
        Long registerId,
        Long employeeId,
        Map<String, Object> enrollment
    ) {
        if (enrollment == null || enrollment.isEmpty()) {
            return null;
        }
        Long departmentId = toLong(enrollment.get("departmentId"));
        if (departmentId == null) {
            return null;
        }
        String priorityLevel = enrollment.get("priorityLevel") != null
            ? String.valueOf(enrollment.get("priorityLevel"))
            : "normal";
        return new EnqueueAfterCommitTask(registerId, departmentId, priorityLevel, employeeId);
    }

    private void scheduleBatchEnqueueAfterCommit(List<EnqueueAfterCommitTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fireEnqueueTasks(tasks);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fireEnqueueTasks(tasks);
            }
        });
    }

    private void fireEnqueueTasks(List<EnqueueAfterCommitTask> tasks) {
        for (EnqueueAfterCommitTask task : tasks) {
            shiftEnqueueService.enqueueAsync(
                task.registerId(),
                LocalDateTime.now(),
                task.departmentId(),
                task.priorityLevel(),
                task.employeeId()
            );
        }
    }

    @Transactional
    public Map<String, Object> randomAssignDepartment(Long departmentId) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可执行随机分布");
        }
        if (departmentId == null) {
            throw new BusinessException("departmentId 不能为空");
        }

        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        if (staff.isEmpty()) {
            throw new BusinessException("当前科室暂无随访医生，无法随机分布");
        }

        List<Long> nurseIds = staff.stream()
            .map(row -> toLong(row.get("id")))
            .filter(id -> id != null)
            .sorted()
            .toList();
        if (nurseIds.isEmpty()) {
            throw new BusinessException("当前科室暂无随访医生，无法随机分布");
        }

        List<Long> unassigned = new ArrayList<>(dashboardMapper.selectUnassignedEnrolledRegisterIds(departmentId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departmentId", departmentId);
        if (unassigned.isEmpty()) {
            result.put("assigned", 0);
            result.put("perDoctor", Map.of());
            return result;
        }

        Collections.shuffle(unassigned);
        Map<Long, Integer> perDoctor = new LinkedHashMap<>();
        for (Long nurseId : nurseIds) {
            perDoctor.put(nurseId, 0);
        }

        List<EnqueueAfterCommitTask> enqueueTasks = new ArrayList<>();
        int assigned = 0;
        for (int i = 0; i < unassigned.size(); i++) {
            Long registerId = unassigned.get(i);
            Long nurseId = nurseIds.get(i % nurseIds.size());
            assignMonitoringInternal(registerId, nurseId, enqueueTasks);
            perDoctor.merge(nurseId, 1, Integer::sum);
            assigned++;
        }
        scheduleBatchEnqueueAfterCommit(enqueueTasks);

        result.put("assigned", assigned);
        result.put("perDoctor", perDoctor);
        return result;
    }

    @Transactional
    public Map<String, Object> autoAssignIfEligible(Long registerId, Long departmentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);

        if (registerId == null || departmentId == null) {
            result.put("assigned", false);
            result.put("reason", "missing_params");
            return result;
        }

        Map<String, Object> existing = dashboardMapper.selectMonitoringByRegisterId(registerId);
        if (existing != null && toLong(existing.get("monitoringEmployeeId")) != null) {
            result.put("assigned", false);
            result.put("reason", "already_assigned");
            return result;
        }

        if (dashboardMapper.countAssignedMonitoringByDepartment(departmentId) == 0) {
            result.put("assigned", false);
            result.put("reason", "awaiting_initial_random");
            return result;
        }

        Long employeeId = pickLeastLoadedEmployee(departmentId);
        if (employeeId == null) {
            result.put("assigned", false);
            result.put("reason", "no_followup_staff");
            return result;
        }

        Map<String, Object> assigned = assignMonitoringInternal(registerId, employeeId, null);
        result.put("assigned", true);
        result.put("monitoringEmployeeId", assigned.get("monitoringEmployeeId"));
        return result;
    }

    public Map<String, Object> getMonitoringLoadSummary(Long departmentId) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可查看监视负载");
        }
        if (departmentId == null) {
            throw new BusinessException("departmentId 不能为空");
        }

        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        Map<Long, Integer> loadByEmployee = new HashMap<>();
        for (Map<String, Object> row : dashboardMapper.selectMonitoringLoadByDepartment(departmentId)) {
            Long employeeId = toLong(row.get("employeeId"));
            if (employeeId != null) {
                loadByEmployee.put(employeeId, toInt(row.get("patientCount")));
            }
        }

        List<Map<String, Object>> doctors = new ArrayList<>();
        for (Map<String, Object> member : staff) {
            Long employeeId = toLong(member.get("id"));
            if (employeeId == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("employeeId", employeeId);
            item.put("name", member.get("name"));
            item.put("patientCount", loadByEmployee.getOrDefault(employeeId, 0));
            doctors.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("departmentId", departmentId);
        summary.put("autoAssignEnabled", dashboardMapper.countAssignedMonitoringByDepartment(departmentId) > 0);
        summary.put("unassignedCount", dashboardMapper.selectUnassignedEnrolledRegisterIds(departmentId).size());
        summary.put("doctors", doctors);
        return summary;
    }

    private Long pickLeastLoadedEmployee(Long departmentId) {
        List<Map<String, Object>> staff = shiftMapper.selectFollowUpStaffByDepartment(departmentId);
        if (staff.isEmpty()) {
            return null;
        }

        Map<Long, Integer> loadByEmployee = new HashMap<>();
        for (Map<String, Object> row : dashboardMapper.selectMonitoringLoadByDepartment(departmentId)) {
            Long employeeId = toLong(row.get("employeeId"));
            if (employeeId != null) {
                loadByEmployee.put(employeeId, toInt(row.get("patientCount")));
            }
        }

        return staff.stream()
            .map(row -> toLong(row.get("id")))
            .filter(id -> id != null)
            .min(Comparator
                .comparingInt((Long id) -> loadByEmployee.getOrDefault(id, 0))
                .thenComparingLong(id -> id))
            .orElse(null);
    }

    @Transactional
    public Map<String, Object> submitTransferRequest(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Long toEmployeeId = toLong(request.get("toEmployeeId"));
        String reason = request.get("reason") != null ? String.valueOf(request.get("reason")).trim() : null;

        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            throw new BusinessException("请填写调换原因");
        }

        Long fromEmployeeId = MedtechAuthContext.employeeIdOrNull();
        if (fromEmployeeId == null) {
            throw new BusinessException(403, "当前账号未绑定员工");
        }

        Map<String, Object> existing = dashboardMapper.selectMonitoringByRegisterId(registerId);
        if (existing == null || existing.isEmpty()) {
            throw new BusinessException("患者不存在");
        }
        Long currentMonitorId = toLong(existing.get("monitoringEmployeeId"));
        if (currentMonitorId == null) {
            throw new BusinessException("该患者尚未分配监视医生，请联系管理员");
        }
        if (!currentMonitorId.equals(fromEmployeeId)) {
            throw new BusinessException("仅当前监视医生可申请调换");
        }

        Long departmentId = toLong(existing.get("departmentId"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("fromEmployeeId", fromEmployeeId);
        payload.put("toEmployeeId", toEmployeeId);
        payload.put("reason", reason);
        monitoringMapper.insertTransferRequest(payload);

        historyService.recordMonitoringTransferRequested(registerId, fromEmployeeId, toEmployeeId, reason);
        return monitoringMapper.selectTransferRequestById(toLong(payload.get("id")));
    }

    public List<Map<String, Object>> listPendingTransferRequests(Long departmentId) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可查看调换申请");
        }
        return monitoringMapper.selectPendingTransferRequests(departmentId);
    }

    public int countPendingTransferRequests(Long departmentId) {
        return monitoringMapper.countPendingTransferRequests(departmentId);
    }

    @Transactional
    public Map<String, Object> reviewTransferRequest(Long requestId, boolean approve, String adminNote) {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可审批调换申请");
        }

        Map<String, Object> request = monitoringMapper.selectTransferRequestById(requestId);
        if (request == null || request.isEmpty()) {
            throw new BusinessException("调换申请不存在");
        }
        if (!"pending".equals(String.valueOf(request.get("status")))) {
            throw new BusinessException("该申请已处理");
        }

        Long reviewerId = MedtechAuthContext.employeeIdOrNull();
        Map<String, Object> update = new HashMap<>();
        update.put("id", requestId);
        update.put("status", approve ? "approved" : "rejected");
        update.put("adminNote", adminNote);
        update.put("reviewedBy", reviewerId);
        monitoringMapper.updateTransferRequestStatus(update);

        Long registerId = toLong(request.get("registerId"));
        if (approve) {
            Long toEmployeeId = toLong(request.get("toEmployeeId"));
            if (toEmployeeId != null) {
                assignMonitoringInternal(registerId, toEmployeeId);
            }
            historyService.recordMonitoringTransferApproved(registerId, reviewerId, adminNote);
        } else {
            historyService.recordMonitoringTransferRejected(registerId, reviewerId, adminNote);
        }

        return monitoringMapper.selectTransferRequestById(requestId);
    }

    private void syncMonitoringEnrollment(Long registerId, Long employeeId, Map<String, Object> enrollment) {
        if (!followUpProperties.preferEnrollmentTable() || enrollment == null || enrollment.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", enrollment.get("departmentId"));
        payload.put("priorityLevel", enrollment.get("priorityLevel"));
        payload.put("interviewIntervalDays", enrollment.get("interviewIntervalDays"));
        payload.put("observationIntervalDays", enrollment.get("observationIntervalDays"));
        payload.put("monitoringEmployeeId", employeeId);
        payload.put("monitoredAt", LocalDateTime.now());
        payload.put("enrolledBy", MedtechAuthContext.employeeIdOrNull());
        enrollmentSyncService.trySyncEnrollment(payload);
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

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
