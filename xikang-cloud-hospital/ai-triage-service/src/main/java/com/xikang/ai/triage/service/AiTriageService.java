package com.xikang.ai.triage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Triage Service
 * TODO: 业务实现由 AI 模块开发人员完成
 */
@Slf4j
@Service
public class AiTriageService {

    /**
     * Get triage recommendation based on symptoms
     */
    public Map<String, Object> getTriageRecommendation(Map<String, Object> symptoms) {
        log.info("Getting triage recommendation for symptoms: {}", symptoms);
        Map<String, Object> recommendation = new HashMap<>();
        recommendation.put("department", "Internal Medicine");
        recommendation.put("urgency", "normal");
        recommendation.put("waitingTime", "30 minutes");
        return recommendation;
    }

    /**
     * Chat with AI triage assistant
     */
    public Map<String, Object> chat(Map<String, Object> chatRequest) {
        log.info("Chat request: {}", chatRequest);
        String message = (String) chatRequest.get("message");

        Map<String, Object> response = new HashMap<>();
        response.put("reply", "Thank you for your message. Please describe your symptoms in detail.");
        response.put("sessionId", chatRequest.get("sessionId"));
        return response;
    }
}
