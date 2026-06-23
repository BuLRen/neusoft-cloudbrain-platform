package com.xikang.schedule.service;

import com.xikang.schedule.dto.QuotaDeductDTO;
import com.xikang.schedule.dto.QuotaResultDTO;
import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.ScheduleAdjustLog;
import com.xikang.schedule.entity.SchedulePlan;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
import com.xikang.schedule.mapper.ScheduleAdjustLogMapper;
import com.xikang.schedule.mapper.SchedulePlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleMapper doctorScheduleMapper;
    private final SchedulePlanMapper schedulePlanMapper;
    private final ScheduleAdjustLogMapper scheduleAdjustLogMapper;

    /**
     * 获取可用排班（供挂号使用）
     */
    public List<DoctorSchedule> getAvailable(Long departmentId, LocalDate date) {
        return doctorScheduleMapper.selectAvailable(departmentId, date);
    }

    /**
     * 获取某日期范围的可用排班
     */
    public List<DoctorSchedule> getAvailableByDateRange(Long departmentId, LocalDate startDate, LocalDate endDate) {
        return doctorScheduleMapper.selectAvailableByDateRange(departmentId, startDate, endDate);
    }

    /**
     * 获取排班详情
     */
    public DoctorSchedule getById(Long id) {
        return doctorScheduleMapper.selectById(id);
    }

    /**
     * 获取某医生的排班列表
     */
    public List<DoctorSchedule> getByPhysician(Long physicianId, LocalDate startDate, LocalDate endDate) {
        return doctorScheduleMapper.selectByPhysician(physicianId, startDate, endDate);
    }

    /**
     * 获取排班计划下的所有排班
     */
    public List<DoctorSchedule> getByPlanId(Long planId) {
        return doctorScheduleMapper.selectByPlanId(planId);
    }

    /**
     * 创建排班
     */
    @Transactional
    public DoctorSchedule create(DoctorSchedule schedule) {
        // 检查是否重复排班
        DoctorSchedule existing = doctorScheduleMapper.selectByPhysicianAndDate(
                schedule.getPhysicianId(), schedule.getWorkDate(), schedule.getTimeSlot());
        if (existing != null) {
            throw new RuntimeException("该医生此时段已有排班");
        }

        schedule.setUsedQuota(0);
        schedule.setAvailableQuota(schedule.getTotalQuota());
        schedule.setStatus("正常");
        schedule.setModified(false);

        doctorScheduleMapper.insert(schedule);

        // 更新计划统计
        updatePlanStatistics(schedule.getPlanId());

        return schedule;
    }

    /**
     * 批量创建排班
     */
    @Transactional
    public void batchCreate(List<DoctorSchedule> schedules, Long planId) {
        // 先删除旧排班
        doctorScheduleMapper.deleteByPlanId(planId);

        // 批量插入
        for (DoctorSchedule schedule : schedules) {
            schedule.setUsedQuota(0);
            schedule.setAvailableQuota(schedule.getTotalQuota());
            schedule.setStatus("正常");
            schedule.setModified(false);
        }
        doctorScheduleMapper.batchInsert(schedules);

        // 更新计划统计
        updatePlanStatistics(planId);

        log.info("批量创建排班 {} 条，计划ID：{}", schedules.size(), planId);
    }

    /**
     * 更新排班
     */
    @Transactional
    public void update(Long scheduleId, DoctorSchedule schedule, Long operatorId, String remark) {
        DoctorSchedule existing = doctorScheduleMapper.selectById(scheduleId);
        if (existing == null) {
            throw new RuntimeException("排班不存在");
        }

        DoctorSchedule duplicate = doctorScheduleMapper.selectByPhysicianAndDate(
                schedule.getPhysicianId(), schedule.getWorkDate(), schedule.getTimeSlot());
        if (duplicate != null && !duplicate.getId().equals(scheduleId)) {
            throw new RuntimeException("该医生此时段已有排班");
        }

        // 记录变更日志
        if (!existing.getPhysicianId().equals(schedule.getPhysicianId())) {
            logAdjust(scheduleId, "physician_id", String.valueOf(existing.getPhysicianId()),
                    String.valueOf(schedule.getPhysicianId()), "admin_urgent", operatorId, remark);
        }
        if (!existing.getTotalQuota().equals(schedule.getTotalQuota())) {
            logAdjust(scheduleId, "total_quota", String.valueOf(existing.getTotalQuota()),
                    String.valueOf(schedule.getTotalQuota()), "admin_urgent", operatorId, remark);
        }
        if (!existing.getStatus().equals(schedule.getStatus())) {
            logAdjust(scheduleId, "status", existing.getStatus(),
                    schedule.getStatus(), "admin_urgent", operatorId, remark);
        }

        schedule.setId(scheduleId);
        schedule.setModified(true);
        schedule.setModifyRemark(remark);
        schedule.setAvailableQuota(Math.max(0, schedule.getTotalQuota() - existing.getUsedQuota()));
        doctorScheduleMapper.update(schedule);

        // 更新计划统计
        updatePlanStatistics(existing.getPlanId());
    }

    /**
     * 扣减号源（供挂号使用）
     */
    @Transactional
    public QuotaResultDTO deductQuota(QuotaDeductDTO dto) {
        DoctorSchedule schedule = doctorScheduleMapper.selectById(dto.getScheduleId());
        if (schedule == null) {
            QuotaResultDTO result = new QuotaResultDTO();
            result.setSuccess(false);
            result.setMessage("排班不存在");
            return result;
        }

        if (schedule.getAvailableQuota() < dto.getCount()) {
            QuotaResultDTO result = new QuotaResultDTO();
            result.setSuccess(false);
            result.setRemainingQuota(schedule.getAvailableQuota());
            result.setMessage("号源不足");
            return result;
        }

        int newUsedQuota = schedule.getUsedQuota() + dto.getCount();
        int newAvailableQuota = schedule.getTotalQuota() - newUsedQuota;

        doctorScheduleMapper.updateQuota(dto.getScheduleId(), newUsedQuota, newAvailableQuota);

        // 更新计划统计
        updatePlanStatistics(schedule.getPlanId());

        log.info("扣减号源：排班ID={}, 扣减数量={}, 剩余号源={}",
                dto.getScheduleId(), dto.getCount(), newAvailableQuota);

        QuotaResultDTO result = new QuotaResultDTO();
        result.setSuccess(true);
        result.setRemainingQuota(newAvailableQuota);
        result.setMessage("扣减成功");
        return result;
    }

    /**
     * 退还号源（供退号使用）
     */
    @Transactional
    public QuotaResultDTO returnQuota(Long scheduleId, Integer count) {
        DoctorSchedule schedule = doctorScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            QuotaResultDTO result = new QuotaResultDTO();
            result.setSuccess(false);
            result.setMessage("排班不存在");
            return result;
        }

        int newUsedQuota = Math.max(0, schedule.getUsedQuota() - count);
        int newAvailableQuota = schedule.getTotalQuota() - newUsedQuota;

        doctorScheduleMapper.updateQuota(scheduleId, newUsedQuota, newAvailableQuota);

        // 更新计划统计
        updatePlanStatistics(schedule.getPlanId());

        log.info("退还号源：排班ID={}, 退还数量={}, 剩余号源={}",
                scheduleId, count, newAvailableQuota);

        QuotaResultDTO result = new QuotaResultDTO();
        result.setSuccess(true);
        result.setRemainingQuota(newAvailableQuota);
        result.setMessage("退还成功");
        return result;
    }

    /**
     *停诊
     */
    @Transactional
    public void stopSchedule(Long scheduleId, Long operatorId, String reason) {
        DoctorSchedule schedule = doctorScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排班不存在");
        }

        doctorScheduleMapper.updateStatus(scheduleId, "停诊");

        //记录日志
        logAdjust(scheduleId, "status", schedule.getStatus(), "停诊",
                "admin_urgent", operatorId, reason);

        log.info("停诊：排班ID={}, 操作人={}, 原因={}", scheduleId, operatorId, reason);
    }

    /**
     * 恢复出诊
     */
    @Transactional
    public void resumeSchedule(Long scheduleId, Long operatorId) {
        doctorScheduleMapper.updateStatus(scheduleId, "正常");

        //记录日志
        logAdjust(scheduleId, "status", "停诊", "正常",
                "admin_urgent", operatorId, "恢复出诊");

        log.info("恢复出诊：排班ID={}, 操作人={}", scheduleId, operatorId);
    }

    /**
     * 获取剩余号源
     */
    public Integer getAvailableQuota(Long scheduleId) {
        DoctorSchedule schedule = doctorScheduleMapper.selectById(scheduleId);
        return schedule != null ? schedule.getAvailableQuota() : 0;
    }

    /**
     * 更新计划统计
     */
    private void updatePlanStatistics(Long planId) {
        List<DoctorSchedule> schedules = doctorScheduleMapper.selectByPlanId(planId);

        int totalSchedules = schedules.size();
        int totalQuota = schedules.stream().mapToInt(DoctorSchedule::getTotalQuota).sum();
        int usedQuota = schedules.stream().mapToInt(DoctorSchedule::getUsedQuota).sum();

        SchedulePlan plan = new SchedulePlan();
        plan.setId(planId);
        plan.setTotalSchedules(totalSchedules);
        plan.setTotalQuota(totalQuota);
        schedulePlanMapper.update(plan);
    }

    /**
     * 记录调整日志
     */
    private void logAdjust(Long scheduleId, String fieldName, String oldValue,
                          String newValue, String adjustType, Long adjustBy, String remark) {
        ScheduleAdjustLog log = new ScheduleAdjustLog();
        log.setScheduleId(scheduleId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setAdjustType(adjustType);
        log.setAdjustBy(adjustBy);
        log.setRemark(remark);
        scheduleAdjustLogMapper.insert(log);
    }
}