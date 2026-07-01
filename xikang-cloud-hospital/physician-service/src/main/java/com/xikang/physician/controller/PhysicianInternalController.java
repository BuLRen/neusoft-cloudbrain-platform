package com.xikang.physician.controller;

import com.xikang.common.result.Result;
import com.xikang.physician.mapper.PhysicianMapper;
import com.xikang.physician.service.ClinicalRecordService;
import com.xikang.physician.service.PhysicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal API for physician-ai-service Feign calls (not exposed via gateway).
 */
@RestController
@RequestMapping("/api/physician/internal")
@RequiredArgsConstructor
public class PhysicianInternalController {

    private final PhysicianService physicianService;
    private final ClinicalRecordService clinicalRecordService;
    private final PhysicianMapper physicianMapper;

    @GetMapping("/medical-record/{registerId}")
    public Result<Map<String, Object>> getMedicalRecord(@PathVariable Long registerId) {
        return Result.success(physicianService.getMedicalRecord(registerId));
    }

    @GetMapping("/patient/{registerId}")
    public Result<Map<String, Object>> getPatient(@PathVariable Long registerId) {
        return Result.success(physicianService.getPatient(registerId));
    }

    @GetMapping("/check-results/{registerId}")
    public Result<List<Map<String, Object>>> getCheckResults(@PathVariable Long registerId) {
        return Result.success(physicianService.getCheckResults(registerId));
    }

    @GetMapping("/inspection-results/{registerId}")
    public Result<List<Map<String, Object>>> getInspectionResults(@PathVariable Long registerId) {
        return Result.success(physicianService.getInspectionResults(registerId));
    }

    @GetMapping("/drugs")
    public Result<List<Map<String, Object>>> getDrugs(@RequestParam(required = false) String keyword) {
        return Result.success(physicianService.getDrugs(keyword));
    }

    @GetMapping("/medical-technologies")
    public Result<List<Map<String, Object>>> getMedicalTechnologies(
        @RequestParam(required = false) String techType,
        @RequestParam(required = false) String keyword
    ) {
        return Result.success(physicianService.getMedicalTechnologies(techType, keyword));
    }

    @GetMapping("/diseases")
    public Result<List<Map<String, Object>>> getDiseases(@RequestParam(required = false) String keyword) {
        return Result.success(physicianService.getDiseases(keyword));
    }

    @GetMapping("/drugs/page")
    public Result<Map<String, Object>> getDrugsPage(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize
    ) {
        return Result.success(physicianService.getDrugsPage(keyword, page, pageSize));
    }

    @GetMapping("/drugs/{id}")
    public Result<Map<String, Object>> getDrug(@PathVariable Long id) {
        return Result.success(physicianService.getDrug(id));
    }

    @GetMapping("/prescriptions/{registerId}")
    public Result<List<Map<String, Object>>> getPrescriptions(@PathVariable Long registerId) {
        return Result.success(physicianService.getPrescriptionList(registerId));
    }

    @GetMapping("/register/{registerId}")
    public Result<Map<String, Object>> getRegister(@PathVariable Long registerId) {
        return Result.success(physicianMapper.selectRegisterById(registerId));
    }

    @GetMapping("/register/{registerId}/employee-id")
    public Result<Long> getRegisterEmployeeId(@PathVariable Long registerId) {
        return Result.success(physicianMapper.selectRegisterEmployeeId(registerId));
    }

    @GetMapping("/latest-ai-consultation/{registerId}")
    public Result<Map<String, Object>> getLatestAiConsultation(@PathVariable Long registerId) {
        return Result.success(physicianMapper.selectLatestAiConsultation(registerId));
    }

    @GetMapping("/available-examinations")
    public Result<List<Map<String, Object>>> getAvailableExaminations() {
        return Result.success(physicianMapper.selectAvailableExaminations());
    }

    @GetMapping("/open-requests-for-simulation/{registerId}")
    public Result<List<Map<String, Object>>> getOpenRequestsForSimulation(@PathVariable Long registerId) {
        return Result.success(physicianMapper.selectOpenRequestsForSimulation(registerId));
    }

