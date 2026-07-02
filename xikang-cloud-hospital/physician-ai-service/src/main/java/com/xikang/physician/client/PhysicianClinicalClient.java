package com.xikang.physician.client;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.result.Result;
import com.xikang.physician.feign.PhysicianClinicalFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade over physician-service internal API for AI workflows and agent tools.
 */
@Service
@RequiredArgsConstructor
public class PhysicianClinicalClient {

    private final PhysicianClinicalFeignClient feign;

    public Map<String, Object> getMedicalRecord(Long registerId) {
        return unwrap(feign.getMedicalRecord(registerId));
    }

    public Map<String, Object> getPatient(Long registerId) {
        return unwrap(feign.getPatient(registerId));
    }

    public List<Map<String, Object>> getCheckResults(Long registerId) {
        return unwrap(feign.getCheckResults(registerId));
    }

    public List<Map<String, Object>> getInspectionResults(Long registerId) {
        return unwrap(feign.getInspectionResults(registerId));
    }

    public List<Map<String, Object>> getDrugs(String keyword) {
        return unwrap(feign.getDrugs(keyword));
    }

    public List<Map<String, Object>> getMedicalTechnologies(String techType, String keyword) {
        return unwrap(feign.getMedicalTechnologies(techType, keyword));
    }

    public List<Map<String, Object>> getDiseases(String keyword) {
        return unwrap(feign.getDiseases(keyword));
    }

    public Map<String, Object> getDrugsPage(String keyword, Integer page, Integer pageSize) {
        return unwrap(feign.getDrugsPage(keyword, page, pageSize));
    }

    public Map<String, Object> getDrug(Long id) {
        return unwrap(feign.getDrug(id));
    }

    public List<Map<String, Object>> getPrescriptionList(Long registerId) {
        return unwrap(feign.getPrescriptions(registerId));
    }

    public Map<String, Object> getRegister(Long registerId) {
        return unwrap(feign.getRegister(registerId));
    }

    public Map<String, Object> getLatestAiConsultation(Long registerId) {
        return unwrap(feign.getLatestAiConsultation(registerId));
    }

    public List<Map<String, Object>> getAvailableExaminations() {
        return unwrap(feign.getAvailableExaminations());
    }

    public List<Map<String, Object>> getOpenRequestsForSimulation(Long registerId) {
        return unwrap(feign.getOpenRequestsForSimulation(registerId));
    }

    public List<Map<String, Object>> getDiseasesByMedicalRecordId(Long medicalRecordId) {
        return unwrap(feign.getDiseasesByMedicalRecordId(medicalRecordId));
    }

    public Map<String, Object> getVisitTimeline(Long registerId) {
        return unwrap(feign.getVisitTimeline(registerId));
    }

    public Map<String, Object> getVisitNotebook(Long registerId) {
        return unwrap(feign.getVisitNotebook(registerId));
    }

    public Map<String, Object> createMedicalRecord(Map<String, Object> request) {
        return unwrap(feign.createMedicalRecord(request));
    }

    public void updateMedicalRecord(Long id, Map<String, Object> request) {
        unwrap(feign.updateMedicalRecord(id, request));
    }

    public void savePreliminaryDiagnosis(Map<String, Object> request) {
        unwrap(feign.savePreliminaryDiagnosis(request));
    }

    public Map<String, Object> createCheckRequest(Map<String, Object> request) {
        return unwrap(feign.createCheckRequest(request));
    }

    public Map<String, Object> createInspectionRequest(Map<String, Object> request) {
        return unwrap(feign.createInspectionRequest(request));
    }

    public Map<String, Object> createDisposalRequest(Map<String, Object> request) {
        return unwrap(feign.createDisposalRequest(request));
    }

    public Map<String, Object> submitDiagnosis(Map<String, Object> request) {
        return unwrap(feign.submitDiagnosis(request));
    }

    public Map<String, Object> createPrescription(Map<String, Object> request) {
        return unwrap(feign.createPrescription(request));
    }

    public Map<String, Object> archiveVisit(Long registerId) {
        return unwrap(feign.archiveVisit(registerId));
    }

    public Map<String, Object> upsertMedicalRecord(Map<String, Object> record) {
        return unwrap(feign.upsertMedicalRecord(record));
    }

