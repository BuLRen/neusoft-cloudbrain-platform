package com.xikang.schedule.controller;

import com.xikang.schedule.entity.LeaveRequest;
import com.xikang.schedule.entity.ScheduleAdjustRequest;
import com.xikang.schedule.service.LeaveRequestService;
import com.xikang.schedule.service.ScheduleAdjustService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假管理 Controller
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveRequestService leaveRequestService;
    private final ScheduleAdjustService scheduleAdjustService;

    // ==================== 请假申请 API ====================

    /**
     * 获取所有请假申请
     */
    @GetMapping("/leave/list")
    public Map<String, Object> getAllLeaves(
            @RequestParam(required = false) Long physicianId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<LeaveRequest> leaves;
        if (physicianId != null && startDate != null && endDate != null) {
            leaves = leaveRequestService.getByPhysicianAndDateRange(physicianId, startDate, endDate);
        } else if (physicianId != null) {
            leaves = leaveRequestService.getByPhysician(physicianId);
        } else {
            leaves = leaveRequestService.getAll();
        }

        return success(leaves);
    }

    /**
     * 获取请假详情
     */
    @GetMapping("/leave/{leaveId}")
    public Map<String, Object> getLeave(@PathVariable Long leaveId) {
        LeaveRequest leave = leaveRequestService.getById(leaveId);
        if (leave == null) {
            return error("请假申请不存在");
        }
        return success(leave);
    }

    /**
     * 创建请假申请
     */
    @PostMapping("/leave/create")
    public Map<String, Object> createLeave(@RequestBody Map<String, Object> body) {
        LeaveRequest leave = new LeaveRequest();
        leave.setPhysicianId(((Number) body.get("physicianId")).longValue());

        if (body.containsKey("leaveDate")) {
            leave.setLeaveDate(LocalDate.parse((String) body.get("leaveDate")));
        }
        if (body.containsKey("timeSlot")) {
            leave.setTimeSlot((String) body.get("timeSlot"));
        }
        if (body.containsKey("leaveType")) {
            leave.setLeaveType((String) body.get("leaveType"));
        }
        if (body.containsKey("reason")) {
            leave.setReason((String) body.get("reason"));
        }
        if (body.containsKey("rawText")) {
            // TODO: 调用 Coze 解析原始文本
            leave.setRawText((String) body.get("rawText"));
        }

        boolean autoProcess = body.containsKey("autoProcess") && (Boolean) body.get("autoProcess");
        LeaveRequest created = leaveRequestService.createLeave(leave, autoProcess);

        return success(created);
    }

    /**
     * 审批请假
     */
    @PostMapping("/leave/{leaveId}/approve")
    public Map<String, Object> approveLeave(
            @PathVariable Long leaveId,
            @RequestBody Map<String, Object> body) {

        Long approverId = ((Number) body.get("approverId")).longValue();
        leaveRequestService.approveLeave(leaveId, approverId);

        // 自动处理请假（生成调整申请）
        ScheduleAdjustRequest adjustRequest = leaveRequestService.processLeave(leaveId);

        Map<String, Object> result = new HashMap<>();
        result.put("leave_id", leaveId);
        result.put("adjust_id", adjustRequest != null ? adjustRequest.getId() : null);
        result.put("status", "已批准");

        return success(result);
    }

    /**
     * 拒绝请假
     */
    @PostMapping("/leave/{leaveId}/reject")
    public Map<String, Object> rejectLeave(
            @PathVariable Long leaveId,
            @RequestBody Map<String, Object> body) {

        Long approverId = ((Number) body.get("approverId")).longValue();
        leaveRequestService.rejectLeave(leaveId, approverId);

        return success("已拒绝");
    }

    /**
     * 查询可用替班医生
     */
    @GetMapping("/substitutes")
    public Map<String, Object> getSubstitutes(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaveDate,
            @RequestParam(required = false) String timeSlot,
            @RequestParam Long excludePhysicianId) {

        var substitutes = leaveRequestService.getAvailableSubstitutes(
                departmentId, leaveDate, timeSlot, excludePhysicianId);

        return success(substitutes);
    }

    // ==================== 调整申请 API ====================

    /**
     * 获取待确认调整
     */
    @GetMapping("/adjust/pending")
    public Map<String, Object> getPendingAdjusts() {
        var adjusts = scheduleAdjustService.getPendingAdjusts();
        return success(adjusts);
    }

    /**
     * 获取调整详情
     */
    @GetMapping("/adjust/{adjustId}")
    public Map<String, Object> getAdjust(@PathVariable Long adjustId) {
        var adjust = scheduleAdjustService.getById(adjustId);
        if (adjust == null) {
            return error("调整申请不存在");
        }
        return success(adjust);
    }

    /**
     * 确认调整
     */
    @PostMapping("/adjust/confirm")
    public Map<String, Object> confirmAdjust(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        Long confirmedBy = ((Number) body.get("confirmedBy")).longValue();
        String remark = (String) body.get("remark");

        scheduleAdjustService.confirmAdjust(requestId, confirmedBy, remark);
        return success("确认成功");
    }

    /**
     * 驳回调整
     */
    @PostMapping("/adjust/reject")
    public Map<String, Object> rejectAdjust(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        Long rejectedBy = ((Number) body.get("rejectedBy")).longValue();
        String reason = (String) body.get("reason");

        scheduleAdjustService.rejectAdjust(requestId, rejectedBy, reason);
        return success("已驳回");
    }

    /**
     * 创建紧急调整申请
     */
    @PostMapping("/adjust/urgent")
    public Map<String, Object> createUrgentAdjust(@RequestBody Map<String, Object> body) {
        ScheduleAdjustRequest request = new ScheduleAdjustRequest();
        request.setScheduleId(((Number) body.get("scheduleId")).longValue());
        request.setAdjustType("admin_urgent");
        request.setTriggeredBy(((Number) body.get("operatorId")).longValue());
        request.setReason((String) body.get("reason"));

        if (body.containsKey("newPhysicianId")) {
            request.setNewPhysicianId(((Number) body.get("newPhysicianId")).longValue());
        }
        if (body.containsKey("newStatus")) {
            request.setNewStatus((String) body.get("newStatus"));
        }
        if (body.containsKey("newQuota")) {
            request.setNewQuota((Integer) body.get("newQuota"));
        }

        ScheduleAdjustRequest created = scheduleAdjustService.createRequest(request);
        return success(created);
    }

    /**
     * 获取调整日志
     */
    @GetMapping("/adjust/logs/{scheduleId}")
    public Map<String, Object> getAdjustLogs(@PathVariable Long scheduleId) {
        var logs = scheduleAdjustService.getAdjustLogs(scheduleId);
        return success(logs);
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }
}