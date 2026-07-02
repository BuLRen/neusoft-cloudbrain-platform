package com.xikang.ctviewer.service;

import com.xikang.ctviewer.context.CtViewerAuthContext;
import com.xikang.ctviewer.dto.CtImagingAuditLogDto;
import com.xikang.ctviewer.entity.CtImagingAuditLog;
import com.xikang.ctviewer.mapper.CtImagingAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CtImagingAuditService {

    private final CtImagingAuditLogMapper auditLogMapper;

    public void logSuccess(
        String action,
        String volumeId,
        String sourceVolumeId,
        Long checkRequestId,
        Long registerId
    ) {
        write(action, volumeId, sourceVolumeId, checkRequestId, registerId, true, null);
    }

    public void logDenied(
        String action,
        String volumeId,
        String denialReason
    ) {
        write(action, volumeId, null, null, null, false, denialReason);
    }

    public void logInternal(
        String action,
        String volumeId,
        Long checkRequestId,
        Long registerId
    ) {
        write(action, volumeId, null, checkRequestId, registerId, true, null);
    }

    public Map<String, Object> queryLogs(
        int page,
        int size,
        String volumeId,
        Long userId,
        String action,
        Boolean success
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        long total = auditLogMapper.countByFilters(volumeId, userId, action, success);
        List<CtImagingAuditLog> rows = auditLogMapper.selectByFilters(volumeId, userId, action, success, offset, safeSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", total);
        result.put("items", rows.stream().map(this::toDto).toList());
        return result;
    }

    private void write(
        String action,
        String volumeId,
        String sourceVolumeId,
        Long checkRequestId,
        Long registerId,
        boolean success,
        String denialReason
    ) {
        try {
            CtViewerAuthContext.Context ctx = CtViewerAuthContext.get();
            CtImagingAuditLog row = new CtImagingAuditLog();
            row.setUserId(ctx != null ? ctx.userId() : null);
            row.setEmployeeId(ctx != null ? ctx.employeeId() : null);
            row.setDepartmentId(ctx != null ? ctx.departmentId() : null);
            row.setAction(action);
            row.setVolumeId(volumeId);
            row.setSourceVolumeId(sourceVolumeId);
            row.setCheckRequestId(checkRequestId);
            row.setRegisterId(registerId);
            row.setSuccess(success);
            row.setDenialReason(denialReason);
            row.setClientIp(resolveClientIp());
            row.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("Failed to write CT imaging audit log action={} volumeId={}", action, volumeId, ex);
        }
    }

    private CtImagingAuditLogDto toDto(CtImagingAuditLog row) {
        CtImagingAuditLogDto dto = new CtImagingAuditLogDto();
        dto.setId(row.getId());
        dto.setUserId(row.getUserId());
        dto.setEmployeeId(row.getEmployeeId());
        dto.setDepartmentId(row.getDepartmentId());
        dto.setAction(row.getAction());
        dto.setVolumeId(row.getVolumeId());
        dto.setSourceVolumeId(row.getSourceVolumeId());
        dto.setCheckRequestId(row.getCheckRequestId());
        dto.setRegisterId(row.getRegisterId());
        dto.setSuccess(row.getSuccess());
        dto.setDenialReason(row.getDenialReason());
        dto.setClientIp(row.getClientIp());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
