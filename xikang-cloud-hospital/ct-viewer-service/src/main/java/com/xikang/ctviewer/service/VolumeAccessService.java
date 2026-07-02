package com.xikang.ctviewer.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.ctviewer.audit.CtImagingAuditAction;
import com.xikang.ctviewer.context.CtViewerAuthContext;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import com.xikang.ctviewer.repository.VolumeMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VolumeAccessService {

    private final VolumeMetaRepository metaRepository;
    private final CtImagingAuditService auditService;

    public VolumeMetaDto requireReadableVolume(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        assertCanRead(meta);
        return meta;
    }

    public void assertCanRead(VolumeMetaDto meta) {
        if (meta == null) {
            throw new BusinessException(404, "volume_id 不存在");
        }
        if (CtViewerAuthContext.isAdminAllAccess()) {
            return;
        }

        CtViewerAuthContext.Context ctx = CtViewerAuthContext.get();
        if (ctx == null) {
            deny(meta.getVolumeId(), "未登录");
        }

        if (Objects.equals(meta.getOwnerUserId(), ctx.userId())) {
            return;
        }

        Long departmentId = ctx.departmentId();
        if (departmentId != null) {
            if (Objects.equals(meta.getOwnerDepartmentId(), departmentId)) {
                return;
            }
            if (meta.getBoundDepartmentId() != null && Objects.equals(meta.getBoundDepartmentId(), departmentId)) {
                return;
            }
        }

        deny(meta.getVolumeId(), "无权访问该 CT 影像");
    }

    private void deny(String volumeId, String reason) {
        auditService.logDenied(CtImagingAuditAction.ACCESS_DENIED, volumeId, reason);
        throw new BusinessException(403, reason);
    }
}
