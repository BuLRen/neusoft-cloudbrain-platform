package com.xikang.medtech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpCommunicationMapper;
import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpCommunicationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpCommunicationMapper communicationMapper;
    private final FollowUpDashboardMapper dashboardMapper;
    private final FollowUpOutcomeMapper outcomeMapper;
    private final HealthObservationService healthObservationService;
    private final CaseSummaryWorkflowService caseSummaryWorkflowService;
    private final FollowUpMedicalAiService medicalAiService;
    private final FollowUpClinicalSnapshotService clinicalSnapshotService;
    private final FollowUpHistoryService historyService;
    private final FollowUpEnrollmentBackfillService enrollmentBackfillService;

    public List<Map<String, Object>> listSessions(Long departmentIdOverride) {
        return communicationMapper.selectSessions(resolveDepartmentId(departmentIdOverride));
    }

    @Transactional
    public Map<String, Object> openSession(Long registerId, Long departmentIdOverride) {
        Long managingDepartmentId = dashboardMapper.selectManagingDepartmentId(registerId);
        if (managingDepartmentId == null) {
            throw new BusinessException("无法确定科室");
        }

        if (dashboardMapper.isEligiblePatient(registerId) && !dashboardMapper.isEnrolledInFollowUpPool(registerId)) {
            enrollmentBackfillService.processRegister(registerId);
            Long refreshed = dashboardMapper.selectManagingDepartmentId(registerId);
            if (refreshed != null) {
                managingDepartmentId = refreshed;
            }
        }

        Map<String, Object> existing = communicationMapper.selectSessionByRegisterId(registerId);
        if (existing != null && !existing.isEmpty()) {
            Long sessionDepartmentId = toLong(existing.get("departmentId"));
            if (sessionDepartmentId == null || !sessionDepartmentId.equals(managingDepartmentId)) {
                Long sessionId = toLong(existing.get("id"));
                communicationMapper.updateSessionDepartment(sessionId, managingDepartmentId);
                existing.put("departmentId", managingDepartmentId);
            }
            return existing;
        }

        Long departmentId = managingDepartmentId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("status", "active");
        payload.put("aiEscalationEnabled", 1);
        communicationMapper.insertSession(payload);

        Map<String, Object> session = communicationMapper.selectSessionById(toLong(payload.get("id")));
        Map<String, Object> notice = new HashMap<>();
        notice.put("sessionId", payload.get("id"));
        notice.put("senderType", "system");
        notice.put("messageType", "notice");
        notice.put("content", "随访沟通会话已建立，医生将在此与您跟进康复情况。");
        communicationMapper.insertMessage(notice);
        return session;
    }

    public Map<String, Object> getSession(Long sessionId) {
        Map<String, Object> session = communicationMapper.selectSessionById(sessionId);
        if (session == null || session.isEmpty()) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    public Map<String, Object> listMessages(Long sessionId, Integer limit, Integer offset) {
        int pageSize = limit != null && limit > 0 ? Math.min(limit, 500) : 100;
        int pageOffset = offset != null && offset >= 0 ? offset : 0;
        List<Map<String, Object>> items = communicationMapper.selectMessages(sessionId, pageSize, pageOffset);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", items);
        page.put("total", communicationMapper.countMessages(sessionId));
        return page;
    }

    @Transactional
    public Map<String, Object> sendDoctorMessage(Long sessionId, String content) {
        validateContent(content);
        Map<String, Object> session = getSession(sessionId);
        Map<String, Object> payload = messagePayload(sessionId, "doctor", "text", content.trim(), null);
        communicationMapper.insertMessage(payload);
        communicationMapper.updateSessionDoctorActive(sessionId);
        recordMessageHistory(session, payload);
        return new LinkedHashMap<>(payload);
    }

    @Transactional
    public Map<String, Object> sendDoctorCard(Long sessionId, String messageType, Map<String, Object> cardPayload) {
        if (!List.of("drug_card", "diagnosis_card", "registration_card").contains(messageType)) {
            throw new BusinessException("不支持的卡片类型");
        }
        if (cardPayload == null || cardPayload.isEmpty()) {
            throw new BusinessException("卡片内容不能为空");
        }
        Map<String, Object> session = getSession(sessionId);
        String title = buildCardTitle(messageType, cardPayload);
        String summary = buildCardSummary(messageType, cardPayload);
        Map<String, Object> payload = messagePayload(sessionId, "doctor", messageType, title, null);
        payload.put("cardPayloadJson", toJson(cardPayload));
        communicationMapper.insertMessage(payload);
        communicationMapper.updateSessionDoctorActive(sessionId);
        recordMessageHistory(session, payload, cardPayload);
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.put("cardPayload", cardPayload);
        return result;
    }

    public List<Map<String, Object>> suggestDrugs(Long registerId, String keyword) {
        return clinicalSnapshotService.suggestDrugs(registerId, keyword);
    }

    public List<Map<String, Object>> suggestDiagnoses(Long registerId) {
        return clinicalSnapshotService.suggestDiagnoses(registerId);
    }

    @Transactional
    public Map<String, Object> sendPatientMessage(Long sessionId, String content, boolean autoAiReply) {
        validateContent(content);
        getSession(sessionId);
        Map<String, Object> payload = messagePayload(sessionId, "patient", "text", content.trim(), null);
        communicationMapper.insertMessage(payload);

        if (autoAiReply) {
            Map<String, Object> session = communicationMapper.selectSessionById(sessionId);
            int aiEnabled = session.get("aiEscalationEnabled") instanceof Number n ? n.intValue() : 1;
            if (aiEnabled == 1) {
                return triggerAiReply(sessionId);
            }
        }
        return payload;
    }

    @Transactional
    public Map<String, Object> triggerAiReply(Long sessionId) {
        Map<String, Object> session = getSession(sessionId);
        Long registerId = toLong(session.get("registerId"));
        List<Map<String, Object>> recent = communicationMapper.selectMessages(sessionId, 10, Math.max(0, communicationMapper.countMessages(sessionId) - 10));

        String lastPatientMessage = recent.stream()
            .filter(m -> "patient".equals(String.valueOf(m.get("senderType"))))
            .reduce((first, second) -> second)
            .map(m -> String.valueOf(m.get("content")))
            .orElse("");

        Map<String, Object> ai = medicalAiService.generateReply(registerId, lastPatientMessage, recent);
        Map<String, Object> payload = messagePayload(
            sessionId, "ai", "text", String.valueOf(ai.get("reply")), ai.get("workflowRunId")
        );
        communicationMapper.insertMessage(payload);
        return payload;
    }

    @Transactional
    public void setAiEscalation(Long sessionId, boolean enabled) {
        getSession(sessionId);
        communicationMapper.updateAiEscalation(sessionId, enabled ? 1 : 0);
    }

    public Map<String, Object> generateCaseSummary(Long registerId) {
        Map<String, Object> session = communicationMapper.selectSessionByRegisterId(registerId);
        if (session == null || session.isEmpty()) {
            session = openSession(registerId, null);
        }
        return caseSummaryWorkflowService.generateDraft(registerId, toLong(session.get("id")));
    }

    public Map<String, Object> getLatestCaseSummary(Long registerId) {
        Map<String, Object> row = communicationMapper.selectLatestCaseSummary(registerId);
        return mapCaseSummary(row);
    }

    public Map<String, Object> getSharedCaseSummary(Long registerId) {
        Map<String, Object> row = communicationMapper.selectSharedCaseSummary(registerId);
        if (row == null || row.isEmpty()) {
            return Map.of("exists", false, "registerId", registerId);
        }
        return mapCaseSummary(row);
    }

    @Transactional
    public Map<String, Object> updateCaseSummary(Long summaryId, String doctorContent) {
        communicationMapper.updateCaseSummaryDoctorContent(summaryId, doctorContent);
        Map<String, Object> row = communicationMapper.selectCaseSummaryById(summaryId);
        return mapCaseSummary(row);
    }

    @Transactional
    public Map<String, Object> approveCaseSummary(Long summaryId, String doctorContent, Boolean sharedToPatient) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", summaryId);
        payload.put("doctorContent", doctorContent);
        payload.put("sharedToPatient", Boolean.TRUE.equals(sharedToPatient) ? 1 : 0);
        payload.put("approvedBy", MedtechAuthContext.employeeIdOrNull());
        communicationMapper.approveCaseSummary(payload);

        if (Boolean.TRUE.equals(sharedToPatient)) {
            Map<String, Object> summary = mapCaseSummary(communicationMapper.selectCaseSummaryById(summaryId));
            Long sessionId = toLong(summary.get("sessionId"));
            Map<String, Object> message = messagePayload(
                sessionId,
                "system",
                "case_summary",
                doctorContent != null ? doctorContent : String.valueOf(summary.get("content")),
                null
            );
            message.put("summaryId", summaryId);
            communicationMapper.insertMessage(message);
            recordMessageHistory(getSession(sessionId), message);
        }
        return mapCaseSummary(communicationMapper.selectCaseSummaryById(summaryId));
    }

    @Transactional
    public Map<String, Object> revokeCaseSummary(Long summaryId) {
        communicationMapper.revokeCaseSummary(summaryId);
        return mapCaseSummary(communicationMapper.selectCaseSummaryById(summaryId));
    }

    public Map<String, Object> getPatientBrief(Long registerId) {
        LocalDate today = LocalDate.now();
        Map<String, Object> brief = new LinkedHashMap<>(communicationMapper.selectPatientBriefProfile(registerId));
        brief.put("diseases", outcomeMapper.selectPatientDiseases(registerId));
        brief.put("recentMetrics", healthObservationService.getRecentMetrics(registerId, today.minusDays(14), today, 8));
        brief.put("observedToday", communicationMapper.selectTodayObservation(registerId, today) != null);
        brief.put("interviewScheduledToday", communicationMapper.selectTodayInterview(registerId, today) != null);
        brief.put("latestSummary", getLatestCaseSummary(registerId));
        return brief;
    }

    public Map<String, Object> getPatientSession(Long registerId) {
        Map<String, Object> session = communicationMapper.selectSessionByRegisterId(registerId);
        if (session == null || session.isEmpty()) {
            return openSession(registerId, null);
        }
        return session;
    }

    public Map<String, Object> getDoctorUnreadSummary(Long departmentIdOverride) {
        Long departmentId = resolveDepartmentId(departmentIdOverride);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUnread", communicationMapper.countDoctorUnreadTotal(departmentId));
        summary.put("byRegisterId", communicationMapper.selectDoctorUnreadByRegister(departmentId));
        return summary;
    }

    @Transactional
    public Map<String, Object> markDoctorSessionRead(Long sessionId) {
        getSession(sessionId);
        Long latestMessageId = communicationMapper.selectLatestMessageId(sessionId);
        if (latestMessageId != null) {
            communicationMapper.markDoctorRead(sessionId, latestMessageId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("marked", true);
        result.put("lastReadMessageId", latestMessageId);
        return result;
    }

    public Map<String, Object> getPatientUnreadSummary(Long registerId) {
        if (registerId == null) {
            throw new BusinessException("registerId 不能为空");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("registerId", registerId);
        summary.put("totalUnread", communicationMapper.countPatientUnread(registerId));
        return summary;
    }

    @Transactional
    public Map<String, Object> markPatientSessionRead(Long registerId) {
        Map<String, Object> session = communicationMapper.selectSessionByRegisterId(registerId);
        if (session == null || session.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("registerId", registerId);
            empty.put("marked", false);
            return empty;
        }
        Long sessionId = toLong(session.get("id"));
        Long latestMessageId = communicationMapper.selectLatestMessageId(sessionId);
        if (latestMessageId != null) {
            communicationMapper.markPatientRead(registerId, latestMessageId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("marked", true);
        result.put("lastReadMessageId", latestMessageId);
        return result;
    }

    private Map<String, Object> mapCaseSummary(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return Map.of("exists", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("registerId", row.get("registerId") != null ? row.get("registerId") : row.get("register_id"));
        result.put("sessionId", row.get("sessionId") != null ? row.get("sessionId") : row.get("session_id"));
        result.put("aiDraftContent", firstNonNull(row, "aiDraftContent", "ai_draft_content"));
        result.put("aiMedicalAdvice", firstNonNull(row, "aiMedicalAdvice", "ai_medical_advice"));
        result.put("aiRiskAlerts", firstNonNull(row, "aiRiskAlerts", "ai_risk_alerts"));
        result.put("doctorContent", firstNonNull(row, "doctorContent", "doctor_content"));
        result.put("status", row.get("status"));
        Object shared = row.get("sharedToPatient") != null ? row.get("sharedToPatient") : row.get("shared_to_patient");
        result.put("sharedToPatient", shared instanceof Number n ? n.intValue() == 1 : Boolean.TRUE.equals(shared));
        result.put("workflowRunId", firstNonNull(row, "workflowRunId", "workflow_run_id"));
        result.put("modelId", firstNonNull(row, "modelId", "model_id"));
        result.put("creationTime", firstNonNull(row, "creationTime", "creation_time"));
        result.put("content", result.get("doctorContent") != null ? result.get("doctorContent") : result.get("aiDraftContent"));
        result.put("exists", true);
        return result;
    }

    private Object firstNonNull(Map<String, Object> row, String camel, String snake) {
        if (row.get(camel) != null) {
            return row.get(camel);
        }
        return row.get(snake);
    }

    private Map<String, Object> messagePayload(
        Long sessionId,
        String senderType,
        String messageType,
        String content,
        Object workflowRunId
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("senderType", senderType);
        payload.put("messageType", messageType);
        payload.put("content", content);
        payload.put("workflowRunId", workflowRunId);
        return payload;
    }

    private void recordMessageHistory(Map<String, Object> session, Map<String, Object> message) {
        recordMessageHistory(session, message, null);
    }

    private void recordMessageHistory(
        Map<String, Object> session,
        Map<String, Object> message,
        Map<String, Object> cardPayload
    ) {
        Long registerId = toLong(session.get("registerId"));
        Long messageId = toLong(message.get("id"));
        if (registerId == null || messageId == null) {
            return;
        }
        String messageType = String.valueOf(message.get("messageType"));
        String content = message.get("content") != null ? String.valueOf(message.get("content")) : null;
        Map<String, Object> extra = new LinkedHashMap<>();
        if (cardPayload != null && !cardPayload.isEmpty()) {
            extra.putAll(cardPayload);
        } else if (message.get("cardPayloadJson") != null) {
            try {
                extra.putAll(MAPPER.readValue(String.valueOf(message.get("cardPayloadJson")), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
            }
        }
        historyService.recordCommunicationMessage(registerId, messageId, messageType, content, extra);
    }

    private String buildCardTitle(String messageType, Map<String, Object> cardPayload) {
        if ("registration_card".equals(messageType)) {
            Object dept = cardPayload.get("departmentName");
            return dept != null ? "复诊挂号：" + dept : "复诊挂号提醒";
        }
        if ("drug_card".equals(messageType)) {
            return "推荐药品：" + cardPayload.getOrDefault("drugName", "药品");
        }
        return "可能病况：" + cardPayload.getOrDefault("diseaseName", cardPayload.getOrDefault("diagnosisText", "病况"));
    }

    private String buildCardSummary(String messageType, Map<String, Object> cardPayload) {
        if ("registration_card".equals(messageType)) {
            Object reminder = cardPayload.get("reminderText");
            return reminder != null ? String.valueOf(reminder) : "建议您近期到院复诊，请点击下方按钮自行预约。";
        }
        if ("drug_card".equals(messageType)) {
            Object usage = cardPayload.get("drugUsage");
            return usage != null ? String.valueOf(usage) : "请遵医嘱用药";
        }
        Object treatment = cardPayload.get("treatmentDirection");
        return treatment != null ? String.valueOf(treatment) : "请结合临床进一步评估";
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
    }

    private Long resolveDepartmentId(Long override) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return override;
        }
        return MedtechAuthContext.departmentIdOrNull();
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
