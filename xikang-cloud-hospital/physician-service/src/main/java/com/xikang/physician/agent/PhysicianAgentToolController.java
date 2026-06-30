package com.xikang.physician.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dify Agent Custom API 网关（OpenAPI Schema 见 WF-AGENT 编排指南 6.2.2）。
 * 鉴权：{@code Authorization: Bearer INTERNAL_AI_TOKEN}
 */
@RestController
@RequestMapping("/api/physician/agent/tools")
@RequiredArgsConstructor
public class PhysicianAgentToolController {

    private final PhysicianAgentToolService agentToolService;

    @PostMapping("/run-preliminary-diagnosis")
    public ResponseEntity<Map<String, Object>> runPreliminaryDiagnosis(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runPreliminaryDiagnosis(body));
    }

    @PostMapping("/run-w1")
    public ResponseEntity<Map<String, Object>> runW1(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runW1(body));
    }

    @PostMapping("/run-w2")
    public ResponseEntity<Map<String, Object>> runW2(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runW2(body));
    }

    @PostMapping("/run-w3")
    public ResponseEntity<Map<String, Object>> runW3(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runW3(body));
    }

    @PostMapping("/run-w4")
    public ResponseEntity<Map<String, Object>> runW4(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runW4(body));
    }

    @PostMapping("/run-w5")
    public ResponseEntity<Map<String, Object>> runW5(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.runW5(body));
    }

    @PostMapping("/get-medical-record")
    public ResponseEntity<Map<String, Object>> getMedicalRecord(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.getMedicalRecord(body));
    }

    @PostMapping("/get-lab-results")
    public ResponseEntity<Map<String, Object>> getLabResults(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.getLabResults(body));
    }

    @PostMapping("/save-preliminary-diagnosis")
    public ResponseEntity<Map<String, Object>> savePreliminaryDiagnosis(@RequestBody Map<String, Object> body) {
        return respond(agentToolService.savePreliminaryDiagnosis(body));
    }

    private ResponseEntity<Map<String, Object>> respond(Map<String, Object> payload) {
        if (Boolean.TRUE.equals(payload.get("success"))) {
            return ResponseEntity.ok(payload);
        }
        int code = payload.get("code") instanceof Number number ? number.intValue() : 500;
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(payload);
    }
}
