package com.xikang.ctviewer.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.ctviewer.audit.CtImagingAuditAction;
import com.xikang.ctviewer.client.AiCtClient;
import com.xikang.ctviewer.client.CtViewerAlgoClient;
import com.xikang.ctviewer.context.CtViewerAuthContext;
import com.xikang.ctviewer.dto.FilterRequestDto;
import com.xikang.ctviewer.dto.FilterResponseDto;
import com.xikang.ctviewer.dto.LoadResponseDto;
import com.xikang.ctviewer.dto.SegmentResponseDto;
import com.xikang.ctviewer.dto.VolumeBindRequestDto;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import com.xikang.ctviewer.repository.VolumeMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CtViewerService {

    private final VolumeStorageService storageService;
    private final VolumeMetaRepository metaRepository;
    private final VolumeAccessService volumeAccessService;
    private final CtImagingAuditService auditService;
    private final CtViewerAlgoClient algoClient;
    private final AiCtClient aiCtClient;

    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ok", true);
        status.put("algoReady", algoClient.healthCheck());
        status.put("aiCtReady", aiCtClient.healthCheck());
        return status;
    }

    public LoadResponseDto loadNrrd(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "缺少文件 file");
        }
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new BusinessException(400, "文件名为空");
        }

        String volumeId = storageService.newVolumeId();
        try {
            storageService.ensureVolumeDir(volumeId);
            String suffix = originalName.toLowerCase().endsWith(".nii.gz") ? ".nii.gz"
                : originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".nrrd";
            Path srcFile = storageService.volumeRoot(volumeId).resolve("src").resolve("upload" + suffix);
            storageService.saveUploadedFile(file, srcFile);

            Path outNrrd = storageService.nrrdPath(volumeId);
            Map<String, Object> algoData = algoClient.convert(
                "nrrd",
                storageService.absolutePath(srcFile),
                storageService.absolutePath(outNrrd),
                originalName
            );
            LoadResponseDto response = persistVolume(volumeId, outNrrd, algoData);
            auditService.logSuccess(CtImagingAuditAction.UPLOAD_NRRD, volumeId, null, null, null);
            return response;
        } catch (IOException ex) {
            storageService.deleteVolumeDirectory(volumeId);
            throw new BusinessException(500, "保存上传文件失败", ex);
        }
    }

    public LoadResponseDto loadDicom(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(400, "请上传 DICOM 文件夹内容");
        }

        String volumeId = storageService.newVolumeId();
        try {
            storageService.ensureVolumeDir(volumeId);
            Path dicomDir = storageService.srcDir(volumeId);
            storageService.saveDicomFiles(files, dicomDir);

            Path outNrrd = storageService.nrrdPath(volumeId);
            Map<String, Object> algoData = algoClient.convert(
                "dicom",
                storageService.absolutePath(dicomDir),
                storageService.absolutePath(outNrrd),
                "DICOM Folder"
            );
            LoadResponseDto response = persistVolume(volumeId, outNrrd, algoData);
            auditService.logSuccess(CtImagingAuditAction.UPLOAD_DICOM, volumeId, null, null, null);
            return response;
        } catch (IOException ex) {
            storageService.deleteVolumeDirectory(volumeId);
            throw new BusinessException(500, "保存 DICOM 文件失败", ex);
        }
    }

    public byte[] getVolumeNrrd(String volumeId) {
        VolumeMetaDto meta = volumeAccessService.requireReadableVolume(volumeId);
        auditService.logSuccess(
            CtImagingAuditAction.VIEW_NRRD,
            volumeId,
            null,
            meta.getBoundCheckRequestId(),
            meta.getBoundRegisterId()
        );
        return storageService.readNrrdBytes(meta);
    }

    public Map<String, Object> getVolumeMeta(String volumeId) {
        VolumeMetaDto meta = volumeAccessService.requireReadableVolume(volumeId);
        auditService.logSuccess(
            CtImagingAuditAction.VIEW_META,
            volumeId,
            null,
            meta.getBoundCheckRequestId(),
            meta.getBoundRegisterId()
        );
        Map<String, Object> result = new LinkedHashMap<>(meta.toFrontendMeta());
        result.put("volume_id", meta.getVolumeId());
        result.put("source_name", meta.getSourceName());
        return result;
    }

    public FilterResponseDto applyFilter(FilterRequestDto request) {
        if (request == null || !StringUtils.hasText(request.getSourceVolumeId()) || !StringUtils.hasText(request.getFilterName())) {
            throw new BusinessException(400, "缺少 source_volume_id 或 filter_name");
        }

        VolumeMetaDto source = volumeAccessService.requireReadableVolume(request.getSourceVolumeId());
        String resultId = storageService.newVolumeId();
        try {
            storageService.ensureVolumeDir(resultId);
            Path outNrrd = storageService.nrrdPath(resultId);
            Map<String, Object> algoData = algoClient.filter(
                source.getNrrdPath(),
                storageService.absolutePath(outNrrd),
                request.getFilterName(),
                request.getParams(),
                source.getSourceName()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) algoData.get("meta");
            long now = System.currentTimeMillis();
            VolumeMetaDto resultMeta = VolumeMetaDto.fromAlgoMeta(resultId, storageService.absolutePath(outNrrd), metaMap, now);
            resultMeta.inheritAccessFrom(source, resultId);
            metaRepository.save(resultMeta);

            auditService.logSuccess(
                CtImagingAuditAction.FILTER,
                resultId,
                source.getVolumeId(),
                source.getBoundCheckRequestId(),
                source.getBoundRegisterId()
            );

            FilterResponseDto response = new FilterResponseDto();
            response.setVolumeId(resultId);
            Object isMask = algoData.get("is_mask");
            response.setMask(Boolean.TRUE.equals(isMask));
            response.setMessage(String.valueOf(algoData.getOrDefault("message", "滤波完成")));
            response.setMeta(resultMeta.toFrontendMeta());
            return response;
        } catch (IOException ex) {
            storageService.deleteVolumeDirectory(resultId);
            throw new BusinessException(500, "创建滤波结果目录失败", ex);
        } catch (RuntimeException ex) {
            storageService.deleteVolumeDirectory(resultId);
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    public SegmentResponseDto segmentVolume(String volumeId, Map<String, Object> params) {
        VolumeMetaDto source = volumeAccessService.requireReadableVolume(volumeId);
        return runSegment(source, params);
    }

    /**
     * 供 medtech-service 内部调用（已在上游完成业务鉴权）。
     */
    public SegmentResponseDto segmentVolumeInternal(String volumeId, Map<String, Object> params) {
        VolumeMetaDto source = metaRepository.requireById(volumeId);
        return runSegment(source, params);
    }

    @SuppressWarnings("unchecked")
    private SegmentResponseDto runSegment(VolumeMetaDto source, Map<String, Object> params) {
        String resultId = storageService.newVolumeId();
        try {
            storageService.ensureVolumeDir(resultId);
            Path outNrrd = storageService.nrrdPath(resultId);
            Map<String, Object> algoData = algoClient.segment(
                source.getNrrdPath(),
                storageService.absolutePath(outNrrd),
                source.getSourceName(),
                params
            );

            Map<String, Object> metaMap = (Map<String, Object>) algoData.get("meta");
            long now = System.currentTimeMillis();
            VolumeMetaDto resultMeta = VolumeMetaDto.fromAlgoMeta(
                resultId,
                storageService.absolutePath(outNrrd),
                metaMap,
                now
            );
            resultMeta.inheritAccessFrom(source, resultId);
            metaRepository.save(resultMeta);

            auditService.logSuccess(
                CtImagingAuditAction.SEGMENT,
                resultId,
                source.getVolumeId(),
                source.getBoundCheckRequestId(),
                source.getBoundRegisterId()
            );

            SegmentResponseDto response = new SegmentResponseDto();
            response.setMaskVolumeId(resultId);
            Object isMask = algoData.get("is_mask");
            response.setMask(Boolean.TRUE.equals(isMask));
            response.setMessage(String.valueOf(algoData.getOrDefault("message", "分割完成")));
            response.setMeta(resultMeta.toFrontendMeta());
            Object lesions = algoData.get("lesions");
            if (lesions instanceof List<?> lesionList) {
                response.setLesions((List<Map<String, Object>>) lesionList);
            }
            Object summary = algoData.get("summary");
            if (summary instanceof Map<?, ?> summaryMap) {
                response.setSummary((Map<String, Object>) summaryMap);
            }
            return response;
        } catch (IOException ex) {
            storageService.deleteVolumeDirectory(resultId);
            throw new BusinessException(500, "创建分割结果目录失败", ex);
        } catch (RuntimeException ex) {
            storageService.deleteVolumeDirectory(resultId);
            throw ex;
        }
    }

    public ExportFile exportVolume(String volumeId, String format) {
        VolumeMetaDto meta = volumeAccessService.requireReadableVolume(volumeId);
        String fmt = format == null ? "nrrd" : format.trim().toLowerCase();

        ExportFile exported;
        if ("nrrd".equals(fmt)) {
            Path path = Path.of(meta.getNrrdPath());
            try {
                exported = new ExportFile(Files.readAllBytes(path), "filtered_volume.nrrd", "application/octet-stream");
            } catch (IOException ex) {
                throw new BusinessException(500, "读取 NRRD 失败", ex);
            }
        } else {
            String suffix = fmt.contains("nii") ? ".nii.gz" : ".nrrd";
            Path outPath = storageService.exportPath(volumeId, suffix);
            algoClient.export(meta.getNrrdPath(), storageService.absolutePath(outPath), fmt);
            try {
                byte[] bytes = Files.readAllBytes(outPath);
                String fileName = "filtered_volume" + suffix;
                String mime = suffix.endsWith(".gz") ? "application/gzip" : "application/octet-stream";
                exported = new ExportFile(bytes, fileName, mime);
            } catch (IOException ex) {
                throw new BusinessException(500, "读取导出文件失败", ex);
            }
        }

        auditService.logSuccess(
            CtImagingAuditAction.EXPORT,
            volumeId,
            null,
            meta.getBoundCheckRequestId(),
            meta.getBoundRegisterId()
        );
        return exported;
    }

    public Map<String, Object> analyzeVolume(String volumeId) {
        VolumeMetaDto meta = volumeAccessService.requireReadableVolume(volumeId);
        Map<String, Object> result = runAiAnalyze(meta);
        auditService.logSuccess(
            CtImagingAuditAction.ANALYZE,
            volumeId,
            null,
            meta.getBoundCheckRequestId(),
            meta.getBoundRegisterId()
        );
        return result;
    }

    /**
     * 供 medtech-service 内部调用（已在上游完成业务鉴权）。
     */
    public Map<String, Object> analyzeVolumeInternal(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        return runAiAnalyze(meta);
    }

    /**
     * 供 physician-service 等内部服务只读拉取体数据（上游已完成业务鉴权）。
     */
    public byte[] getVolumeNrrdInternal(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        return storageService.readNrrdBytes(meta);
    }

    private Map<String, Object> runAiAnalyze(VolumeMetaDto meta) {
        ExportFile exported = exportVolumeInternal(meta, "nii.gz");
        return aiCtClient.analyze(exported.bytes(), exported.fileName());
    }

    public void bindVolume(String volumeId, VolumeBindRequestDto request) {
        if (request == null || request.getCheckRequestId() == null) {
            throw new BusinessException(400, "缺少 checkRequestId");
        }
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        meta.applyBinding(request.getCheckRequestId(), request.getDepartmentId(), request.getRegisterId());
        metaRepository.savePersistent(meta);
        auditService.logInternal(
            CtImagingAuditAction.BIND,
            volumeId,
            request.getCheckRequestId(),
            request.getRegisterId()
        );
    }

    public void unbindVolume(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        Long checkRequestId = meta.getBoundCheckRequestId();
        Long registerId = meta.getBoundRegisterId();
        meta.clearBinding();
        metaRepository.save(meta);
        auditService.logInternal(
            CtImagingAuditAction.UNBIND,
            volumeId,
            checkRequestId,
            registerId
        );
    }

    public Map<String, Object> getVolumeMetaInternal(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        Map<String, Object> result = new LinkedHashMap<>(meta.toFrontendMeta());
        result.put("volume_id", meta.getVolumeId());
        result.put("source_name", meta.getSourceName());
        return result;
    }

    public Map<String, Object> queryAuditLogs(
        int page,
        int size,
        String volumeId,
        Long userId,
        String action,
        Boolean success
    ) {
        if (!CtViewerAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可查询审计日志");
        }
        return auditService.queryLogs(page, size, volumeId, userId, action, success);
    }

    @SuppressWarnings("unchecked")
    private LoadResponseDto persistVolume(String volumeId, Path outNrrd, Map<String, Object> algoData) {
        Map<String, Object> metaMap = (Map<String, Object>) algoData.get("meta");
        long now = System.currentTimeMillis();
        VolumeMetaDto meta = VolumeMetaDto.fromAlgoMeta(volumeId, storageService.absolutePath(outNrrd), metaMap, now);
        applyCurrentOwner(meta);
        metaRepository.save(meta);

        LoadResponseDto response = new LoadResponseDto();
        response.setVolumeId(volumeId);
        response.setMeta(meta.toFrontendMeta());
        return response;
    }

    private void applyCurrentOwner(VolumeMetaDto meta) {
        CtViewerAuthContext.Context ctx = CtViewerAuthContext.get();
        if (ctx == null) {
            return;
        }
        meta.applyOwner(ctx.userId(), ctx.employeeId(), ctx.departmentId());
    }

    private ExportFile exportVolumeInternal(VolumeMetaDto meta, String format) {
        String fmt = format == null ? "nrrd" : format.trim().toLowerCase();
        String volumeId = meta.getVolumeId();

        if ("nrrd".equals(fmt)) {
            Path path = Path.of(meta.getNrrdPath());
            try {
                return new ExportFile(Files.readAllBytes(path), "filtered_volume.nrrd", "application/octet-stream");
            } catch (IOException ex) {
                throw new BusinessException(500, "读取 NRRD 失败", ex);
            }
        }

        String suffix = fmt.contains("nii") ? ".nii.gz" : ".nrrd";
        Path outPath = storageService.exportPath(volumeId, suffix);
        algoClient.export(meta.getNrrdPath(), storageService.absolutePath(outPath), fmt);
        try {
            byte[] bytes = Files.readAllBytes(outPath);
            String fileName = "filtered_volume" + suffix;
            String mime = suffix.endsWith(".gz") ? "application/gzip" : "application/octet-stream";
            return new ExportFile(bytes, fileName, mime);
        } catch (IOException ex) {
            throw new BusinessException(500, "读取导出文件失败", ex);
        }
    }

    public record ExportFile(byte[] bytes, String fileName, String contentType) {}
}
