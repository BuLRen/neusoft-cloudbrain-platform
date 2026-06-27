package com.xikang.medtech.controller;

import com.xikang.common.result.Result;
import com.xikang.medtech.service.FollowUpCommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medtech/follow-up/communication")
@RequiredArgsConstructor
public class FollowUpCommunicationController {

    private final FollowUpCommunicationService followUpCommunicationService;

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> listSessions(
        @RequestParam(required = false) Long departmentId
    ) {
        return Result.success(followUpCommunicationService.listSessions(departmentId));
    }

    @PostMapping("/sessions")
    public Result<Map<String, Object>> openSession(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            return Result.error("registerId 不能为空");
        }
        return Result.success(followUpCommunicationService.openSession(registerId));
    }

    @GetMapping("/sessions/{id}")
    public Result<Map<String, Object>> getSession(@PathVariable Long id) {
        return Result.success(followUpCommunicationService.getSession(id));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> listMessages(
        @PathVariable Long id,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset
    ) {
        return Result.success(followUpCommunicationService.listMessages(id, limit, offset));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> sendDoctorMessage(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String content = request.get("content") != null ? String.valueOf(request.get("content")) : "";
        return Result.success(followUpCommunicationService.sendDoctorMessage(id, content));
    }

    @PostMapping("/sessions/{id}/patient-messages")
    public Result<Map<String, Object>> sendPatientMessage(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String content = request.get("content") != null ? String.valueOf(request.get("content")) : "";
        boolean autoAiReply = !Boolean.FALSE.equals(request.get("autoAiReply"));
        return Result.success(followUpCommunicationService.sendPatientMessage(id, content, autoAiReply));
    }

    @PostMapping("/sessions/{id}/ai-reply")
    public Result<Map<String, Object>> triggerAiReply(@PathVariable Long id) {
        return Result.success(followUpCommunicationService.triggerAiReply(id));
    }

    @PatchMapping("/sessions/{id}/ai-escalation")
    public Result<Void> setAiEscalation(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        boolean enabled = !Boolean.FALSE.equals(request.get("enabled"));
        followUpCommunicationService.setAiEscalation(id, enabled);
        return Result.success();
    }

    @PostMapping("/case-summary/generate")
    public Result<Map<String, Object>> generateCaseSummary(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        if (registerId == null) {
            return Result.error("registerId 不能为空");
        }
        return Result.success(followUpCommunicationService.generateCaseSummary(registerId));
    }

    @GetMapping("/case-summary/{registerId}")
    public Result<Map<String, Object>> getLatestCaseSummary(@PathVariable Long registerId) {
        return Result.success(followUpCommunicationService.getLatestCaseSummary(registerId));
    }

    @GetMapping("/case-summary/{registerId}/shared")
    public Result<Map<String, Object>> getSharedCaseSummary(@PathVariable Long registerId) {
        return Result.success(followUpCommunicationService.getSharedCaseSummaryForPatient(registerId));
    }

    @PutMapping("/case-summary/{id}")
    public Result<Map<String, Object>> updateCaseSummary(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String doctorContent = request.get("doctorContent") != null ? String.valueOf(request.get("doctorContent")) : "";
        return Result.success(followUpCommunicationService.updateCaseSummary(id, doctorContent));
    }

    @PostMapping("/case-summary/{id}/approve")
    public Result<Map<String, Object>> approveCaseSummary(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String doctorContent = request.get("doctorContent") != null ? String.valueOf(request.get("doctorContent")) : null;
        boolean sharedToPatient = Boolean.TRUE.equals(request.get("sharedToPatient"));
        return Result.success(followUpCommunicationService.approveCaseSummary(id, doctorContent, sharedToPatient));
    }

    @PostMapping("/case-summary/{id}/revoke")
    public Result<Map<String, Object>> revokeCaseSummary(@PathVariable Long id) {
        return Result.success(followUpCommunicationService.revokeCaseSummary(id));
    }

    @GetMapping("/patient-brief/{registerId}")
    public Result<Map<String, Object>> getPatientBrief(@PathVariable Long registerId) {
        return Result.success(followUpCommunicationService.getPatientBrief(registerId));
    }

    @GetMapping("/patient/session/{registerId}")
    public Result<Map<String, Object>> getPatientSession(@PathVariable Long registerId) {
        return Result.success(followUpCommunicationService.getOrCreatePatientSession(registerId));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
