package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.mapper.FollowUpCommunicationMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowUpCommunicationService {

    private static final int DEFAULT_MESSAGE_LIMIT = 200;

    private final FollowUpCommunicationMapper communicationMapper;
    private final FollowUpOutcomeMapper followUpOutcomeMapper;
    private final CaseSummaryWorkflowService caseSummaryWorkflowService;
    private final FollowUpMedicalAiService followUpMedicalAiService;

    public List<Map<String, Object>> listSessions(Long departmentIdOverride) {
        Long departmentId = resolveDepartmentId(departmentIdOverride);
        return communicationMapper.selectSessions(departmentId);
    }

    @Transactional
    public Map<String, Object> openSession(Long registerId) {
        Map<String, Object> existing = communicationMapper.selectSessionByRegisterId(registerId);
        if (existing != null && !existing.isEmpty()) {
            touchDoctorActive(toLong(existing.get("id")));
            return communicationMapper.selectSessionById(toLong(existing.get("id")));
        }

        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        if (profile == null || profile.isEmpty()) {
            throw new BusinessException("未找到该挂号患者");
        }

        Long departmentId = resolveDepartmentId(null);
        Map<String, Object> deptRow = communicationMapper.selectRegisterDepartmentId(registerId);
        if (departmentId == null && deptRow != null) {
            departmentId = toLong(deptRow.get("departmentId"));
        }
        if (departmentId == null) {
            throw new BusinessException("无法确定患者科室");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("registerId", registerId);
        payload.put("departmentId", departmentId);
        payload.put("status", "active");
        payload.put("aiEscalationEnabled", 1);
        payload.put("doctorLastActiveAt", LocalDateTime.now());
        communicationMapper.insertSession(payload);

        Long sessionId = toLong(payload.get("id"));
        insertSystemMessage(sessionId, "随访沟通会话已建立，医生将在此与您跟进康复情况。");
        return communicationMapper.selectSessionById(sessionId);
    }

    public Map<String, Object> getSession(Long sessionId) {
        Map<String, Object> session = requireSession(sessionId);
        assertDepartmentAccess(session);
        return session;
    }

    public Map<String, Object> listMessages(Long sessionId, Integer limit, Integer offset) {
        requireAccessibleSession(sessionId);
        int pageSize = limit == null || limit <= 0 ? DEFAULT_MESSAGE_LIMIT : Math.min(limit, 500);
        int pageOffset = offset == null || offset < 0 ? 0 : offset;
        List<Map<String, Object>> items = communicationMapper.selectMessages(sessionId, pageSize, pageOffset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", communicationMapper.countMessages(sessionId));
        return result;
    }

    @Transactional
    public Map<String, Object> sendDoctorMessage(Long sessionId, String content) {
        Map<String, Object> session = requireAccessibleSession(sessionId);
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        touchDoctorActive(sessionId);
        return insertMessage(sessionId, "doctor", "text", content.trim(), null, null);
    }

    @Transactional
    public Map<String, Object> sendPatientMessage(Long sessionId, String content, boolean autoAiReply) {
        Map<String, Object> session = requireSession(sessionId);
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        Map<String, Object> patientMsg = insertMessage(sessionId, "patient", "text", content.trim(), null, null);

        if (autoAiReply && followUpMedicalAiService.shouldAutoReply(session)) {
            Long registerId = toLong(session.get("registerId"));
            List<Map<String, Object>> recent = communicationMapper.selectMessages(sessionId, 20, 0);
            Map<String, Object> aiResult = followUpMedicalAiService.generateReply(registerId, content, recent);
            String reply = String.valueOf(aiResult.getOrDefault("reply", ""));
            if (!reply.isBlank()) {
                insertMessage(sessionId, "ai", "text", reply, null, textOrNull(aiResult.get("workflowRunId")));
            }
        }
        return patientMsg;
    }

    @Transactional
    public Map<String, Object> triggerAiReply(Long sessionId) {
        Map<String, Object> session = requireAccessibleSession(sessionId);
        Long registerId = toLong(session.get("registerId"));
        List<Map<String, Object>> recent = communicationMapper.selectMessages(sessionId, 20, 0);
        String lastPatientMessage = recent.stream()
            .filter(m -> "patient".equals(String.valueOf(m.get("senderType"))))
            .reduce((first, second) -> second)
            .map(m -> String.valueOf(m.get("content")))
            .orElse("");
        if (lastPatientMessage.isBlank()) {
            throw new BusinessException("暂无患者消息可供 AI 回复");
        }
        Map<String, Object> aiResult = followUpMedicalAiService.generateReply(registerId, lastPatientMessage, recent);
        String reply = String.valueOf(aiResult.getOrDefault("reply", ""));
        if (reply.isBlank()) {
            throw new BusinessException("AI 未生成有效回复");
        }
        return insertMessage(sessionId, "ai", "text", reply, null, textOrNull(aiResult.get("workflowRunId")));
    }

    @Transactional
    public Map<String, Object> generateCaseSummary(Long registerId) {
        Map<String, Object> session = communicationMapper.selectSessionByRegisterId(registerId);
        if (session == null || session.isEmpty()) {
            session = openSession(registerId);
        } else {
            assertDepartmentAccess(session);
            touchDoctorActive(toLong(session.get("id")));
        }
        return caseSummaryWorkflowService.generateDraft(registerId, toLong(session.get("id")));
    }

    public Map<String, Object> getLatestCaseSummary(Long registerId) {
        Map<String, Object> summary = communicationMapper.selectLatestCaseSummary(registerId);
        if (summary == null || summary.isEmpty()) {
            return Map.of("registerId", registerId, "exists", false);
        }
        return enrichSummary(summary);
    }

    public Map<String, Object> getSharedCaseSummaryForPatient(Long registerId) {
        Map<String, Object> summary = communicationMapper.selectLatestCaseSummary(registerId);
        if (summary == null || summary.isEmpty()) {
            return Map.of("registerId", registerId, "exists", false);
        }
        if (!isShared(summary)) {
            return Map.of("registerId", registerId, "exists", false);
        }
        Map<String, Object> result = enrichSummary(summary);
        result.put("content", summary.get("doctorContent"));
        return result;
    }

    @Transactional
    public Map<String, Object> updateCaseSummary(Long summaryId, String doctorContent) {
        Map<String, Object> summary = requireSummary(summaryId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", summaryId);
        payload.put("doctorContent", doctorContent);
        communicationMapper.updateCaseSummaryDraft(payload);
        return enrichSummary(communicationMapper.selectCaseSummaryById(summaryId));
    }

    @Transactional
    public Map<String, Object> approveCaseSummary(Long summaryId, String doctorContent, boolean sharedToPatient) {
        Map<String, Object> summary = requireSummary(summaryId);
        Long sessionId = toLong(summary.get("sessionId"));
        requireAccessibleSession(sessionId);
        touchDoctorActive(sessionId);

        String finalContent = doctorContent != null && !doctorContent.isBlank()
            ? doctorContent.trim()
            : textOrEmpty(summary.get("doctorContent"), textOrEmpty(summary.get("aiDraftContent"), ""));

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", summaryId);
        payload.put("doctorContent", finalContent);
        payload.put("status", sharedToPatient ? "shared" : "approved");
        payload.put("sharedToPatient", sharedToPatient ? 1 : 0);
        payload.put("approvedBy", MedtechAuthContext.employeeIdOrNull());
        payload.put("approvedAt", LocalDateTime.now());
        communicationMapper.updateCaseSummaryApproved(payload);

        if (sharedToPatient) {
            insertMessage(sessionId, "system", "case_summary", finalContent, summaryId, null);
        }
        return enrichSummary(communicationMapper.selectCaseSummaryById(summaryId));
    }

    @Transactional
    public Map<String, Object> revokeCaseSummary(Long summaryId) {
        Map<String, Object> summary = requireSummary(summaryId);
        requireAccessibleSession(toLong(summary.get("sessionId")));
        communicationMapper.revokeCaseSummary(summaryId);
        return enrichSummary(communicationMapper.selectCaseSummaryById(summaryId));
    }

    public Map<String, Object> getPatientBrief(Long registerId) {
        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        if (profile == null || profile.isEmpty()) {
            throw new BusinessException("未找到该患者");
        }
        Map<String, Object> detail = followUpOutcomeMapper.selectPatientDetail(registerId);
        List<Map<String, Object>> diseases = followUpOutcomeMapper.selectPatientDiseases(registerId);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> metrics = communicationMapper.selectRecentMetrics(registerId, today.minusDays(30), today);

        Map<String, Object> brief = new LinkedHashMap<>(profile);
        if (detail != null) {
            brief.put("diagnosis", detail.get("diagnosis"));
            brief.put("chiefComplaint", detail.get("chiefComplaint"));
            brief.put("allergy", detail.get("allergy"));
        }
        brief.put("diseases", diseases);
        brief.put("recentMetrics", metrics.size() > 12 ? metrics.subList(0, 12) : metrics);
        brief.put("observedToday", communicationMapper.selectTodayObservation(registerId, today) != null);
        brief.put("interviewScheduledToday", communicationMapper.selectTodayInterview(registerId, today) != null);
        brief.put("latestSummary", getLatestCaseSummary(registerId));
        return brief;
    }

    @Transactional
    public void setAiEscalation(Long sessionId, boolean enabled) {
        requireAccessibleSession(sessionId);
        communicationMapper.updateSessionAiEscalation(sessionId, enabled ? 1 : 0);
    }

    public Map<String, Object> getOrCreatePatientSession(Long registerId) {
        Map<String, Object> session = communicationMapper.selectSessionByRegisterId(registerId);
        if (session == null || session.isEmpty()) {
            return openSession(registerId);
        }
        return session;
    }

    private Map<String, Object> insertMessage(
        Long sessionId,
        String senderType,
        String messageType,
        String content,
        Long summaryId,
        String workflowRunId
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("senderType", senderType);
        payload.put("messageType", messageType);
        payload.put("content", content);
        payload.put("summaryId", summaryId);
        payload.put("workflowRunId", workflowRunId);
        communicationMapper.insertMessage(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", payload.get("id"));
        result.put("sessionId", sessionId);
        result.put("senderType", senderType);
        result.put("messageType", messageType);
        result.put("content", content);
        result.put("summaryId", summaryId);
        result.put("workflowRunId", workflowRunId);
        result.put("creationTime", LocalDateTime.now());
        return result;
    }

    private void insertSystemMessage(Long sessionId, String content) {
        insertMessage(sessionId, "system", "notice", content, null, null);
    }

    private void touchDoctorActive(Long sessionId) {
        communicationMapper.updateSessionDoctorActive(sessionId, LocalDateTime.now());
    }

    private Map<String, Object> requireSession(Long sessionId) {
        Map<String, Object> session = communicationMapper.selectSessionById(sessionId);
        if (session == null || session.isEmpty()) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    private Map<String, Object> requireAccessibleSession(Long sessionId) {
        Map<String, Object> session = requireSession(sessionId);
        assertDepartmentAccess(session);
        touchDoctorActive(sessionId);
        return session;
    }

    private Map<String, Object> requireSummary(Long summaryId) {
        Map<String, Object> summary = communicationMapper.selectCaseSummaryById(summaryId);
        if (summary == null || summary.isEmpty()) {
            throw new BusinessException("病例总结不存在");
        }
        return summary;
    }

    private void assertDepartmentAccess(Map<String, Object> session) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return;
        }
        Long dept = MedtechAuthContext.departmentIdOrNull();
        Long sessionDept = toLong(session.get("departmentId"));
        if (dept == null) {
            throw new BusinessException(403, "当前账号未绑定科室");
        }
        if (sessionDept != null && !dept.equals(sessionDept)) {
            throw new BusinessException(403, "无权访问该科室会话");
        }
    }

    private Long resolveDepartmentId(Long override) {
        if (MedtechAuthContext.isAdminAllAccess()) {
            return override;
        }
        Long departmentId = MedtechAuthContext.departmentIdOrNull();
        if (departmentId == null) {
            throw new BusinessException(403, "当前账号未绑定科室");
        }
        return departmentId;
    }

    private Map<String, Object> enrichSummary(Map<String, Object> summary) {
        Map<String, Object> row = new LinkedHashMap<>(summary);
        row.put("exists", true);
        row.put("sharedToPatient", isShared(summary));
        return row;
    }

    private boolean isShared(Map<String, Object> summary) {
        Object shared = summary.get("sharedToPatient");
        if (shared instanceof Number number) {
            return number.intValue() != 0;
        }
        return "shared".equals(String.valueOf(summary.get("status")));
    }

    private String textOrEmpty(Object value, String defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    private String textOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
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
