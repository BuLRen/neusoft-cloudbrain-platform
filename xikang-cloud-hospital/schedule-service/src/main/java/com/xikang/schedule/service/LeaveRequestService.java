package com.xikang.schedule.service;

import com.xikang.schedule.dto.DifyLeaveAdjustResult;
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
     * <p>只落库，不触发 AI。AI 替班推荐由 {@link #processLeave} 在审批通过后调用。
     * <p>autoProcess 参数保留兼容性，当前忽略（审批流程才触发 Dify）。
     */
    @Transactional
    public LeaveRequest createLeave(LeaveRequest leaveRequest, boolean autoProcess) {
        leaveRequest.setStatus("待审批");
        leaveRequest.setAutoProcessed(autoProcess);
        leaveRequestMapper.insert(leaveRequest);

        log.info("创建请假申请：医生ID={}, 日期={}, 时段={}, autoProcess={}",
                leaveRequest.getPhysicianId(), leaveRequest.getLeaveDate(),
                leaveRequest.getTimeSlot(), autoProcess);

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
     * <p>审批通过后触发：先调 Dify 7 节点工作流拿 AI 推荐替班方案，再落库为「待确认」调整记录。
     * <p>Dify 调用失败时不阻塞业务，降级为「待管理员手动指定替班」。
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

        // 创建调整申请基础信息
        ScheduleAdjustRequest adjustRequest = new ScheduleAdjustRequest();
        adjustRequest.setScheduleId(schedule.getId());
        adjustRequest.setAdjustType("leave_ai");
        adjustRequest.setOldPhysicianId(schedule.getPhysicianId());
        adjustRequest.setOldStatus(schedule.getStatus());
        adjustRequest.setOldQuota(schedule.getTotalQuota());
        adjustRequest.setReason(leave.getReason());
        adjustRequest.setTriggeredBy(leave.getPhysicianId());
        adjustRequest.setAffectPatients(schedule.getUsedQuota());

        // 调 Dify 工作流生成 AI 替班方案（失败降级为 null，由管理员手动指定）
        try {
            DifyLeaveAdjustResult aiResult = difyIntegrationService.processLeaveWithAI(leave, schedule);
            if (aiResult != null) {
                if (aiResult.getSubstitutePhysicianId() != null) {
                    adjustRequest.setNewPhysicianId(aiResult.getSubstitutePhysicianId());
                }
                if (aiResult.getSubstitutePhysicianName() != null) {
                    adjustRequest.setSubstitutePhysicianName(aiResult.getSubstitutePhysicianName());
                }
                if (aiResult.getReason() != null) {
                    adjustRequest.setReason(aiResult.getReason());
                }
                adjustRequest.setAiSuggestion(aiResult.getRawJson());
                log.info("AI 替班方案生成成功：leaveId={}, substituteId={}, source={}",
                        leaveId, aiResult.getSubstitutePhysicianId(), aiResult.getSource());
            }
        } catch (Exception e) {
            log.warn("AI 替班方案生成失败，降级为手动指定：leaveId={}, err={}", leaveId, e.getMessage());
            adjustRequest.setAiSuggestion("AI 推理失败：" + e.getMessage());
        }

        return scheduleAdjustService.createRequest(adjustRequest);
    }

    /**
     * 查询某请假记录对应的排班（给 Controller 的上下文聚合端点用）
     */
    public DoctorSchedule findScheduleForLeave(Long leaveId) {
        LeaveRequest leave = leaveRequestMapper.selectById(leaveId);
        if (leave == null) return null;
        return doctorScheduleMapper.selectByPhysicianAndDate(
                leave.getPhysicianId(), leave.getLeaveDate(),
                leave.getTimeSlot() != null ? leave.getTimeSlot() : "上午");
    }

    /**
     * 查询可用替班医生
     * <p>修复：原代码 s.getPhysicianId().equals(excludePhysicianId) 写反，
     * 应该是 !equals 排除请假医生本人。
     */
    public List<DoctorSchedule> getAvailableSubstitutes(Long departmentId, LocalDate date, String timeSlot, Long excludePhysicianId) {
        List<DoctorSchedule> schedules = doctorScheduleMapper.selectAvailable(departmentId, date);
        return schedules.stream()
                .filter(s -> !s.getPhysicianId().equals(excludePhysicianId))
                .filter(s -> s.getStatus() != null && s.getStatus().equals("正常"))
                .filter(s -> s.getAvailableQuota() != null && s.getAvailableQuota() > 0)
                .collect(java.util.stream.Collectors.toList());
    }
}