package com.xikang.medtech.service;

import com.xikang.medtech.ai.CaseSummaryContextBuilder;
import com.xikang.medtech.ai.CaseSummaryFallbackEngine;
import com.xikang.medtech.ai.CaseSummaryOutputMapper;
import com.xikang.medtech.ai.DifyWorkflowClient;
import com.xikang.medtech.ai.DifyWorkflowException;
import com.xikang.medtech.ai.DifyWorkflowRunResult;
import com.xikang.medtech.mapper.FollowUpCommunicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpMedicalAiService {

    private final DifyWorkflowClient difyWorkflowClient;
    private final CaseSummaryContextBuilder contextBuilder;
    private final CaseSummaryOutputMapper outputMapper;
    private final CaseSummaryFallbackEngine fallbackEngine;
    private final FollowUpCommunicationMapper followUpCommunicationMapper;

    public Map<String, Object> generateReply(Long registerId, String patientMessage, List<Map<String, Object>> recentMessages) {
        Map<String, Object> inputs = contextBuilder.buildMedicalChatInputs(registerId, patientMessage, recentMessages);
        String user = "follow-up-chat-" + registerId;

        if (difyWorkflowClient.isFollowUpMedicalChatEnabled()) {
            try {
                DifyWorkflowRunResult run = difyWorkflowClient.runFollowUpMedicalChatBlocking(inputs, user, null);
                Map<String, Object> mapped = outputMapper.mapMedicalChatOutputs(run.getOutputs());
                if (!String.valueOf(mapped.get("reply")).isBlank()) {
                    mapped.put("workflowRunId", run.getWorkflowRunId());
                    mapped.put("source", "workflow");
                    return mapped;
                }
            } catch (DifyWorkflowException ex) {
                log.warn("Medical chat Dify failed registerId={}: {}", registerId, ex.getMessage());
            }
        }

        Map<String, Object> fallback = fallbackEngine.generateMedicalReply(inputs);
        fallback.put("workflowRunId", null);
        return fallback;
    }

    public boolean shouldAutoReply(Map<String, Object> session) {
        if (session == null || session.isEmpty()) {
            return false;
        }
        Object enabled = session.get("aiEscalationEnabled");
        if (enabled instanceof Number number && number.intValue() == 0) {
            return false;
        }
        if (enabled instanceof Boolean bool && !bool) {
            return false;
        }
        return !isDoctorOnline(session);
    }

    public boolean isDoctorOnline(Map<String, Object> session) {
        Object activeAt = session.get("doctorLastActiveAt");
        if (activeAt == null) {
            return false;
        }
        try {
            java.time.LocalDateTime last = activeAt instanceof java.time.LocalDateTime ldt
                ? ldt
                : java.time.LocalDateTime.parse(String.valueOf(activeAt).replace(' ', 'T'));
            return last.isAfter(java.time.LocalDateTime.now().minusMinutes(5));
        } catch (Exception ex) {
            return false;
        }
    }
}
