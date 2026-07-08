package com.xikang.medtech.controller;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.medtech.ai.DifyAiProperties;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.mapper.FollowUpClinicalMapper;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.service.FollowUpEnqueueDifyService;
import com.xikang.medtech.service.FollowUpEnrollmentBackfillService;
import com.xikang.medtech.service.FollowUpPriorityScorer;
import com.xikang.medtech.service.FollowUpShiftEnqueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/admin")
@RequiredArgsConstructor
public class FollowUpAdminController {

    private final FollowUpEnrollmentBackfillService backfillService;
    private final FollowUpShiftEnqueueService shiftEnqueueService;
    private final FollowUpEnqueueDifyService enqueueDifyService;
    private final FollowUpPriorityScorer priorityScorer;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpClinicalMapper clinicalMapper;
    private final DifyAiProperties difyAiProperties;

    @PostMapping("/backfill/enrollment")
    public Result<Map<String, Object>> backfillEnrollment(@RequestBody(required = false) Map<String, Object> request) {
        requireAdmin();
        Integer batchSize = request != null && request.get("batchSize") != null
            ? Integer.valueOf(String.valueOf(request.get("batchSize")))
            : null;
        Integer maxBatches = request != null && request.get("maxBatches") != null
            ? Integer.valueOf(String.valueOf(request.get("maxBatches")))
            : null;
        Long departmentId = request != null ? toLong(request.get("departmentId")) : null;
        Map<String, Object> result = departmentId != null
            ? backfillService.backfillDepartment(departmentId, batchSize, maxBatches)
            : backfillService.backfillEnrollment(batchSize, maxBatches);
        return Result.success(
            departmentId != null ? "已同步看诊结束患者到随访池" : "回填完成",
            result
        );
    }

    @GetMapping("/dify/enqueue-status")
    public Result<Map<String, Object>> enqueueDifyStatus() {
        requireAdmin();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("difyEnabled", difyAiProperties.isEnabled());
        status.put("difyBaseUrl", difyAiProperties.getBaseUrl());
        status.put("enqueueWorkflowEnabled", difyAiProperties.isFollowUpEnqueueEnabled());
        status.put("enqueueApiKeyConfigured", !difyAiProperties.resolveFollowUpEnqueueApiKey().isBlank());
        status.put("endOutputKey", "enqueue_result_json");
        return Result.success(status);
    }

    /**
     * 测试 Dify 入队工作流连接。
     * dryRun=true 仅调用 Dify 不落库；dryRun=false 完整入队（写 contact_task 或 pending 队列）。
     */
    @PostMapping("/enqueue/test")
    public Result<Map<String, Object>> testEnqueue(@RequestBody Map<String, Object> request) {
        requireAdmin();
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        if (!dashboardMapper.isEligiblePatient(registerId)) {
            throw new BusinessException("该患者不符合随访纳入条件");
        }

        Long departmentId = toLong(request.get("departmentId"));
        if (departmentId == null) {
            departmentId = dashboardMapper.selectRegisterDepartmentId(registerId);
        }
        if (departmentId == null) {
            throw new BusinessException("无法确定科室");
        }

        String departmentName = resolveDepartmentName(departmentId);
        FollowUpPriorityResult priority = priorityScorer.score(registerId);
        LocalDateTime visitEndedAt = parseVisitEndedAt(request.get("visitEndedAt"));
        boolean dryRun = request.get("dryRun") == null || Boolean.parseBoolean(String.valueOf(request.get("dryRun")));

        if (dryRun) {
            Map<String, Object> planned = enqueueDifyService.planEnqueue(
                registerId,
                visitEndedAt.toLocalDate(),
                departmentId,
                departmentName,
                priority.getPriorityLevel(),
                toLong(request.get("preferMonitorEmployeeId"))
            );
            Map<String, Object> result = new LinkedHashMap<>(planned);
            result.put("dryRun", true);
            result.put("registerId", registerId);
            result.put("departmentId", departmentId);
            result.put("departmentName", departmentName);
            return Result.success(result);
        }

        Map<String, Object> result = shiftEnqueueService.applyEnqueue(
            registerId,
            visitEndedAt,
            departmentId,
            priority.getPriorityLevel(),
            toLong(request.get("preferMonitorEmployeeId"))
        );
        result.put("dryRun", false);
        result.put("departmentName", departmentName);
        return Result.success(result);
    }

    private void requireAdmin() {
        if (!MedtechAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "仅管理员可执行该操作");
        }
    }

    private String resolveDepartmentName(Long departmentId) {
        Map<String, Object> dept = clinicalMapper.selectRegisterDepartmentBriefByDepartmentId(departmentId);
        if (dept != null && dept.get("departmentName") != null) {
            return String.valueOf(dept.get("departmentName"));
        }
        return "";
    }

    private static LocalDateTime parseVisitEndedAt(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).trim());
        } catch (Exception ex) {
            return LocalDate.now().atTime(12, 0);
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
