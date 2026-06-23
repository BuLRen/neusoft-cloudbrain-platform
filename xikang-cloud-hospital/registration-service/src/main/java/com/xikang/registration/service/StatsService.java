package com.xikang.registration.service;

import com.xikang.registration.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Stats Service - 管理员统计聚合服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;

    /**
     * 科室工作量
     * @param startDate 可选，包含
     * @param endDate   可选，排除（即 [startDate, endDate)）
     */
    public List<Map<String, Object>> departmentWorkload(LocalDate startDate, LocalDate endDate) {
        return statsMapper.departmentWorkload(startDate, endDate);
    }

    /**
     * 每日趋势：默认近 7 天
     */
    public List<Map<String, Object>> dailyTrend(Integer days) {
        int n = (days == null || days <= 0 || days > 90) ? 7 : days;
        LocalDate end = LocalDate.now().plusDays(1);   // 排除边界：到明天 0 点
        LocalDate start = end.minusDays(n);
        return statsMapper.dailyTrend(start, end);
    }

    /**
     * KPI 汇总（今日）
     */
    public Map<String, Object> kpiSummary() {
        return statsMapper.kpiSummary(LocalDate.now());
    }
}
