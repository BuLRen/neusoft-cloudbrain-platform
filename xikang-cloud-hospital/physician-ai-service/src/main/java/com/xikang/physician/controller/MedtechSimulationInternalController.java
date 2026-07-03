package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.simulation.SimulationConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dify 模拟检查/检验工作流 HTTP 节点回调（Bearer {@code INTERNAL_AI_TOKEN}）。
 * 路径保持 {@code /api/medtech/internal/**}，便于 Dify 仅切换 host/port 至 physician-ai-service。
 */
@Slf4j
@RestController
@RequestMapping("/api/medtech/internal")
public class MedtechSimulationInternalController {

    private final SimulationConfigService simulationConfigService;

    public MedtechSimulationInternalController(SimulationConfigService simulationConfigService) {
        this.simulationConfigService = simulationConfigService;
    }

    @GetMapping("/simulation-config")
    public ResponseEntity<Result<Map<String, Object>>> getSimulationConfig(
        @RequestParam(required = false) String techCode,
        @RequestParam(required = false) String checkName,
        @RequestParam(required = false) String mode
    ) {
        log.info(
            "Dify simulation-config request | techCode={} checkName={} mode={}",
            techCode,
            checkName,
            mode
        );
        return simulationConfigService.resolveForWorkflow(techCode, checkName)
            .map(data -> {
                log.info("Dify simulation-config matched | configKey={}", data.get("configKey"));
                return ResponseEntity.ok(Result.success(data));
            })
            .orElseGet(() -> {
                log.warn("Dify simulation-config not found | techCode={} checkName={}", techCode, checkName);
                return ResponseEntity.status(404).body(Result.error(404, "未找到模拟配置"));
            });
    }
}
