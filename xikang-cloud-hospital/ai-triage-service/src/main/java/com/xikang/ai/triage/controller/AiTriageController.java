package com.xikang.ai.triage.controller;

import com.xikang.ai.triage.service.AiTriageService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Triage Controller
 */
@RestController
@RequestMapping("/api/ai/triage")
@RequiredArgsConstructor
public class AiTriageController {

    private final AiTriageService aiTriageService;

    /**
     * Get triage recommendation
     */
    @PostMapping("/recommend")
    public Result<Map<String, Object>> getTriageRecommendation(@RequestBody Map<String, Object> symptoms) {
        Map<String, Object> recommendation = aiTriageService.getTriageRecommendation(symptoms);
        return Result.success(recommendation);
    }

    /**
     * Chat with AI triage assistant
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> chatRequest) {
        Map<String, Object> response = aiTriageService.chat(chatRequest);
        return Result.success(response);
    }
}
