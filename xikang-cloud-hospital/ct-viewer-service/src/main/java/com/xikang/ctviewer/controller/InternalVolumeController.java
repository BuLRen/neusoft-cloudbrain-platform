package com.xikang.ctviewer.controller;

import com.xikang.common.result.Result;
import com.xikang.ctviewer.dto.AiSegmentRequestDto;
import com.xikang.ctviewer.dto.SegmentResponseDto;
import com.xikang.ctviewer.dto.VolumeBindRequestDto;
import com.xikang.ctviewer.service.CtViewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ct-viewer/internal/volume")
@RequiredArgsConstructor
public class InternalVolumeController {

    private final CtViewerService ctViewerService;

    @GetMapping("/{volumeId}/meta")
    public Result<Map<String, Object>> getVolumeMeta(@PathVariable String volumeId) {
        return Result.success(ctViewerService.getVolumeMetaInternal(volumeId));
    }

    @GetMapping("/{volumeId}/nrrd")
    public ResponseEntity<byte[]> getVolumeNrrd(@PathVariable String volumeId) {
        byte[] payload = ctViewerService.getVolumeNrrdInternal(volumeId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + volumeId + ".nrrd\"")
            .body(payload);
    }

    @PutMapping("/{volumeId}/bind")
    public Result<Void> bindVolume(
        @PathVariable String volumeId,
        @RequestBody VolumeBindRequestDto request
    ) {
        ctViewerService.bindVolume(volumeId, request);
        return Result.success();
    }

    @DeleteMapping("/{volumeId}/bind")
    public Result<Void> unbindVolume(@PathVariable String volumeId) {
        ctViewerService.unbindVolume(volumeId);
        return Result.success();
    }

    @PostMapping("/{volumeId}/analyze")
    public Result<Map<String, Object>> analyzeVolume(@PathVariable String volumeId) {
        return Result.success(ctViewerService.analyzeVolumeInternal(volumeId));
    }

    @PostMapping("/{volumeId}/segment")
    public Result<SegmentResponseDto> segmentVolume(@PathVariable String volumeId) {
        return Result.success(ctViewerService.segmentVolumeInternal(volumeId, null));
    }

    @PostMapping("/{volumeId}/segment/ai")
    public Result<SegmentResponseDto> aiSegmentVolume(
        @PathVariable String volumeId,
        @RequestBody(required = false) AiSegmentRequestDto request
    ) {
        String modelId = request != null ? request.getModelId() : null;
        return Result.success(ctViewerService.aiSegmentVolumeInternal(volumeId, modelId));
    }
}
