package com.xikang.physician.feign;

import com.xikang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(
    name = "physician-service",
    url = "${services.physician-service.url:http://localhost:8092}",
    configuration = PhysicianClinicalFeignConfig.class
)
public interface PhysicianClinicalFeignClient {

    @GetMapping("/api/physician/internal/medical-record/{registerId}")
    Result<Map<String, Object>> getMedicalRecord(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/patient/{registerId}")
    Result<Map<String, Object>> getPatient(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/check-results/{registerId}")
    Result<List<Map<String, Object>>> getCheckResults(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/inspection-results/{registerId}")
    Result<List<Map<String, Object>>> getInspectionResults(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/drugs")
    Result<List<Map<String, Object>>> getDrugs(@RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/api/physician/internal/medical-technologies")
    Result<List<Map<String, Object>>> getMedicalTechnologies(
        @RequestParam(value = "techType", required = false) String techType,
        @RequestParam(value = "keyword", required = false) String keyword
    );

    @GetMapping("/api/physician/internal/diseases")
    Result<List<Map<String, Object>>> getDiseases(@RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/api/physician/internal/drugs/page")
    Result<Map<String, Object>> getDrugsPage(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "pageSize", required = false) Integer pageSize
    );

    @GetMapping("/api/physician/internal/drugs/{id}")
    Result<Map<String, Object>> getDrug(@PathVariable("id") Long id);

    @GetMapping("/api/physician/internal/prescriptions/{registerId}")
    Result<List<Map<String, Object>>> getPrescriptions(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/register/{registerId}")
    Result<Map<String, Object>> getRegister(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/latest-ai-consultation/{registerId}")
    Result<Map<String, Object>> getLatestAiConsultation(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/available-examinations")
    Result<List<Map<String, Object>>> getAvailableExaminations();

    @GetMapping("/api/physician/internal/open-requests-for-simulation/{registerId}")
    Result<List<Map<String, Object>>> getOpenRequestsForSimulation(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/diseases-by-medical-record/{medicalRecordId}")
    Result<List<Map<String, Object>>> getDiseasesByMedicalRecordId(@PathVariable("medicalRecordId") Long medicalRecordId);

    @GetMapping("/api/physician/internal/visit-timeline/{registerId}")
    Result<Map<String, Object>> getVisitTimeline(@PathVariable("registerId") Long registerId);

    @GetMapping("/api/physician/internal/visit-notebook/{registerId}")
    Result<Map<String, Object>> getVisitNotebook(@PathVariable("registerId") Long registerId);

    @PostMapping("/api/physician/internal/medical-record")
    Result<Map<String, Object>> createMedicalRecord(@RequestBody Map<String, Object> request);

    @PutMapping("/api/physician/internal/medical-record/{id}")
    Result<Void> updateMedicalRecord(@PathVariable("id") Long id, @RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/preliminary-diagnosis")
    Result<Void> savePreliminaryDiagnosis(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/check-requests")
    Result<Map<String, Object>> createCheckRequest(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/inspection-requests")
    Result<Map<String, Object>> createInspectionRequest(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/disposal-requests")
    Result<Map<String, Object>> createDisposalRequest(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/diagnosis")
    Result<Map<String, Object>> submitDiagnosis(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/prescription")
    Result<Map<String, Object>> createPrescription(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/archive-visit/{registerId}")
    Result<Map<String, Object>> archiveVisit(@PathVariable("registerId") Long registerId);

    @PostMapping("/api/physician/internal/medical-record-upsert")
    Result<Map<String, Object>> upsertMedicalRecord(@RequestBody Map<String, Object> record);

    @PostMapping("/api/physician/internal/check-request-result")
    Result<Void> updateCheckRequestResult(@RequestBody Map<String, Object> request);

    @PostMapping("/api/physician/internal/inspection-request-result")
    Result<Void> updateInspectionRequestResult(@RequestBody Map<String, Object> request);
}
