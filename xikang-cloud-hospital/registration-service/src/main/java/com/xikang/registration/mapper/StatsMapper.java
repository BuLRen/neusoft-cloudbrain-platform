package com.xikang.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Stats Mapper - 管理员统计聚合查询
 * 说明：所有服务共享同一个 PostgreSQL 库（xikang_hospital），因此可以跨表 JOIN
 * physician / medtech / pharmacy / ai_triage 的表。
 */
@Mapper
public interface StatsMapper {

    /**
     * 科室工作量聚合：挂号量 / 接诊量 / 检查量 / 处方量
     * - 挂号量：register 计数（排除退号4、爽约5）
     * - 接诊量：medical_record 计数（已写病历视为已接诊）
     * - 检查量：check_request + inspection_request + disposal_request 合计
     * - 处方量：prescription 计数
     * startDate / endDate 为可选时间区间，按 register.visit_date 过滤
     */
    List<Map<String, Object>> departmentWorkload(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 每日趋势：近 N 天的挂号量、收费额（已缴费 expense_record）、待分诊积压
     * 返回 [{label, registrations, charges, triagePending}, ...]
     */
    List<Map<String, Object>> dailyTrend(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * KPI 汇总：医院基础资源类指标
     * - departments:            在册科室数（department.delmark = 0）
     * - doctors:                在册临床医生数（employee.delmark = 0 且 deptment_id 在 1-20 临床科室）
     * - drugs:                  启用状态的药品数（drug_info.status = 1）
     * - aiTriageConsultations:  AI 导诊历史咨询数（ai_triage_record 全表计数）
     */
    Map<String, Object> kpiSummary(@Param("today") LocalDate today);
}
