package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/history")
@RequiredArgsConstructor
public class FollowUpHistoryController {

    private final FollowUpHistoryService historyService;

    @GetMapping
    public Result<List<Map<String, Object>>> listEvents(
        @RequestParam(required = false) Long registerId,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Integer limit
    ) {
        return Result.success(historyService.listEvents(registerId, departmentId, from, to, eventType, limit));
    }
}
