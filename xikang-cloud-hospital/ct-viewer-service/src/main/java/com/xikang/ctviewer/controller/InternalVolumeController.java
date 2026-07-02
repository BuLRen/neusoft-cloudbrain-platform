package com.xikang.ctviewer.controller;

import com.xikang.common.result.Result;
import com.xikang.ctviewer.dto.VolumeBindRequestDto;
import com.xikang.ctviewer.service.CtViewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
