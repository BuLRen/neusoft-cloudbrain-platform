package com.xikang.medtech.service;

import com.xikang.medtech.ai.CaseSummaryContextBuilder;
import com.xikang.medtech.ai.CaseSummaryFallbackEngine;
import com.xikang.medtech.ai.CaseSummaryOutputMapper;
import com.xikang.medtech.ai.DifyWorkflowClient;
import com.xikang.medtech.ai.DifyWorkflowException;
import com.xikang.medtech.ai.DifyWorkflowRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.mapper.FollowUpCommunicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseSummaryWorkflowService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyWorkflowClient difyWorkflowClient;
    private final CaseSummaryContextBuilder contextBuilder;
    private final CaseSummaryOutputMapper outputMapper;
    private final CaseSummaryFallbackEngine fallbackEngine;
    private final FollowUpCommunicationMapper followUpCommunicationMapper;

    @Transactional
    public Map<String, Object> generateDraft(Long registerId, Long sessionId) {
        Map<String, Object> inputs = contextBuilder.build(registerId);
        Map<String, Object> generated = invokeWorkflow(registerId, inputs);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registerId", registerId);
        payload.put("sessionId", sessionId);
        payload.put("aiDraftContent", generated.get("caseSummary"));
        payload.put("aiMedicalAdvice", generated.get("medicalAdvice"));
        payload.put("aiRiskAlerts", toJsonArray(generated.get("riskAlerts")));
        payload.put("doctorContent", generated.get("caseSummary"));
        payload.put("status", "draft");
        payload.put("sharedToPatient", 0);
        payload.put("workflowRunId", generated.get("workflowRunId"));
        payload.put("modelId", generated.getOrDefault("modelId", "dify-workflow"));

        followUpCommunicationMapper.insertCaseSummary(payload);
        Map<String, Object> saved = followUpCommunicationMapper.selectCaseSummaryById(toLong(payload.get("id")));
        if (saved != null) {
            saved.put("followUpFocus", generated.get("followUpFocus"));
            saved.put("confidence", generated.get("confidence"));
            saved.put("source", generated.get("source"));
        }
        return saved;
    }

    private Map<String, Object> invokeWorkflow(Long registerId, Map<String, Object> inputs) {
        String user = "follow-up-summary-" + registerId;
        if (difyWorkflowClient.isFollowUpCaseSummaryEnabled()) {
            try {
                DifyWorkflowRunResult run = difyWorkflowClient.runFollowUpCaseSummaryBlocking(inputs, user, null);
                Map<String, Object> mapped = outputMapper.mapCaseSummaryOutputs(run.getOutputs());
                if (!String.valueOf(mapped.get("caseSummary")).isBlank()) {
                    mapped.put("workflowRunId", run.getWorkflowRunId());
                    mapped.put("modelId", "dify-workflow");
                    mapped.put("source", "workflow");
                    return mapped;
                }
            } catch (DifyWorkflowException ex) {
                log.warn("Case summary Dify failed registerId={}: {}", registerId, ex.getMessage());
            }
        }
        Map<String, Object> fallback = fallbackEngine.generate(inputs);
        fallback.put("workflowRunId", null);
        return fallback;
    }

    private String toJsonArray(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
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
