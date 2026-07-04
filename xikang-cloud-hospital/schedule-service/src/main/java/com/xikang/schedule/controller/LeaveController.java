package com.xikang.schedule.controller;

import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.LeaveRequest;
import com.xikang.schedule.entity.ScheduleAdjustRequest;
import com.xikang.schedule.service.DifyIntegrationService;
import com.xikang.schedule.service.LeaveRequestService;
import com.xikang.schedule.service.ScheduleAdjustService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveRequestService leaveRequestService;
    private final ScheduleAdjustService scheduleAdjustService;
    private final DifyIntegrationService difyIntegrationService;

    /** Dify HTTP 节点回调时的简单鉴权 token（防止外部恶意调用） */
    @Value("${dify.callback-token:schedule-internal-2026}")
    private String difyCallbackToken;

    // ==================== 请假申请 API ====================

    /**
     * 获取所有请假申请
     */
    @GetMapping("/leave/list")
    public Map<String, Object> getAllLeaves(
            @RequestParam(required = false) Long physicianId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {

        List<LeaveRequest> leaves;
        if (physicianId != null && startDate != null && endDate != null) {
            leaves = leaveRequestService.getByPhysicianAndDateRange(physicianId, startDate, endDate);
        } else if (physicianId != null) {
            leaves = leaveRequestService.getByPhysician(physicianId);
        } else {
            leaves = leaveRequestService.getAll();
        }

        // 按状态过滤（前端"待审批"tab 用），后端已落"已拒绝/已批准"的不再返回
        if (status != null && !status.isBlank()) {
            leaves = leaves.stream()
                    .filter(l -> status.equals(l.getStatus()))
                    .toList();
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
            // TODO: 调用 Dify 解析原始文本
            leave.setRawText((String) body.get("rawText"));
        }

        boolean autoProcess = body.containsKey("autoProcess") && (Boolean) body.get("autoProcess");
        try {
            LeaveRequest created = leaveRequestService.createLeave(leave, autoProcess);
            return success(created);
        } catch (RuntimeException e) {
            log.warn("创建请假失败：{}", e.getMessage());
            return error(e.getMessage());
        }
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
     * 重新生成 AI 替班方案
     * <p>管理员驳回原 AI 方案后，可选择「重新生成」再次调 Dify 工作流。
     * <p>流程：把原 adjust 标记为「已驳回-重新生成」→ 用原 scheduleId 反查 leaveId
     * → 调用 LeaveRequestService.processLeave 重新跑 Dify → 返回新 adjust。
     */
    @PostMapping("/adjust/regen")
    public Map<String, Object> regenAdjust(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        Long operatorId = ((Number) body.get("operatorId")).longValue();
        String reason = body.containsKey("reason") ? (String) body.get("reason") : "重新生成";

        // 1. 标记原 adjust 为「已驳回-重新生成」
        ScheduleAdjustRequest oldAdjust = scheduleAdjustService.regenAdjust(requestId, operatorId, reason);

        // 2. 通过 scheduleId 反查 leaveId
        Long scheduleId = oldAdjust.getScheduleId();
        Long leaveId = leaveRequestService.findLeaveIdByScheduleId(scheduleId);
        if (leaveId == null) {
            return error("未找到对应的请假记录，无法重新生成");
        }

        // 3. 重新跑 Dify 工作流生成新方案
        ScheduleAdjustRequest newAdjust = leaveRequestService.processLeave(leaveId);
        if (newAdjust == null) {
            return error("重新生成失败：未找到对应排班");
        }

        return success(newAdjust);
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

    // ==================== Dify 回调：上下文聚合 API ====================

    /**
     * 上下文聚合端点（Dify HTTP 节点调用）
     * <p>给 Dify 替班工作流的节点 3 用，返回请假详情 + 候选医生 + 影响患者 + 科室规则。
     * <p>简单 token 鉴权（X-Dify-Token header），防止外部恶意调用。
     */
    @GetMapping("/adjust/context")
    public Map<String, Object> getAdjustContext(
            @RequestParam Long leaveId,
            @RequestHeader(value = "X-Dify-Token", required = false) String token) {

        if (!difyCallbackToken.equals(token)) {
            log.warn("Dify 回调鉴权失败：leaveId={}, tokenPresent={}", leaveId, token != null);
            return error("鉴权失败");
        }

        LeaveRequest leave = leaveRequestService.getById(leaveId);
        if (leave == null) {
            return error("请假记录不存在");
        }

        // 查找对应排班（拿到 departmentId / usedQuota）
        DoctorSchedule schedule = leaveRequestService.findScheduleForLeave(leaveId);
        if (schedule == null) {
            return error("未找到对应排班");
        }

        // 聚合候选医生 — 必须用和 findLeaveSubstitutes 完全一致的查询，
        // 否则 LLM 看到的候选（HTTP 节点 3）和守门员校验的候选（开始节点 brief）会不一致，
        // LLM 推荐的 ID 不在 brief 里 → 触发 fallback。
        List<Map<String, Object>> candidates = difyIntegrationService.findLeaveSubstitutesPublic(schedule, leave);

        // 组装返回
        Map<String, Object> leaveInfo = new LinkedHashMap<>();
        leaveInfo.put("id", leave.getId());
        leaveInfo.put("physicianId", leave.getPhysicianId());
        leaveInfo.put("leaveDate", leave.getLeaveDate() != null ? leave.getLeaveDate().toString() : null);
        leaveInfo.put("timeSlot", leave.getTimeSlot());
        leaveInfo.put("leaveType", leave.getLeaveType());
        leaveInfo.put("reason", leave.getReason());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("leaveInfo", leaveInfo);
        context.put("candidates", candidates);
        context.put("affectedPatientCount", schedule.getUsedQuota() != null ? schedule.getUsedQuota() : 0);
        context.put("departmentId", schedule.getDepartmentId());
        context.put("departmentRule", "该科室最少需 1 人在岗");
        context.put("statistics", Map.of(
                "totalCandidates", candidates.size(),
                "scheduleId", schedule.getId()
        ));

        log.info("Dify 上下文聚合：leaveId={}, candidates={}, affectedPatients={}",
                leaveId, candidates.size(), schedule.getUsedQuota());

        return success(context);
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