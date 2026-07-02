package com.xikang.ctviewer.controller;

import com.xikang.common.result.Result;
import com.xikang.ctviewer.dto.FilterRequestDto;
import com.xikang.ctviewer.dto.FilterResponseDto;
import com.xikang.ctviewer.dto.LoadResponseDto;
import com.xikang.ctviewer.service.CtViewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ct-viewer")
@RequiredArgsConstructor
public class CtViewerController {

    private final CtViewerService ctViewerService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(ctViewerService.health());
    }

    @PostMapping(value = "/load-nrrd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<LoadResponseDto> loadNrrd(@RequestParam("file") MultipartFile file) {
        return Result.success(ctViewerService.loadNrrd(file));
    }

    @PostMapping(value = "/load-dicom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<LoadResponseDto> loadDicom(@RequestParam("files") MultipartFile[] files) {
        return Result.success(ctViewerService.loadDicom(files));
    }

    @GetMapping("/volume/{volumeId}/nrrd")
    public ResponseEntity<byte[]> getVolumeNrrd(@PathVariable String volumeId) {
        byte[] payload = ctViewerService.getVolumeNrrd(volumeId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + volumeId + ".nrrd\"")
            .body(payload);
    }

    @GetMapping("/volume/{volumeId}/meta")
    public Result<Map<String, Object>> getVolumeMeta(@PathVariable String volumeId) {
        return Result.success(ctViewerService.getVolumeMeta(volumeId));
    }

    @PostMapping("/filter")
    public Result<FilterResponseDto> filter(@RequestBody FilterRequestDto request) {
        return Result.success(ctViewerService.applyFilter(request));
    }

    @GetMapping("/volume/{volumeId}/save")
    public ResponseEntity<byte[]> saveVolume(
        @PathVariable String volumeId,
        @RequestParam(defaultValue = "nrrd") String format
    ) {
        CtViewerService.ExportFile exported = ctViewerService.exportVolume(volumeId, format);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, exported.contentType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.fileName() + "\"")
            .body(exported.bytes());
    }
}
