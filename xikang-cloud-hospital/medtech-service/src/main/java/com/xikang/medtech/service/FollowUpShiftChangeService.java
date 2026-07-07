package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpShiftChangeService {

    private final FollowUpShiftMapper shiftMapper;

    @Transactional
    public Map<String, Object> submitChangeRequest(Map<String, Object> request) {
        Long employeeId = MedtechAuthContext.employeeIdOrNull();
        if (employeeId == null) {
            throw new BusinessException(403, "当前账号未绑定员工");
        }
        Long originalShiftId = toLong(request.get("originalShiftId"));
        LocalDate requestedDate = parseDate(request.get("requestedWorkDate"));
        String reason = request.get("reason") != null ? String.valueOf(request.get("reason")).trim() : "";
        if (originalShiftId == null || requestedDate == null) {
            throw new BusinessException("请填写完整调班信息");
        }
        if (reason.isEmpty()) {
            throw new BusinessException("请填写调班原因");
        }

        Map<String, Object> shift = shiftMapper.selectShiftById(originalShiftId);
        if (shift == null || shift.isEmpty()) {
            throw new BusinessException("原班次不存在");
        }
        if (!employeeId.equals(toLong(shift.get("employeeId")))) {
            throw new BusinessException(403, "只能申请调整自己的班次");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("employeeId", employeeId);
        payload.put("departmentId", shift.get("departmentId"));
        payload.put("originalShiftId", originalShiftId);
        payload.put("requestedWorkDate", requestedDate);
        payload.put("reason", reason);
        shiftMapper.insertChangeRequest(payload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", payload.get("id"));
        result.put("status", "pending");
        return result;
    }

    public List<Map<String, Object>> listPendingRequests(Long departmentId) {
        return shiftMapper.selectPendingChangeRequests(departmentId);
    }

    public int countPendingRequests(Long departmentId) {
        return shiftMapper.countPendingChangeRequests(departmentId);
    }

    @Transactional
    public Map<String, Object> reviewRequest(Long requestId, boolean approved, String adminNote) {
        Map<String, Object> request = shiftMapper.selectChangeRequestById(requestId);
        if (request == null || request.isEmpty()) {
            throw new BusinessException("调班申请不存在");
        }
        if (!"pending".equals(String.valueOf(request.get("status")))) {
            throw new BusinessException("该申请已处理");
        }

        Map<String, Object> update = new HashMap<>();
        update.put("id", requestId);
        update.put("status", approved ? "approved" : "rejected");
        update.put("reviewedBy", MedtechAuthContext.employeeIdOrNull());
        update.put("adminNote", adminNote);
        shiftMapper.updateChangeRequestStatus(update);

        if (approved) {
            Long shiftId = toLong(request.get("originalShiftId"));
            LocalDate newDate = parseDate(request.get("requestedWorkDate"));
            shiftMapper.updateShiftDate(shiftId, newDate);
        }

        Map<String, Object> result = new LinkedHashMap<>(shiftMapper.selectChangeRequestById(requestId));
        return result;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value).trim());
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
}
