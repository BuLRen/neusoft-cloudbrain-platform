package com.xikang.schedule.controller;

import com.xikang.schedule.dto.AiGeneratePlanRequest;
import com.xikang.schedule.dto.AiGeneratePlanResult;
import com.xikang.schedule.dto.AiGenerateTaskView;
import com.xikang.schedule.service.AiGenerateTaskService;
import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.SchedulePlan;
import com.xikang.schedule.service.CozeIntegrationService;
import com.xikang.schedule.service.DoctorScheduleService;
import com.xikang.schedule.service.SchedulePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排班管理 Controller
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final SchedulePlanService schedulePlanService;
    private final DoctorScheduleService doctorScheduleService;
    private final CozeIntegrationService cozeIntegrationService;
    private final AiGenerateTaskService aiGenerateTaskService;

    // ==================== 排班计划 API ====================

    /**
     * 获取所有排班计划
     */
    @GetMapping("/plans")
    public Map<String, Object> getAllPlans(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status) {

        List<SchedulePlan> plans;
        if (departmentId != null && month != null) {
            plans = schedulePlanService.getPlansByDepartmentAndMonth(departmentId, month);
        } else if (status != null) {
            plans = schedulePlanService.getPlansByStatus(status);
        } else {
            plans = schedulePlanService.getAllPlans();
        }

        return success(plans);
    }

    /**
     * 获取排班计划详情
     */
    @GetMapping("/plan/{planId}")
    public Map<String, Object> getPlan(@PathVariable Long planId) {
        SchedulePlan plan = schedulePlanService.getPlanById(planId);
        if (plan == null) {
            return error("排班计划不存在");
        }
        return success(plan);
    }

    /**
     * 创建排班计划
     */
    @PostMapping("/plan")
    public Map<String, Object> createPlan(@RequestBody SchedulePlan plan) {
        SchedulePlan created = schedulePlanService.createPlan(plan);
        return success(created);
    }

    /**
     * AI 生成排班计划（异步提交：立刻返回任务视图，后台跑）。
     */
    @PostMapping("/plan/ai-generate")
    public Map<String, Object> generatePlanByAI(@RequestBody AiGeneratePlanRequest request) {
        AiGenerateTaskView view = aiGenerateTaskService.submit(request);
        return success(view);
    }

    /**
     * 查询进行中或刚结束的 AI 排班任务。
     */
    @GetMapping("/plan/ai-generate/active")
    public Map<String, Object> getActiveAiTask(
            @RequestParam Long operatorId,
            @RequestParam Long departmentId,
            @RequestParam String month) {
        AiGenerateTaskView view = aiGenerateTaskService.getActive(operatorId, departmentId, month);
        if (view == null) {
            return success(null);
        }
        return success(view);
    }

    /**
     * 取消进行中的 AI 排班任务。
     */
    @DeleteMapping("/plan/ai-generate/active")
    public Map<String, Object> cancelActiveAiTask(
            @RequestParam Long operatorId,
            @RequestParam Long departmentId,
            @RequestParam String month) {
        boolean cancelled = aiGenerateTaskService.cancel(operatorId, departmentId, month);
        if (!cancelled) {
            return error("没有可取消的运行中任务");
        }
        return success("已取消");
    }

    /**
     * 更新排班计划
     */
    @PutMapping("/plan/{planId}")
    public Map<String, Object> updatePlan(@PathVariable Long planId, @RequestBody SchedulePlan plan) {
        plan.setId(planId);
        schedulePlanService.updatePlan(plan);
        return success("更新成功");
    }

    /**
     * 发布排班计划
     */
    @PostMapping("/plan/{planId}/publish")
    public Map<String, Object> publishPlan(@PathVariable Long planId, @RequestBody Map<String, Long> body) {
        Long operatorId = body.get("operatorId");
        schedulePlanService.publishPlan(planId, operatorId);
        return success("发布成功");
    }

    /**
     * 删除排班计划
     */
    @DeleteMapping("/plan/{planId}")
    public Map<String, Object> deletePlan(@PathVariable Long planId) {
        schedulePlanService.deletePlan(planId);
        return success("删除成功");
    }

    /**
     * 获取排班日历
     */
    @GetMapping("/calendar")
    public Map<String, Object> getCalendar(
            @RequestParam Long departmentId,
            @RequestParam String month) {

        List<?> calendar = schedulePlanService.getCalendarByMonth(departmentId, month);
        return success(calendar);
    }

    // ==================== 医生出诊 API ====================

    /**
     * 获取可用排班（供挂号使用）
     */
    @GetMapping("/available")
    public Map<String, Object> getAvailable(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String timeSlot) {

        List<DoctorSchedule> schedules = doctorScheduleService.getAvailable(departmentId, date);

        if (timeSlot != null) {
            schedules = schedules.stream()
                    .filter(s -> s.getTimeSlot().equals(timeSlot))
                    .collect(java.util.stream.Collectors.toList());
        }

        return success(schedules);
    }

    /**
     * 获取日期范围的可用排班
     */
    @GetMapping("/available/range")
    public Map<String, Object> getAvailableByDateRange(
            @RequestParam Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<DoctorSchedule> schedules = doctorScheduleService.getAvailableByDateRange(departmentId, startDate, endDate);
        return success(schedules);
    }

    /**
     * 获取排班详情
     */
    @GetMapping("/detail/{scheduleId}")
    public Map<String, Object> getDetail(@PathVariable Long scheduleId) {
        DoctorSchedule schedule = doctorScheduleService.getById(scheduleId);
        if (schedule == null) {
            return error("排班不存在");
        }
        return success(schedule);
    }

    /**
     * 获取某医生的排班
     */
    @GetMapping("/physician/{physicianId}")
    public Map<String, Object> getByPhysician(
            @PathVariable Long physicianId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<DoctorSchedule> schedules = doctorScheduleService.getByPhysician(physicianId, startDate, endDate);
        return success(schedules);
    }

    /**
     * 获取排班计划下的所有排班
     */
    @GetMapping("/plan/{planId}/schedules")
    public Map<String, Object> getByPlanId(@PathVariable Long planId) {
        List<DoctorSchedule> schedules = doctorScheduleService.getByPlanId(planId);
        return success(schedules);
    }

    /**
     * 创建排班
     */
    @PostMapping("/schedule")
    public Map<String, Object> createSchedule(@RequestBody DoctorSchedule schedule) {
        DoctorSchedule created = doctorScheduleService.create(schedule);
        return success(created);
    }

    /**
     * 更新排班
     */
    @PutMapping("/schedule/{scheduleId}")
    public Map<String, Object> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody Map<String, Object> body) {

        DoctorSchedule schedule = new DoctorSchedule();
        if (body.containsKey("physicianId")) {
            schedule.setPhysicianId(((Number) body.get("physicianId")).longValue());
        }
        if (body.containsKey("workDate")) {
            schedule.setWorkDate(java.time.LocalDate.parse((String) body.get("workDate")));
        }
        if (body.containsKey("timeSlot")) {
            schedule.setTimeSlot((String) body.get("timeSlot"));
        }
        if (body.containsKey("totalQuota")) {
            schedule.setTotalQuota((Integer) body.get("totalQuota"));
        }
        if (body.containsKey("status")) {
            schedule.setStatus((String) body.get("status"));
        }
        if (body.containsKey("aiSuggestion")) {
            schedule.setAiSuggestion((String) body.get("aiSuggestion"));
        }

        Long operatorId = body.containsKey("operatorId") ?
                ((Number) body.get("operatorId")).longValue() : null;
        String remark = (String) body.get("remark");

        doctorScheduleService.update(scheduleId, schedule, operatorId, remark);
        return success("更新成功");
    }

    /**
     * 停诊
     */
    @PostMapping("/schedule/{scheduleId}/stop")
    public Map<String, Object> stopSchedule(
            @PathVariable Long scheduleId,
            @RequestBody Map<String, Object> body) {

        Long operatorId = ((Number) body.get("operatorId")).longValue();
        String reason = (String) body.get("reason");

        doctorScheduleService.stopSchedule(scheduleId, operatorId, reason);
        return success("已停诊");
    }

    /**
     * 恢复出诊
     */
    @PostMapping("/schedule/{scheduleId}/resume")
    public Map<String, Object> resumeSchedule(
            @PathVariable Long scheduleId,
            @RequestBody Map<String, Object> body) {

        Long operatorId = ((Number) body.get("operatorId")).longValue();
        doctorScheduleService.resumeSchedule(scheduleId, operatorId);
        return success("已恢复");
    }

    // ==================== 号源管理 API（供其他服务调用） ====================

    /**
     * 扣减号源
     */
    @PostMapping("/quota/deduct")
    public Map<String, Object> deductQuota(@RequestBody Map<String, Object> body) {
        Long scheduleId = ((Number) body.get("scheduleId")).longValue();
        Integer count = (Integer) body.get("count");

        var result = doctorScheduleService.deductQuota(
                new com.xikang.schedule.dto.QuotaDeductDTO() {{
                    setScheduleId(scheduleId);
                    setCount(count);
                    setRegisterId(body.containsKey("registerId") ?
                            ((Number) body.get("registerId")).longValue() : null);
                }}
        );

        return success(result);
    }

    /**
     * 退还号源
     */
    @PostMapping("/quota/return")
    public Map<String, Object> returnQuota(@RequestBody Map<String, Object> body) {
        Long scheduleId = ((Number) body.get("scheduleId")).longValue();
        Integer count = (Integer) body.get("count");

        var result = doctorScheduleService.returnQuota(scheduleId, count);
        return success(result);
    }

    /**
     * 查询剩余号源
     */
    @GetMapping("/quota/{scheduleId}")
    public Map<String, Object> getQuota(@PathVariable Long scheduleId) {
        Integer available = doctorScheduleService.getAvailableQuota(scheduleId);
        return success(available);
    }

    /**
     * 号源分析（供 Coze 调用）
     */
    @GetMapping("/quota-analysis")
    public Map<String, Object> quotaAnalysis(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return success(new HashMap<>());
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
