package com.xikang.schedule.service;

import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.SchedulePlan;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
import com.xikang.schedule.mapper.SchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePlanService {

    private final SchedulePlanMapper schedulePlanMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;

    /**
     * 获取所有排班计划
     */
    public List<SchedulePlan> getAllPlans() {
        return schedulePlanMapper.selectAll();
    }

    /**
     * 根据ID获取排班计划
     */
    public SchedulePlan getPlanById(Long id) {
        return schedulePlanMapper.selectById(id);
    }

    /**
     * 根据科室和月份获取排班计划
     */
    public List<SchedulePlan> getPlansByDepartmentAndMonth(Long departmentId, String planMonth) {
        return schedulePlanMapper.selectByDepartmentAndMonth(departmentId, planMonth);
    }

    /**
     * 根据状态获取排班计划
     */
    public List<SchedulePlan> getPlansByStatus(String status) {
        return schedulePlanMapper.selectByStatus(status);
    }

    /**
     * 创建排班计划
     */
    @Transactional
    public SchedulePlan createPlan(SchedulePlan plan) {
        plan.setStatus("草稿");
        plan.setAiGenerated(false);
        schedulePlanMapper.insert(plan);
        return plan;
    }

    @Transactional
    public SchedulePlan createOrReuseAiDraftPlan(Long departmentId,
                                                 String planMonth,
                                                 String departmentName,
                                                 Long operatorId,
                                                 Integer aiVersion,
                                                 Integer totalSchedules,
                                                 Integer totalQuota) {
        List<SchedulePlan> existingPlans = getPlansByDepartmentAndMonth(departmentId, planMonth);
        SchedulePlan draftPlan = existingPlans.stream()
                .filter(plan -> "草稿".equals(plan.getStatus()))
                .findFirst()
                .orElse(null);

        String planName = (StringUtils.hasText(departmentName) ? departmentName : "") + planMonth + "排班";
        if (draftPlan == null) {
            SchedulePlan plan = new SchedulePlan();
            plan.setPlanName(planName);
            plan.setDepartmentId(departmentId);
            plan.setPlanMonth(planMonth);
            plan.setStatus("草稿");
            plan.setAiGenerated(true);
            plan.setAiVersion(aiVersion);
            plan.setTotalSchedules(totalSchedules);
            plan.setTotalQuota(totalQuota);
            plan.setCreatedBy(operatorId);
            schedulePlanMapper.insert(plan);
            return plan;
        }

        draftPlan.setPlanName(planName);
        draftPlan.setStatus("草稿");
        draftPlan.setAiGenerated(true);
        draftPlan.setAiVersion(aiVersion);
        draftPlan.setTotalSchedules(totalSchedules);
        draftPlan.setTotalQuota(totalQuota);
        if (draftPlan.getCreatedBy() == null) {
            draftPlan.setCreatedBy(operatorId);
        }
        schedulePlanMapper.update(draftPlan);
        return draftPlan;
    }

    /**
     * 更新排班计划
     */
    public void updatePlan(SchedulePlan plan) {
        schedulePlanMapper.update(plan);
    }

    /**
     * 发布排班计划
     */
    @Transactional
    public void publishPlan(Long planId, Long operatorId) {
        SchedulePlan plan = schedulePlanMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("排班计划不存在");
        }

        schedulePlanMapper.updatePublishInfo(planId, LocalDateTime.now(), operatorId);

        List<DoctorSchedule> schedules = doctorScheduleMapper.selectByPlanId(planId);
        for (DoctorSchedule schedule : schedules) {
            if ("草稿".equals(schedule.getStatus())) {
                doctorScheduleMapper.updateStatus(schedule.getId(), "正常");
            }
        }

        log.info("排班计划 {} 已发布，发布人：{}", planId, operatorId);
    }

    /**
     * 删除排班计划
     */
    @Transactional
    public void deletePlan(Long planId) {
        doctorScheduleMapper.deleteByPlanId(planId);
        schedulePlanMapper.deleteById(planId);
    }

    /**
     * 获取某科室某月的日历视图数据
     */
    public List<DayScheduleVO> getCalendarByMonth(Long departmentId, String month) {
        String[] parts = month.split("-");
        int year = Integer.parseInt(parts[0]);
        int monthNum = Integer.parseInt(parts[1]);
        LocalDate startDate = LocalDate.of(year, monthNum, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        List<SchedulePlan> plans = getPlansByDepartmentAndMonth(departmentId, month);
        SchedulePlan preferredPlan = plans.stream()
                .filter(plan -> "草稿".equals(plan.getStatus()))
                .findFirst()
                .orElseGet(() -> plans.stream()
                        .filter(plan -> "已发布".equals(plan.getStatus()))
                        .findFirst()
                        .orElse(null));

        if (preferredPlan == null) {
            return java.util.Collections.emptyList();
        }

        List<DoctorSchedule> schedules = doctorScheduleMapper.selectByPlanIdAndDateRange(
                preferredPlan.getId(), startDate, endDate);

        return schedules.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        DoctorSchedule::getWorkDate,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> {
                    DayScheduleVO vo = new DayScheduleVO();
                    vo.setDate(entry.getKey());
                    vo.setMorning(entry.getValue().stream()
                            .filter(s -> "上午".equals(s.getTimeSlot()))
                            .map(DoctorSchedule::getTotalQuota)
                            .filter(java.util.Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .sum());
                    vo.setAfternoon(entry.getValue().stream()
                            .filter(s -> "下午".equals(s.getTimeSlot()))
                            .map(DoctorSchedule::getTotalQuota)
                            .filter(java.util.Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .sum());
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @lombok.Data
    public static class DayScheduleVO {
        private LocalDate date;
        private Integer morning;
        private Integer afternoon;
    }
}
