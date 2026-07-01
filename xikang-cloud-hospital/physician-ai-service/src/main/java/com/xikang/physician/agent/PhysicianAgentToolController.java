package com.xikang.physician.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dify Agent Custom API 网关（OpenAPI Schema 见 WF-AGENT 编排指南）。
 * 鉴权：{@code Authorization: Bearer INTERNAL_AI_TOKEN}
 */
@RestController
@RequestMapping("/api/physician/agent/tools")
@RequiredArgsConstructor
public class PhysicianAgentToolController {

    private final PhysicianAgentToolService agentToolService;

    // Workflow
    @PostMapping("/run-preliminary-diagnosis")
    public ResponseEntity<Map<String, Object>> runPreliminaryDiagnosis(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runPreliminaryDiagnosis(merge(query, body)));
    }

    @PostMapping("/run-w1")
    public ResponseEntity<Map<String, Object>> runW1(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runW1(merge(query, body)));
    }

    @PostMapping("/run-w2")
    public ResponseEntity<Map<String, Object>> runW2(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runW2(merge(query, body)));
    }

    @PostMapping("/run-w3")
    public ResponseEntity<Map<String, Object>> runW3(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runW3(merge(query, body)));
    }

    @PostMapping("/run-w4")
    public ResponseEntity<Map<String, Object>> runW4(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runW4(merge(query, body)));
    }

    @PostMapping("/run-w5")
    public ResponseEntity<Map<String, Object>> runW5(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.runW5(merge(query, body)));
    }

    // Read
    @PostMapping("/get-medical-record")
    public ResponseEntity<Map<String, Object>> getMedicalRecord(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getMedicalRecord(merge(query, body)));
    }

    @PostMapping("/get-lab-results")
    public ResponseEntity<Map<String, Object>> getLabResults(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getLabResults(merge(query, body)));
    }

    @PostMapping("/get-patient")
    public ResponseEntity<Map<String, Object>> getPatient(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getPatient(merge(query, body)));
    }

    @PostMapping("/get-medical-technologies")
    public ResponseEntity<Map<String, Object>> getMedicalTechnologies(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getMedicalTechnologies(merge(query, body)));
    }

    @PostMapping("/get-diseases")
    public ResponseEntity<Map<String, Object>> getDiseases(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getDiseases(merge(query, body)));
    }

    @PostMapping("/get-drugs")
    public ResponseEntity<Map<String, Object>> getDrugs(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getDrugs(merge(query, body)));
    }

    @PostMapping("/get-prescriptions")
    public ResponseEntity<Map<String, Object>> getPrescriptions(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getPrescriptions(merge(query, body)));
    }

    @PostMapping("/get-visit-timeline")
    public ResponseEntity<Map<String, Object>> getVisitTimeline(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getVisitTimeline(merge(query, body)));
    }

    @PostMapping("/get-exam-suggestions")
    public ResponseEntity<Map<String, Object>> getExamSuggestions(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getExamSuggestions(merge(query, body)));
    }

    @PostMapping("/get-diagnosis-suggestions")
    public ResponseEntity<Map<String, Object>> getDiagnosisSuggestions(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.getDiagnosisSuggestions(merge(query, body)));
    }

    // Draft
    @PostMapping("/draft-medical-record")
    public ResponseEntity<Map<String, Object>> draftMedicalRecord(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.draftMedicalRecord(merge(query, body)));
    }

    @PostMapping("/draft-order-basket")
    public ResponseEntity<Map<String, Object>> draftOrderBasket(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.draftOrderBasket(merge(query, body)));
    }

    @PostMapping("/draft-diagnosis")
    public ResponseEntity<Map<String, Object>> draftDiagnosis(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.draftDiagnosis(merge(query, body)));
    }

    @PostMapping("/draft-prescription")
    public ResponseEntity<Map<String, Object>> draftPrescription(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.draftPrescription(merge(query, body)));
    }

    @PostMapping("/draft-preliminary-diagnosis")
    public ResponseEntity<Map<String, Object>> draftPreliminaryDiagnosis(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.draftPreliminaryDiagnosis(merge(query, body)));
    }

    // Commit (require confirmation_token)
    @PostMapping("/save-preliminary-diagnosis")
    public ResponseEntity<Map<String, Object>> savePreliminaryDiagnosis(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.savePreliminaryDiagnosis(merge(query, body)));
    }

    @PostMapping("/commit-medical-record")
    public ResponseEntity<Map<String, Object>> commitMedicalRecord(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitMedicalRecord(merge(query, body)));
    }

    @PostMapping("/commit-check-requests")
    public ResponseEntity<Map<String, Object>> commitCheckRequests(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitCheckRequests(merge(query, body)));
    }

    @PostMapping("/commit-inspection-requests")
    public ResponseEntity<Map<String, Object>> commitInspectionRequests(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitInspectionRequests(merge(query, body)));
    }

    @PostMapping("/commit-disposal-requests")
    public ResponseEntity<Map<String, Object>> commitDisposalRequests(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitDisposalRequests(merge(query, body)));
    }

    @PostMapping("/commit-diagnosis")
    public ResponseEntity<Map<String, Object>> commitDiagnosis(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitDiagnosis(merge(query, body)));
    }

    @PostMapping("/commit-prescription")
    public ResponseEntity<Map<String, Object>> commitPrescription(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitPrescription(merge(query, body)));
    }

    @PostMapping("/commit-archive-visit")
    public ResponseEntity<Map<String, Object>> commitArchiveVisit(
        @RequestParam Map<String, String> query,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return respond(agentToolService.commitArchiveVisit(merge(query, body)));
    }

    private Map<String, Object> merge(Map<String, String> query, Map<String, Object> body) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    merged.put(key, value);
                }
            });
        }
        if (body != null) {
            merged.putAll(body);
        }
        return merged;
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
