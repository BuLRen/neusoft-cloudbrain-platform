package com.xikang.ctviewer.controller;

import com.xikang.common.result.Result;
import com.xikang.ctviewer.service.CtViewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ct-viewer/audit")
@RequiredArgsConstructor
public class CtViewerAuditController {

    private final CtViewerService ctViewerService;

    @GetMapping("/logs")
    public Result<Map<String, Object>> queryLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String volumeId,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Boolean success
    ) {
        return Result.success(ctViewerService.queryAuditLogs(page, size, volumeId, userId, action, success));
    }
}
