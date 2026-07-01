package com.xikang.physician.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.physician.agent.entity.AgentToolAuditLog;
import com.xikang.physician.agent.mapper.AgentToolAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.xikang.physician.context.PhysicianAuthContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolAuditService {

    private final AgentToolAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public void logSuccess(
        String toolName,
        AgentToolExecutionContext.RiskLevel riskLevel,
        Long registerId,
        Map<String, Object> requestBody,
        Map<String, Object> responseData,
        String beforeSnapshot,
        String afterSnapshot,
        String confirmSource,
        String confirmationToken
    ) {
        insert(toolName, riskLevel, registerId, null, requestBody, responseData, beforeSnapshot, afterSnapshot,
            confirmSource, confirmationToken, true, null);
    }

    /** Copilot UI 确认提交审计（显式传入 sessionId，不依赖 Agent 工具 ThreadLocal） */
    public void logCopilotConfirmSuccess(
        String actionType,
        Long registerId,
        Long sessionId,
        Map<String, Object> requestBody,
        Map<String, Object> responseData,
        String beforeSnapshot,
        String afterSnapshot,
        String confirmationToken
    ) {
        insert(
            "copilot_confirm_" + actionType,
            AgentToolExecutionContext.RiskLevel.COMMIT,
            registerId,
            sessionId,
            requestBody,
            responseData,
            beforeSnapshot,
            afterSnapshot,
            "copilot-ui",
            confirmationToken,
            true,
            null
        );
    }

    public List<Map<String, Object>> listCopilotConfirmCompletions(Long registerId, Long sessionId) {
        if (registerId == null || sessionId == null) {
            return List.of();
        }
        List<AgentToolAuditLog> rows = auditLogMapper.selectCopilotConfirmCompletions(registerId, sessionId);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (AgentToolAuditLog row : rows) {
            String toolName = row.getToolName() == null ? "" : row.getToolName();
            String actionType = toolName.startsWith("copilot_confirm_")
                ? toolName.substring("copilot_confirm_".length())
                : toolName;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("actionType", actionType);
            item.put("completedAt", row.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    public void logFailure(
        String toolName,
        AgentToolExecutionContext.RiskLevel riskLevel,
        Long registerId,
        Map<String, Object> requestBody,
        String errorMessage
    ) {
        insert(toolName, riskLevel, registerId, null, requestBody, null, null, null,
            null, null, false, errorMessage);
    }

    private void insert(
        String toolName,
        AgentToolExecutionContext.RiskLevel riskLevel,
        Long registerId,
        Long sessionId,
        Map<String, Object> requestBody,
        Map<String, Object> responseData,
        String beforeSnapshot,
        String afterSnapshot,
        String confirmSource,
        String confirmationToken,
        boolean success,
        String errorMessage
    ) {
        try {
            AgentToolAuditLog row = new AgentToolAuditLog();
            row.setRegisterId(registerId);
            Long doctorId = AgentToolExecutionContext.getDoctorId();
            if (doctorId == null) {
                doctorId = PhysicianAuthContext.confirmationActorIdOrNull();
            }
            row.setDoctorId(doctorId);
            Long resolvedSessionId = sessionId != null ? sessionId : AgentToolExecutionContext.getSessionId();
            row.setSessionId(resolvedSessionId);
            row.setRequestId(AgentToolExecutionContext.getRequestId());
            row.setToolName(toolName);
            row.setRiskLevel(riskLevel.name().toLowerCase());
            row.setRequestPayload(toJson(requestBody));
            row.setResponsePayload(responseData == null ? null : toJson(responseData));
            row.setBeforeSnapshot(beforeSnapshot);
            row.setAfterSnapshot(afterSnapshot);
            row.setConfirmSource(confirmSource);
            row.setConfirmationToken(confirmationToken);
            row.setSuccess(success);
            row.setErrorMessage(errorMessage);
            row.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("Failed to write agent tool audit log tool={} registerId={}", toolName, registerId, ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
