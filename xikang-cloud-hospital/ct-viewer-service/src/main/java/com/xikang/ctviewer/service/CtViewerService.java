package com.xikang.ctviewer.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.ctviewer.client.CtViewerAlgoClient;
import com.xikang.ctviewer.dto.FilterRequestDto;
import com.xikang.ctviewer.dto.FilterResponseDto;
import com.xikang.ctviewer.dto.LoadResponseDto;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import com.xikang.ctviewer.repository.VolumeMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CtViewerService {

    private final VolumeStorageService storageService;
    private final VolumeMetaRepository metaRepository;
    private final CtViewerAlgoClient algoClient;

    public Map<String, Object> health() {
        return Map.of("ok", true, "algoReady", algoClient.healthCheck());
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
            return persistVolume(volumeId, outNrrd, algoData);
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
            return persistVolume(volumeId, outNrrd, algoData);
        } catch (IOException ex) {
            storageService.deleteVolumeDirectory(volumeId);
            throw new BusinessException(500, "保存 DICOM 文件失败", ex);
        }
    }

    public byte[] getVolumeNrrd(String volumeId) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        return storageService.readNrrdBytes(meta);
    }

    public FilterResponseDto applyFilter(FilterRequestDto request) {
        if (request == null || !StringUtils.hasText(request.getSourceVolumeId()) || !StringUtils.hasText(request.getFilterName())) {
            throw new BusinessException(400, "缺少 source_volume_id 或 filter_name");
        }

        VolumeMetaDto source = metaRepository.requireById(request.getSourceVolumeId());
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
            metaRepository.save(resultMeta);

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

    public ExportFile exportVolume(String volumeId, String format) {
        VolumeMetaDto meta = metaRepository.requireById(volumeId);
        String fmt = format == null ? "nrrd" : format.trim().toLowerCase();

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

    @SuppressWarnings("unchecked")
    private LoadResponseDto persistVolume(String volumeId, Path outNrrd, Map<String, Object> algoData) {
        Map<String, Object> metaMap = (Map<String, Object>) algoData.get("meta");
        long now = System.currentTimeMillis();
        VolumeMetaDto meta = VolumeMetaDto.fromAlgoMeta(volumeId, storageService.absolutePath(outNrrd), metaMap, now);
        metaRepository.save(meta);

        LoadResponseDto response = new LoadResponseDto();
        response.setVolumeId(volumeId);
        response.setMeta(meta.toFrontendMeta());
        return response;
    }

    public record ExportFile(byte[] bytes, String fileName, String contentType) {}
}
