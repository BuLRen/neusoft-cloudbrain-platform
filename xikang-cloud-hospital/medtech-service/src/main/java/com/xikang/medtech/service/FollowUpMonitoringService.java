package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpMonitoringMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpMonitoringService {

    private final FollowUpMonitoringMapper monitoringMapper;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpHistoryService historyService;

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

        Map<String, Object> enrollment = dashboardMapper.selectEnrollmentByRegisterId(registerId);
        if (enrollment == null || enrollment.isEmpty()) {
            throw new BusinessException("患者尚未纳入随访，请先完成纳入");
        }

        int updated = monitoringMapper.assignMonitoring(registerId, employeeId);
        monitoringMapper.assignMonitoringProfile(registerId, employeeId);
        if (updated == 0) {
            throw new BusinessException("分配失败，患者可能未在管");
        }

        historyService.recordMonitoringAssigned(registerId, employeeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("monitoringEmployeeId", employeeId);
        result.put("assigned", true);
        return result;
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
                monitoringMapper.assignMonitoring(registerId, toEmployeeId);
                monitoringMapper.assignMonitoringProfile(registerId, toEmployeeId);
                historyService.recordMonitoringAssigned(registerId, toEmployeeId);
            }
            historyService.recordMonitoringTransferApproved(registerId, reviewerId, adminNote);
        } else {
            historyService.recordMonitoringTransferRejected(registerId, reviewerId, adminNote);
        }

        return monitoringMapper.selectTransferRequestById(requestId);
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
