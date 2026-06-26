package com.xikang.schedule.service;

import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.LeaveRequest;
import com.xikang.schedule.entity.ScheduleAdjustRequest;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
import com.xikang.schedule.mapper.LeaveRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestMapper leaveRequestMapper;
    private final DoctorScheduleMapper doctorScheduleMapper;
    private final ScheduleAdjustService scheduleAdjustService;
    private final DifyIntegrationService difyIntegrationService;

    /**
     * 获取所有请假申请
     */
    public List<LeaveRequest> getAll() {
        return leaveRequestMapper.selectAll();
    }

    /**
     * 获取请假详情
     */
    public LeaveRequest getById(Long id) {
        return leaveRequestMapper.selectById(id);
    }

    /**
     * 获取某医生的请假列表
     */
    public List<LeaveRequest> getByPhysician(Long physicianId) {
        return leaveRequestMapper.selectByPhysician(physicianId);
    }

    /**
     * 获取某医生的请假列表（日期范围）
     */
    public List<LeaveRequest> getByPhysicianAndDateRange(Long physicianId, LocalDate startDate, LocalDate endDate) {
        return leaveRequestMapper.selectByPhysicianAndDateRange(physicianId, startDate, endDate);
    }

    /**
     * 获取某日期的请假申请
     */
    public List<LeaveRequest> getByDate(LocalDate date) {
        return leaveRequestMapper.selectByDate(date);
    }

    /**
     * 创建请假申请
     * 如果启用 Dify，会自动生成调整方案
     */
    @Transactional
    public LeaveRequest createLeave(LeaveRequest leaveRequest, boolean autoProcess) {
        leaveRequest.setStatus("待审批");
        leaveRequest.setAutoProcessed(autoProcess);
        leaveRequestMapper.insert(leaveRequest);

        log.info("创建请假申请：医生ID={}, 日期={}, 时段={}",
                leaveRequest.getPhysicianId(), leaveRequest.getLeaveDate(), leaveRequest.getTimeSlot());

        if (autoProcess) {
            // 调用 Dify 生成调整方案（预留，明天填充）
            try {
                difyIntegrationService.processLeaveWithAI(leaveRequest);
            } catch (Exception e) {
                log.warn("AI处理请假失败，将手动处理：{}", e.getMessage());
            }
        }

        return leaveRequest;
    }

    /**
     * 审批请假
     */
    @Transactional
    public void approveLeave(Long leaveId, Long approverId) {
        LeaveRequest leave = leaveRequestMapper.selectById(leaveId);
        if (leave == null) {
            throw new RuntimeException("请假申请不存在");
        }

        leaveRequestMapper.updateStatus(leaveId, "已批准", approverId, java.time.LocalDateTime.now());

        log.info("审批请假：请假ID={}, 审批人={}", leaveId, approverId);
    }

    /**
     * 拒绝请假
     */
    @Transactional
    public void rejectLeave(Long leaveId, Long approverId) {
        leaveRequestMapper.updateStatus(leaveId, "已拒绝", approverId, java.time.LocalDateTime.now());

        log.info("拒绝请假：请假ID={}, 审批人={}", leaveId, approverId);
    }

    /**
     * 处理请假（生成调整申请）
     * 由 Dify 触发或管理员手动触发
     */
    @Transactional
    public ScheduleAdjustRequest processLeave(Long leaveId) {
        LeaveRequest leave = leaveRequestMapper.selectById(leaveId);
        if (leave == null) {
            throw new RuntimeException("请假申请不存在");
        }

        // 查找对应的排班
        DoctorSchedule schedule = doctorScheduleMapper.selectByPhysicianAndDate(
                leave.getPhysicianId(), leave.getLeaveDate(),
                leave.getTimeSlot() != null ? leave.getTimeSlot() : "上午");

        if (schedule == null) {
            log.warn("未找到对应排班，请假ID={}", leaveId);
            return null;
        }

        // 创建调整申请
        ScheduleAdjustRequest adjustRequest = new ScheduleAdjustRequest();
        adjustRequest.setScheduleId(schedule.getId());
        adjustRequest.setAdjustType("leave_ai");
        adjustRequest.setOldPhysicianId(schedule.getPhysicianId());
        adjustRequest.setOldStatus(schedule.getStatus());
        adjustRequest.setOldQuota(schedule.getTotalQuota());
        adjustRequest.setReason(leave.getReason());
        adjustRequest.setTriggeredBy(leave.getPhysicianId());
        adjustRequest.setAffectPatients(schedule.getUsedQuota());

        return scheduleAdjustService.createRequest(adjustRequest);
    }

    /**
     * 查询可用替班医生
     */
    public List<DoctorSchedule> getAvailableSubstitutes(Long departmentId, LocalDate date, String timeSlot, Long excludePhysicianId) {
        List<DoctorSchedule> schedules = doctorScheduleMapper.selectAvailable(departmentId, date);
        return schedules.stream()
                .filter(s -> s.getPhysicianId().equals(excludePhysicianId))
                .filter(s -> s.getStatus().equals("正常"))
                .filter(s -> s.getAvailableQuota() > 0)
                .collect(java.util.stream.Collectors.toList());
    }
}