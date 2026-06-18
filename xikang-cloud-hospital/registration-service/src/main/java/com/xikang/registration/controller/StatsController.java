package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.registration.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Stats Controller - 管理员统计聚合接口
 * 供管理员端「运营仪表盘」「统计报表」使用
 */
@RestController
@RequestMapping("/api/registration/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 科室工作量：挂号量 / 接诊量 / 检查量 / 处方量
     * 可选时间区间 [startDate, endDate)
     */
    @GetMapping("/department-workload")
    public Result<List<Map<String, Object>>> departmentWorkload(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statsService.departmentWorkload(startDate, endDate));
    }

    /**
     * 每日趋势（默认近 7 天）
     */
    @GetMapping("/daily-trend")
    public Result<List<Map<String, Object>>> dailyTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return Result.success(statsService.dailyTrend(days));
    }

    /**
     * KPI 汇总（今日挂号 / 待分诊 / 今日收费 / 待接诊）
     */
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        return Result.success(statsService.kpiSummary());
    }
}