    public void updateCheckRequestResult(Long registerId, Long techId, String result, String state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registerId", registerId);
        body.put("techId", techId);
        body.put("result", result);
        body.put("state", state);
        unwrap(feign.updateCheckRequestResult(body));
    }

    public void updateInspectionRequestResult(Long registerId, Long techId, String result, String state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registerId", registerId);
        body.put("techId", techId);
        body.put("result", result);
        body.put("state", state);
        unwrap(feign.updateInspectionRequestResult(body));
    }

    public Map<String, Object> getDifyWorkflowContracts() {
        return Map.of(
            "w1Structure", Map.of(
                "inputs", List.of("registerId", "inputMode", "longText", "doctorForm", "structuredRecord", "patientInfoFromRegister"),
                "outputs", List.of("registerId", "patientInfo", "chiefComplaint", "symptomDuration", "presentIllness", "history", "allergy", "physique", "preliminaryImpression")
            ),
            "w2Recommend", Map.of(
                "inputs", List.of("clinical_context_json", "available_examinations_json"),
                "outputs", List.of("preliminaryAssessment", "recommendedExaminations", "notRecommendedNote", "unmatchedSuggestions")
            ),
            "w2bSimulate", Map.of(
                "inputs", List.of("registerId", "structuredRecord", "orderedExaminations", "simulationProfile"),
                "outputs", List.of("simulatedResults")
            ),
            "w3Analyze", Map.of(
                "inputs", List.of("registerId", "structuredRecordJson", "allResultsJson", "preliminaryAssessment"),
                "outputs", List.of("registerId", "clinicalImpression", "examSummaries", "overallAnalysis", "explicitNonDiagnosis")
            ),
            "w4Diagnose", Map.of(
                "inputs", List.of("register_id", "patient_info_text", "chief_complaint", "present_illness", "past_history", "allergy_history", "preliminary_diagnosis_text", "preliminary_diseases_text", "check_results_text", "inspection_results_text", "w3_analysis_text", "abnormal_indicators_text", "ai_previsit_summary", "doctor_notes"),
                "outputs", List.of("status", "registerId", "suggestions", "fallbackSuggestions", "clinicalSummaryForDoctor", "differentialDiagnosis", "warningSigns", "searchAdvice")
            ),
            "w5RecommendDrugs", Map.of(
                "inputs", List.of("register_id", "patient_info_text", "confirmed_diagnosis_text", "w4_suggestions_text", "allergy_history", "past_history", "chief_complaint", "w3_analysis_text", "abnormal_indicators_text", "preliminary_diagnosis_text", "doctor_notes"),
                "outputs", List.of("status", "registerId", "suggestions", "fallbackSuggestions", "clinicalSummaryForDoctor", "allergyWarnings", "searchAdvice")
            ),
            "preliminaryDiagnosis", Map.of(
                "inputs", List.of("registerId", "text", "preHandle", "model"),
                "outputs", List.of("diagnosisText", "diagnosisBasis", "confidence", "suggestedDiseases", "modelId", "llmModel")
            ),
            "ctModel", getCtModelOutputContract()
        );
    }

    public Map<String, Object> getCtModelOutputContract() {
        return Map.of(
            "input", Map.of(
                "required", List.of("checkRequestId", "registerId", "dicomSeriesOrImageUri"),
                "optional", List.of("age", "gender", "chiefComplaint", "checkPosition", "checkPurpose")
            ),
            "output", Map.of(
                "hasAbnormality", "boolean",
                "abnormalProbability", "number",
                "riskLevel", List.of("normal", "attention", "warning", "danger"),
                "findings", List.of(Map.of(
                    "findingType", "string",
                    "anatomicalLocation", "string",
                    "size", "string",
                    "severity", "string",
                    "confidence", "number",
                    "bbox", "optional",
                    "maskUri", "optional"
                )),
                "aiImpression", "string",
                "limitations", "string"
            ),
            "databaseMapping", Map.of(
                "originalResult", "ai_exam_analysis.original_result",
                "findings", "ai_exam_analysis.abnormal_indicators",
                "riskLevel", "ai_exam_analysis.risk_level",
                "aiImpression", "ai_exam_analysis.analysis_report",
                "correlationAnalysis", "ai_exam_analysis.correlation_analysis"
            )
        );
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(500, "physician-service 无响应");
        }
        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
