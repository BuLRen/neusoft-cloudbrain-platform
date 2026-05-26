package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Scheduling Controller - 医生排班管理控制器
 */
@RestController
@RequestMapping("/api/registration/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;

    /**
     * 创建排班
     */
    @PostMapping
    public Result<Map<String, Object>> createScheduling(@RequestBody Map<String, Object> scheduling) {
        Map<String, Object> result = schedulingService.createScheduling(scheduling);
        return Result.success("排班创建成功", result);
    }

    /**
     * 更新排班
     */
    @PutMapping("/{id}")
    public Result<Void> updateScheduling(
            @PathVariable Long id,
            @RequestBody Map<String, Object> scheduling) {
        schedulingService.updateScheduling(id, scheduling);
        return Result.success();
    }

    /**
     * 删除排班（停诊）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteScheduling(@PathVariable Long id) {
        schedulingService.deleteScheduling(id);
        return Result.success();
    }

    /**
     * 获取排班详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getSchedulingDetail(@PathVariable Long id) {
        Map<String, Object> scheduling = schedulingService.getSchedulingDetail(id);
        return Result.success(scheduling);
    }

    /**
     * 按科室和日期查询可用排班
     */
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> getAvailableScheduling(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> schedulings = schedulingService.getAvailableScheduling(departmentId, date);
        return Result.success(schedulings);
    }

    /**
     * 按科室查询排班列表
     */
    @GetMapping("/department/{departmentId}")
    public Result<List<Map<String, Object>>> getSchedulingByDepartment(@PathVariable Long departmentId) {
        List<Map<String, Object>> schedulings = schedulingService.getSchedulingByDepartment(departmentId);
        return Result.success(schedulings);
    }

    /**
     * 按日期查询所有排班
     */
    @GetMapping("/date/{date}")
    public Result<List<Map<String, Object>>> getSchedulingByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> schedulings = schedulingService.getSchedulingByDate(date);
        return Result.success(schedulings);
    }

    /**
     * 按医生和日期范围查询排班
     */
    @GetMapping("/physician/{physicianId}")
    public Result<List<Map<String, Object>>> getSchedulingByPhysician(
            @PathVariable Long physicianId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> schedulings = schedulingService.getSchedulingByPhysicianAndDateRange(
            physicianId, startDate, endDate);
        return Result.success(schedulings);
    }

    /**
     * 批量创建排班
     */
    @PostMapping("/batch")
    public Result<List<Map<String, Object>>> batchCreateScheduling(
            @RequestBody List<Map<String, Object>> schedulingList) {
        List<Map<String, Object>> results = schedulingService.batchCreateScheduling(schedulingList);
        return Result.success("批量排班创建完成", results);
    }

    /**
     * 获取号源使用情况
     */
    @GetMapping("/{id}/quota")
    public Result<Map<String, Object>> getQuotaUsage(@PathVariable Long id) {
        Map<String, Object> usage = schedulingService.getQuotaUsage(id);
        return Result.success(usage);
    }
}
