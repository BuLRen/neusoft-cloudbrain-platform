package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.TriageDeskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Triage Desk Controller - AI分诊台控制器
 */
@RestController
@RequestMapping("/api/registration/triage-desk")
@RequiredArgsConstructor
public class TriageDeskController {

    private final TriageDeskService triageDeskService;

    /**
     * 创建分诊记录（从AI导诊创建）
     */
    @PostMapping
    public Result<Map<String, Object>> createTriageRecord(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = triageDeskService.createTriageRecord(request);
        return Result.success(result);
    }

    /**
     * 获取待确认分诊列表
     */
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> getPendingRecords() {
        List<Map<String, Object>> records = triageDeskService.getPendingRecords();
        return Result.success(records);
    }

    /**
     * 获取分诊记录详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getRecordDetail(@PathVariable Long id) {
        Map<String, Object> record = triageDeskService.getRecordDetail(id);
        return Result.success(record);
    }

    /**
     * 确认分诊结果
     */
    @PutMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirmTriage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> confirmData) {
        Map<String, Object> result = triageDeskService.confirmTriage(id, confirmData);
        return Result.success("分诊确认成功", result);
    }

    /**
     * 取消分诊记录
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelTriage(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        triageDeskService.cancelTriage(id, reason != null ? reason : "管理员取消");
        return Result.success();
    }

    /**
     * 获取患者的历史分诊记录
     */
    @GetMapping("/patient/{patientId}")
    public Result<List<Map<String, Object>>> getPatientRecords(@PathVariable Long patientId) {
        List<Map<String, Object>> records = triageDeskService.getPatientRecords(patientId);
        return Result.success(records);
    }
}
