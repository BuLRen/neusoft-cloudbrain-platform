package com.xikang.ai.gateway.controller;

import com.xikang.ai.gateway.service.AiGatewayService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Gateway Controller
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiGatewayService aiGatewayService;

    /**
     * Route AI request to appropriate service
     */
    @PostMapping("/route")
    public Result<Map<String, Object>> routeRequest(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = aiGatewayService.routeRequest(request);
        return Result.success(response);
    }

    /**
     * Get AI service status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getServiceStatus() {
        return Result.success(aiGatewayService.getServiceStatus());
    }
}
