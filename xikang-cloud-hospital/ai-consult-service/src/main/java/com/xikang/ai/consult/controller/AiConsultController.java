package com.xikang.ai.consult.controller;

import com.xikang.ai.consult.service.AiConsultService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Consult Controller
 */
@RestController
@RequestMapping("/api/ai/consult")
@RequiredArgsConstructor
public class AiConsultController {

    private final AiConsultService aiConsultService;

    /**
     * Start consultation session
     */
    @PostMapping("/session")
    public Result<Map<String, Object>> startSession(@RequestBody Map<String, Object> sessionRequest) {
        Map<String, Object> session = aiConsultService.startSession(sessionRequest);
        return Result.success(session);
    }

    /**
     * Send message in consultation
     */
    @PostMapping("/message")
    public Result<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> messageRequest) {
        Map<String, Object> response = aiConsultService.sendMessage(messageRequest);
        return Result.success(response);
    }

    /**
     * End consultation session
     */
    @PostMapping("/session/{sessionId}/end")
    public Result<Void> endSession(@PathVariable String sessionId) {
        aiConsultService.endSession(sessionId);
        return Result.success();
    }
}
