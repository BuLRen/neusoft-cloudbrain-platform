package com.xikang.medtech.controller;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.medtech.ai.DifyAiProperties;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.service.FollowUpEnrollmentBackfillService;
import com.xikang.medtech.service.FollowUpMonitoringService;
import com.xikang.medtech.service.FollowUpShiftAiTaskService;
import com.xikang.medtech.service.FollowUpShiftChangeService;
import com.xikang.medtech.service.FollowUpShiftScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/shift/admin")
@RequiredArgsConstructor
public class FollowUpShiftAdminController {

    private final FollowUpShiftScheduleService scheduleService;
    private final FollowUpShiftChangeService changeService;
    private final FollowUpShiftAiTaskService aiTaskService;
    private final FollowUpMonitoringService monitoringService;
    private final FollowUpEnrollmentBackfillService enrollmentBackfillService;
    private final DifyAiProperties difyAiProperties;

    private void requireAdmin() {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可操作随访排班管理");
        }
    }

    @GetMapping("/plan")
    public Result<Map<String, Object>> getPlan(
        @RequestParam Long departmentId,
        @RequestParam String month
    ) {
        requireAdmin();
        return Result.success(scheduleService.getPlan(departmentId, month));
    }

    @GetMapping("/shifts")
    public Result<List<Map<String, Object>>> listShifts(
        @RequestParam Long departmentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        requireAdmin();
        return Result.success(scheduleService.listDepartmentShifts(departmentId, from, to));
    }

    @PostMapping("/sync-enrollment")
    public Result<Map<String, Object>> syncDepartmentEnrollment(@RequestBody Map<String, Object> request) {
        requireAdmin();
        Long departmentId = request.get("departmentId") != null
            ? Long.valueOf(String.valueOf(request.get("departmentId")))
            : null;
        if (departmentId == null) {
            throw new BusinessException("departmentId 不能为空");
        }
        Integer batchSize = request.get("batchSize") != null
            ? Integer.valueOf(String.valueOf(request.get("batchSize")))
            : null;
        Integer maxBatches = request.get("maxBatches") != null
            ? Integer.valueOf(String.valueOf(request.get("maxBatches")))
            : null;
        Map<String, Object> result = enrollmentBackfillService.backfillDepartment(departmentId, batchSize, maxBatches);
        return Result.success("已同步看诊结束患者到随访池", result);
    }

    @GetMapping("/dify/shift-status")
    public Result<Map<String, Object>> shiftDifyStatus() {
        requireAdmin();
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("difyEnabled", difyAiProperties.isEnabled());
        status.put("difyBaseUrl", difyAiProperties.getBaseUrl());
        status.put("shiftWorkflowEnabled", difyAiProperties.isFollowUpShiftScheduleEnabled());
        status.put("shiftApiKeyConfigured", !difyAiProperties.resolveFollowUpShiftScheduleApiKey().isBlank());
        if (!difyAiProperties.isFollowUpShiftScheduleEnabled()) {
            status.put("disabledReason", difyAiProperties.describeFollowUpShiftScheduleDisabledReason());
        }
        return Result.success(status);
    }

    @PostMapping("/ai-generate")
    public Result<Map<String, Object>> aiGenerate(@RequestBody Map<String, Object> request) {
        requireAdmin();
        Long departmentId = Long.valueOf(String.valueOf(request.get("departmentId")));
        String month = String.valueOf(request.get("month"));
        String departmentName = request.get("departmentName") != null
            ? String.valueOf(request.get("departmentName"))
            : scheduleService.resolveDepartmentName(departmentId);
        return Result.success("AI 排班任务已提交", aiTaskService.submit(departmentId, departmentName, month));
    }

    @GetMapping("/ai-generate/active")
    public Result<Map<String, Object>> aiGenerateActive(
        @RequestParam Long departmentId,
        @RequestParam String month
    ) {
        requireAdmin();
        return Result.success(aiTaskService.getActive(departmentId, month));
    }

    @PostMapping("/publish/{planId}")
    public Result<Map<String, Object>> publish(@PathVariable Long planId) {
        requireAdmin();
        return Result.success("排班已发布", scheduleService.publishPlan(planId));
    }

    @GetMapping("/change-requests/pending")
    public Result<List<Map<String, Object>>> pendingChangeRequests(
        @RequestParam(required = false) Long departmentId
    ) {
        requireAdmin();
        return Result.success(changeService.listPendingRequests(departmentId));
    }

    @GetMapping("/change-requests/count")
    public Result<Integer> pendingCount(@RequestParam(required = false) Long departmentId) {
        requireAdmin();
        return Result.success(changeService.countPendingRequests(departmentId));
    }

    @PostMapping("/change-requests/{id}/approve")
    public Result<Map<String, Object>> approve(
        @PathVariable Long id,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        requireAdmin();
        String note = body != null && body.get("adminNote") != null ? String.valueOf(body.get("adminNote")) : null;
        return Result.success("已同意调班", changeService.reviewRequest(id, true, note));
    }

    @PostMapping("/change-requests/{id}/reject")
    public Result<Map<String, Object>> reject(
        @PathVariable Long id,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        requireAdmin();
        String note = body != null && body.get("adminNote") != null ? String.valueOf(body.get("adminNote")) : null;
        return Result.success("已驳回调班", changeService.reviewRequest(id, false, note));
    }

    @PostMapping("/monitoring/assign")
    public Result<Map<String, Object>> assignMonitoring(@RequestBody Map<String, Object> request) {
        requireAdmin();
        Long registerId = request.get("registerId") != null ? Long.valueOf(String.valueOf(request.get("registerId"))) : null;
        Long employeeId = request.get("employeeId") != null ? Long.valueOf(String.valueOf(request.get("employeeId"))) : null;
        Long departmentId = request.get("departmentId") != null ? Long.valueOf(String.valueOf(request.get("departmentId"))) : null;
        return Result.success("已分配监视医生", monitoringService.assignMonitoring(registerId, employeeId, departmentId));
    }

    @PostMapping("/monitoring/random-assign")
    public Result<Map<String, Object>> randomAssignMonitoring(@RequestBody Map<String, Object> request) {
        requireAdmin();
        Long departmentId = request.get("departmentId") != null
            ? Long.valueOf(String.valueOf(request.get("departmentId")))
            : null;
        Map<String, Object> result = monitoringService.randomAssignDepartment(departmentId);
        int assigned = result.get("assigned") instanceof Number number ? number.intValue() : 0;
        return Result.success(
            assigned > 0 ? "已随机分配 " + assigned + " 名患者" : "暂无待分配患者",
            result
        );
    }

    @GetMapping("/monitoring/load-summary")
    public Result<Map<String, Object>> monitoringLoadSummary(@RequestParam Long departmentId) {
        requireAdmin();
        return Result.success(monitoringService.getMonitoringLoadSummary(departmentId));
    }

    @GetMapping("/monitoring/transfer-requests/pending")
    public Result<List<Map<String, Object>>> pendingTransferRequests(
        @RequestParam(required = false) Long departmentId
    ) {
        requireAdmin();
        return Result.success(monitoringService.listPendingTransferRequests(departmentId));
    }

    @GetMapping("/monitoring/transfer-requests/count")
    public Result<Integer> pendingTransferCount(@RequestParam(required = false) Long departmentId) {
        requireAdmin();
        return Result.success(monitoringService.countPendingTransferRequests(departmentId));
    }

    @PostMapping("/monitoring/transfer-requests/{id}/approve")
    public Result<Map<String, Object>> approveTransfer(
        @PathVariable Long id,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        requireAdmin();
        String note = body != null && body.get("adminNote") != null ? String.valueOf(body.get("adminNote")) : null;
        return Result.success("已同意调换", monitoringService.reviewTransferRequest(id, true, note));
    }

    @PostMapping("/monitoring/transfer-requests/{id}/reject")
    public Result<Map<String, Object>> rejectTransfer(
        @PathVariable Long id,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        requireAdmin();
        String note = body != null && body.get("adminNote") != null ? String.valueOf(body.get("adminNote")) : null;
        return Result.success("已驳回调换", monitoringService.reviewTransferRequest(id, false, note));
    }
}
