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

    private final FollowUpCommunicationService communicationService;

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> listSessions(@RequestParam(required = false) Long departmentId) {
        return Result.success(communicationService.listSessions(departmentId));
    }

    @PostMapping("/sessions")
    public Result<Map<String, Object>> openSession(@RequestBody Map<String, Object> request) {
        Long registerId = request.get("registerId") != null ? Long.valueOf(String.valueOf(request.get("registerId"))) : null;
        Long departmentId = request.get("departmentId") != null ? Long.valueOf(String.valueOf(request.get("departmentId"))) : null;
        return Result.success(communicationService.openSession(registerId, departmentId));
    }

    @GetMapping("/sessions/{id}")
    public Result<Map<String, Object>> getSession(@PathVariable Long id) {
        return Result.success(communicationService.getSession(id));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> listMessages(
        @PathVariable Long id,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset
    ) {
        return Result.success(communicationService.listMessages(id, limit, offset));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> sendDoctorMessage(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String messageType = request.get("messageType") != null ? String.valueOf(request.get("messageType")) : "text";
        if ("drug_card".equals(messageType) || "diagnosis_card".equals(messageType) || "registration_card".equals(messageType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cardPayload = request.get("cardPayload") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
            return Result.success(communicationService.sendDoctorCard(id, messageType, cardPayload));
        }
        String content = request.get("content") != null ? String.valueOf(request.get("content")) : null;
        return Result.success(communicationService.sendDoctorMessage(id, content));
    }

    @PostMapping("/sessions/{id}/patient-messages")
    public Result<Map<String, Object>> sendPatientMessage(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String content = request.get("content") != null ? String.valueOf(request.get("content")) : null;
        boolean autoAiReply = request.get("autoAiReply") == null || Boolean.parseBoolean(String.valueOf(request.get("autoAiReply")));
        return Result.success(communicationService.sendPatientMessage(id, content, autoAiReply));
    }

    @PostMapping("/sessions/{id}/ai-reply")
    public Result<Map<String, Object>> triggerAiReply(@PathVariable Long id) {
        return Result.success(communicationService.triggerAiReply(id));
    }

    @PatchMapping("/sessions/{id}/ai-escalation")
    public Result<Void> setAiEscalation(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        boolean enabled = request.get("enabled") != null && Boolean.parseBoolean(String.valueOf(request.get("enabled")));
        communicationService.setAiEscalation(id, enabled);
        return Result.success(null);
    }

    @PostMapping("/case-summary/generate")
    public Result<Map<String, Object>> generateCaseSummary(@RequestBody Map<String, Object> request) {
        Long registerId = Long.valueOf(String.valueOf(request.get("registerId")));
        return Result.success(communicationService.generateCaseSummary(registerId));
    }

    @GetMapping("/case-summary/{registerId}")
    public Result<Map<String, Object>> getLatestCaseSummary(@PathVariable Long registerId) {
        return Result.success(communicationService.getLatestCaseSummary(registerId));
    }

    @GetMapping("/case-summary/{registerId}/shared")
    public Result<Map<String, Object>> getSharedCaseSummary(@PathVariable Long registerId) {
        return Result.success(communicationService.getSharedCaseSummary(registerId));
    }

    @PutMapping("/case-summary/{id}")
    public Result<Map<String, Object>> updateCaseSummary(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String doctorContent = request.get("doctorContent") != null ? String.valueOf(request.get("doctorContent")) : null;
        return Result.success(communicationService.updateCaseSummary(id, doctorContent));
    }

    @PostMapping("/case-summary/{id}/approve")
    public Result<Map<String, Object>> approveCaseSummary(
        @PathVariable Long id,
        @RequestBody Map<String, Object> request
    ) {
        String doctorContent = request.get("doctorContent") != null ? String.valueOf(request.get("doctorContent")) : null;
        Boolean shared = request.get("sharedToPatient") != null
            ? Boolean.valueOf(String.valueOf(request.get("sharedToPatient")))
            : null;
        return Result.success(communicationService.approveCaseSummary(id, doctorContent, shared));
    }

    @PostMapping("/case-summary/{id}/revoke")
    public Result<Map<String, Object>> revokeCaseSummary(@PathVariable Long id) {
        return Result.success(communicationService.revokeCaseSummary(id));
    }

    @GetMapping("/patient-brief/{registerId}")
    public Result<Map<String, Object>> getPatientBrief(@PathVariable Long registerId) {
        return Result.success(communicationService.getPatientBrief(registerId));
    }

    @GetMapping("/patient/session/{registerId}")
    public Result<Map<String, Object>> getPatientSession(@PathVariable Long registerId) {
        return Result.success(communicationService.getPatientSession(registerId));
    }

    @GetMapping("/suggestions/drugs")
    public Result<List<Map<String, Object>>> suggestDrugs(
        @RequestParam Long registerId,
        @RequestParam(required = false) String keyword
    ) {
        return Result.success(communicationService.suggestDrugs(registerId, keyword));
    }

    @GetMapping("/suggestions/diagnoses")
    public Result<List<Map<String, Object>>> suggestDiagnoses(@RequestParam Long registerId) {
        return Result.success(communicationService.suggestDiagnoses(registerId));
    }

    @GetMapping("/unread-summary")
    public Result<Map<String, Object>> unreadSummary(@RequestParam(required = false) Long departmentId) {
        return Result.success(communicationService.getDoctorUnreadSummary(departmentId));
    }

    @PostMapping("/sessions/{id}/mark-read")
    public Result<Map<String, Object>> markSessionRead(@PathVariable Long id) {
        return Result.success(communicationService.markDoctorSessionRead(id));
    }
}