    @GetMapping("/diseases-by-medical-record/{medicalRecordId}")
    public Result<List<Map<String, Object>>> getDiseasesByMedicalRecordId(@PathVariable Long medicalRecordId) {
        return Result.success(physicianMapper.selectDiseasesByMedicalRecordId(medicalRecordId));
    }

    @GetMapping("/visit-timeline/{registerId}")
    public Result<Map<String, Object>> getVisitTimeline(@PathVariable Long registerId) {
        return Result.success(clinicalRecordService.getVisitTimeline(registerId));
    }

    @GetMapping("/visit-notebook/{registerId}")
    public Result<Map<String, Object>> getVisitNotebook(@PathVariable Long registerId) {
        return Result.success(clinicalRecordService.getVisitNotebook(registerId));
    }

    @PostMapping("/medical-record")
    public Result<Map<String, Object>> createMedicalRecord(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createMedicalRecord(request));
    }

    @PutMapping("/medical-record/{id}")
    public Result<Void> updateMedicalRecord(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        physicianService.updateMedicalRecord(id, request);
        return Result.success();
    }

    @PostMapping("/preliminary-diagnosis")
    public Result<Void> savePreliminaryDiagnosis(@RequestBody Map<String, Object> request) {
        physicianService.savePreliminaryDiagnosis(request);
        return Result.success();
    }

    @PostMapping("/check-requests")
    public Result<Map<String, Object>> createCheckRequest(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createCheckRequest(request));
    }

    @PostMapping("/inspection-requests")
    public Result<Map<String, Object>> createInspectionRequest(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createInspectionRequest(request));
    }

    @PostMapping("/disposal-requests")
    public Result<Map<String, Object>> createDisposalRequest(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createDisposalRequest(request));
    }

    @PostMapping("/diagnosis")
    public Result<Map<String, Object>> submitDiagnosis(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createDiagnosis(request));
    }

    @PostMapping("/prescription")
    public Result<Map<String, Object>> createPrescription(@RequestBody Map<String, Object> request) {
        return Result.success(physicianService.createPrescription(request));
    }

    @PostMapping("/archive-visit/{registerId}")
    public Result<Map<String, Object>> archiveVisit(@PathVariable Long registerId) {
        return Result.success(clinicalRecordService.archiveVisit(registerId));
    }

    @PutMapping("/visit-state")
    public Result<Void> updateVisitState(@RequestBody Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Integer visitState = request.get("visitState") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(request.get("visitState")));
        physicianMapper.updateVisitState(registerId, visitState);
        return Result.success();
    }

    @PostMapping("/medical-record-upsert")
    public Result<Map<String, Object>> upsertMedicalRecord(@RequestBody Map<String, Object> record) {
        Long registerId = toLong(record.get("registerId"));
        Map<String, Object> existing = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (existing == null) {
            physicianMapper.insertMedicalRecord(record);
            Map<String, Object> result = new HashMap<>();
            result.put("id", record.get("id"));
            result.put("registerId", registerId);
            result.put("created", true);
            return Result.success(result);
        }
        record.put("id", existing.get("id"));
        physicianMapper.updateMedicalRecord(record);
        Map<String, Object> result = new HashMap<>();
        result.put("id", existing.get("id"));
        result.put("registerId", registerId);
        result.put("created", false);
        return Result.success(result);
    }

    @PostMapping("/check-request-result")
    public Result<Void> updateCheckRequestResult(@RequestBody Map<String, Object> request) {
        physicianMapper.updateCheckRequestResult(
            toLong(request.get("registerId")),
            toLong(request.get("techId")),
            String.valueOf(request.get("result")),
            String.valueOf(request.get("state"))
        );
        return Result.success();
    }

    @PostMapping("/inspection-request-result")
    public Result<Void> updateInspectionRequestResult(@RequestBody Map<String, Object> request) {
        physicianMapper.updateInspectionRequestResult(
            toLong(request.get("registerId")),
            toLong(request.get("techId")),
            String.valueOf(request.get("result")),
            String.valueOf(request.get("state"))
        );
        return Result.success();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
