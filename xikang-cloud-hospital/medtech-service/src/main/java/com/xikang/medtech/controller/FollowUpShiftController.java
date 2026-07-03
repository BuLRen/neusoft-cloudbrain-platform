package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpShiftAiTaskService;
import com.xikang.medtech.service.FollowUpShiftChangeService;
import com.xikang.medtech.service.FollowUpShiftScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/shift")
@RequiredArgsConstructor
public class FollowUpShiftController {

    private final FollowUpShiftScheduleService scheduleService;
    private final FollowUpShiftChangeService changeService;

    @GetMapping("/my-shifts")
    public Result<List<Map<String, Object>>> myShifts(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return Result.success(scheduleService.listMyShifts(from, to));
    }

    @PostMapping("/change-request")
    public Result<Map<String, Object>> submitChangeRequest(@RequestBody Map<String, Object> request) {
        return Result.success("调班申请已提交", changeService.submitChangeRequest(request));
    }
}
