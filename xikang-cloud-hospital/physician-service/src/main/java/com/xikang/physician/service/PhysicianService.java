package com.xikang.physician.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.physician.mapper.PhysicianMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Physician Service
 */
@Service
public class PhysicianService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int VISIT_REGISTERED = 1;
    private static final int VISIT_IN_PROGRESS = 2;
    private static final int VISIT_ENDED = 3;
    private static final int VISIT_EXAM_PENDING = 5;
    private static final int VISIT_EXAM_COMPLETED = 6;

    private final PhysicianMapper physicianMapper;

    public PhysicianService(PhysicianMapper physicianMapper) {
        this.physicianMapper = physicianMapper;
    }

    public Map<String, Object> getPatients(String keyword, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : size;
        int offset = (currentPage - 1) * pageSize;
        List<Map<String, Object>> records = physicianMapper.selectPatients(keyword, offset, pageSize).stream()
            .map(this::withAiConsultSummary)
            .map(this::syncExamStateIfNeeded)
            .toList();
        long total = physicianMapper.countPatients(keyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public Map<String, Object> getPatientStats() {
        return physicianMapper.selectPatientStats();
    }

    public Map<String, Object> getPatient(Long registerId) {
        Map<String, Object> row = physicianMapper.selectPatientByRegisterId(registerId);
        if (row == null) {
            return null;
        }
        return syncExamStateIfNeeded(withAiConsultSummary(row));
    }

    @Transactional
    public Map<String, Object> startEncounter(Long registerId) {
        int current = currentVisitState(registerId);
        if (current == VISIT_REGISTERED) {
            physicianMapper.updateVisitState(registerId, VISIT_IN_PROGRESS);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    @Transactional
    public Map<String, Object> endVisit(Long registerId) {
        int current = currentVisitState(registerId);
        if (current == VISIT_IN_PROGRESS || current == VISIT_EXAM_PENDING || current == VISIT_EXAM_COMPLETED) {
            physicianMapper.updateVisitState(registerId, VISIT_ENDED);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    public Map<String, Object> getMedicalRecord(Long registerId) {
        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (record == null) {
            return null;
        }
        Long medicalRecordId = toLong(record.get("id"));
        record.put("diseases", physicianMapper.selectDiseasesByMedicalRecordId(medicalRecordId));
        record.put("preliminaryAiMeta", loadPreliminaryAiMeta(registerId));
        return record;
    }

    @Transactional
    public void savePreliminaryDiagnosis(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        Map<String, Object> existing = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        Long medicalRecordId;
        if (existing == null) {
            Map<String, Object> record = new HashMap<>();
            record.put("registerId", registerId);
            physicianMapper.insertMedicalRecord(record);
            medicalRecordId = toLong(record.get("id"));
        } else {
            medicalRecordId = toLong(existing.get("id"));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("id", medicalRecordId);
        update.put("preliminaryDiagnosis", request.get("preliminaryDiagnosis"));
        physicianMapper.updateMedicalRecordPreliminary(update);
        List<Long> ids = diseaseIds(request);
        if (!ids.isEmpty()) {
            syncRecordDiseases(medicalRecordId, ids);
        }
        persistPreliminaryDoctorSave(registerId, request);
    }

    private void persistPreliminaryDoctorSave(Long registerId, Map<String, Object> request) {
        List<String> names = stringList(request.get("suggestedDiseaseNames"));
        if (registerId == null || names.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("suggestedDiseaseNames", names);
            meta.put("savedBy", "doctor");
            Map<String, Object> log = new HashMap<>();
            log.put("registerId", registerId);
            log.put("sourceType", "preliminary_diagnosis");
            log.put("aiDiagnosis", request.get("preliminaryDiagnosis"));
            log.put("modelId", "doctor-save");
            log.put("doctorModification", JSON.writeValueAsString(meta));
            physicianMapper.insertAiMedicalRecordLog(log);
        } catch (Exception ignored) {
            // ignore log persistence errors
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    @Transactional
    public Map<String, Object> createMedicalRecord(Map<String, Object> request) {
        Map<String, Object> record = copyRecordFields(request);
        physicianMapper.insertMedicalRecord(record);
        Long medicalRecordId = toLong(record.get("id"));
        syncRecordDiseases(medicalRecordId, diseaseIds(request));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", medicalRecordId);
        result.put("registerId", toLong(request.get("registerId")));
        return result;
    }

    @Transactional
    public void updateMedicalRecord(Long id, Map<String, Object> request) {
        Map<String, Object> record = copyRecordFields(request);
        record.put("id", id);
        physicianMapper.updateMedicalRecord(record);
        syncRecordDiseases(id, diseaseIds(request));
    }

    public List<Map<String, Object>> getMedicalTechnologies(String techType, String keyword) {
        return physicianMapper.selectMedicalTechnologies(techType, keyword);
    }

    @Transactional
    public Map<String, Object> createCheckRequest(Map<String, Object> request) {
        Map<String, Object> result = createTechnologyRequest(request, "check");
        markExamPending(toLong(request.get("registerId")));
        result.put("visitState", currentVisitState(toLong(request.get("registerId"))));
        return result;
    }

    @Transactional
    public Map<String, Object> createInspectionRequest(Map<String, Object> request) {
        Map<String, Object> result = createTechnologyRequest(request, "inspection");
        markExamPending(toLong(request.get("registerId")));
        result.put("visitState", currentVisitState(toLong(request.get("registerId"))));
        return result;
    }

    @Transactional
    public Map<String, Object> createDisposalRequest(Map<String, Object> request) {
        return createTechnologyRequest(request, "disposal");
    }

    public List<Map<String, Object>> getCheckResults(Long registerId) {
        return physicianMapper.selectCheckResults(registerId).stream().map(this::withAiAnalysis).toList();
    }

    public List<Map<String, Object>> getInspectionResults(Long registerId) {
        return physicianMapper.selectInspectionResults(registerId).stream().map(this::withAiAnalysis).toList();
    }

    public List<Map<String, Object>> getDiseases(String keyword) {
        return physicianMapper.selectDiseases(keyword);
    }

    @Transactional
    public void submitDiagnosis(Map<String, Object> request) {
        physicianMapper.updateDiagnosis(request);
        Long medicalRecordId = toLong(request.get("medicalRecordId"));
        syncRecordDiseases(medicalRecordId, diseaseIds(request));
    }

    public List<Map<String, Object>> getDiagnosisList(Long registrationId) {
        return physicianMapper.selectDiagnosisSuggestions(registrationId);
    }

    public Map<String, Object> createDiagnosis(Map<String, Object> diagnosisRequest) {
        submitDiagnosis(diagnosisRequest);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "saved");
        return result;
    }

    public List<Map<String, Object>> getDrugs(String keyword) {
        return physicianMapper.selectDrugs(keyword);
    }

    public Map<String, Object> getDrug(Long id) {
        return physicianMapper.selectDrugById(id);
    }

    public List<Map<String, Object>> getPrescriptionList(Long registrationId) {
        return physicianMapper.selectPrescriptions(registrationId);
    }

    @Transactional
    public Map<String, Object> createPrescription(Map<String, Object> prescriptionRequest) {
        Long registerId = toLong(prescriptionRequest.get("registerId"));
        List<Map<String, Object>> items = requestItems(prescriptionRequest);
        List<Long> prescriptionIds = items.stream().map(item -> {
            Map<String, Object> row = new HashMap<>(item);
            row.put("registerId", registerId);
            physicianMapper.insertPrescription(row);
            return toLong(row.get("id"));
        }).toList();

        BigDecimal totalAmount = physicianMapper.selectPrescriptions(registerId).stream()
            .map(item -> toDecimal(item.get("drugPrice")).multiply(new BigDecimal(String.valueOf(item.getOrDefault("drugNumber", "0")))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        endVisit(registerId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prescriptionIds", prescriptionIds);
        result.put("totalAmount", totalAmount);
        result.put("confirmedDiagnosis", prescriptionRequest.get("confirmedDiagnosis"));
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    public void deletePrescription(Long id) {
        physicianMapper.deletePrescription(id);
    }

    public List<Map<String, Object>> getExamSuggestions(Long registerId) {
        return physicianMapper.selectExamSuggestions(registerId);
    }

    public List<Map<String, Object>> getDiagnosisSuggestions(Long registerId) {
        return physicianMapper.selectDiagnosisSuggestions(registerId);
    }

    public Map<String, Object> getPrescriptionReview(Long registerId) {
        return fallbackReview(registerId);
    }

    public Map<String, Object> getDifyWorkflowContracts() {
        return Map.of(
            "w1Structure", Map.of(
                "inputs", List.of("registerId", "inputMode", "longText", "doctorForm", "structuredRecord", "patientInfoFromRegister"),
                "outputs", List.of("registerId", "patientInfo", "chiefComplaint", "symptomDuration", "presentIllness", "history", "allergy", "physique", "preliminaryImpression")
            ),
            "w2Recommend", Map.of(
                "inputs", List.of("clinical_context_json", "available_examinations_json"),
                "outputs", List.of(
                    "preliminaryAssessment",
                    "recommendedExaminations",
                    "notRecommendedNote",
                    "unmatchedSuggestions"
                )
            ),
            "w2bSimulate", Map.of(
                "inputs", List.of("registerId", "structuredRecord", "orderedExaminations", "simulationProfile"),
                "outputs", List.of("simulatedResults")
            ),
            "w3Analyze", Map.of(
                "inputs", List.of("registerId", "structuredRecord", "preliminaryAssessment", "allResults"),
                "outputs", List.of("examSummaries", "overallAnalysis", "explicitNonDiagnosis")
            ),
            "w4Diagnose", Map.of(
                "inputs", List.of("registerId", "structuredRecord", "w3Output", "diseaseCatalog"),
                "outputs", List.of("primaryDiagnosis", "differentialDiagnoses", "clinicalAdvice", "confidenceScore")
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

    private void markExamPending(Long registerId) {
        if (registerId == null) {
            return;
        }
        int current = currentVisitState(registerId);
        if (current == VISIT_IN_PROGRESS || current == VISIT_EXAM_COMPLETED) {
            physicianMapper.updateVisitState(registerId, VISIT_EXAM_PENDING);
        }
    }

    private Map<String, Object> syncExamStateIfNeeded(Map<String, Object> row) {
        Long registerId = toLong(row.get("registerId"));
        int state = toInt(row.get("visitState"));
        if (registerId != null && state == VISIT_EXAM_PENDING) {
            int synced = syncExamCompletedState(registerId);
            row.put("visitState", synced);
        }
        return row;
    }

    private int syncExamCompletedState(Long registerId) {
        int current = currentVisitState(registerId);
        if (current != VISIT_EXAM_PENDING) {
            return current;
        }
        if (physicianMapper.countPendingExamRequests(registerId) == 0) {
            physicianMapper.updateVisitState(registerId, VISIT_EXAM_COMPLETED);
            return VISIT_EXAM_COMPLETED;
        }
        return VISIT_EXAM_PENDING;
    }

    private int currentVisitState(Long registerId) {
        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (register == null) {
            return VISIT_REGISTERED;
        }
        return toInt(register.get("visitState"));
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return VISIT_REGISTERED;
    }

    private Map<String, Object> withAiConsultSummary(Map<String, Object> row) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chiefComplaint", row.remove("chiefComplaint"));
        summary.put("symptomDuration", row.remove("symptomDuration"));
        summary.put("historySummary", row.remove("historySummary"));
        summary.put("allergySummary", row.remove("allergySummary"));
        summary.put("medicationSummary", row.remove("medicationSummary"));
        summary.put("aiSummary", row.remove("aiSummary"));
        summary.put("suggestedExam", row.remove("suggestedExam"));
        row.put("aiConsultSummary", summary.values().stream().allMatch(Objects::isNull) ? null : summary);
        return row;
    }

    private Map<String, Object> withAiAnalysis(Map<String, Object> row) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("riskLevel", row.remove("riskLevel"));
        analysis.put("analysisReport", row.remove("analysisReport"));
        analysis.put("abnormalIndicators", row.remove("abnormalIndicators"));
        analysis.put("correlationAnalysis", row.remove("correlationAnalysis"));
        row.put("aiAnalysis", analysis.values().stream().allMatch(Objects::isNull) ? null : analysis);
        return row;
    }

    private Map<String, Object> copyRecordFields(Map<String, Object> request) {
        Map<String, Object> record = new HashMap<>();
        record.put("registerId", toLong(request.get("registerId")));
        record.put("readme", request.get("readme"));
        record.put("present", request.get("present"));
        record.put("presentTreat", request.get("presentTreat"));
        record.put("history", request.get("history"));
        record.put("allergy", request.get("allergy"));
        record.put("physique", request.get("physique"));
        record.put("proposal", request.get("proposal"));
        return record;
    }

    private Map<String, Object> createTechnologyRequest(Map<String, Object> request, String type) {
        Long registerId = toLong(request.get("registerId"));
        List<Long> requestIds = requestItems(request).stream().map(item -> {
            Map<String, Object> row = new HashMap<>(item);
            row.put("registerId", registerId);
            if ("check".equals(type)) {
                physicianMapper.insertCheckRequest(row);
            } else if ("inspection".equals(type)) {
                physicianMapper.insertInspectionRequest(row);
            } else {
                physicianMapper.insertDisposalRequest(row);
            }
            return toLong(row.get("id"));
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestIds", requestIds);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestItems(Map<String, Object> request) {
        Object items = request.get("items");
        if (items instanceof List<?>) {
            return ((List<?>) items).stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private void syncRecordDiseases(Long medicalRecordId, List<Long> diseaseIds) {
        if (medicalRecordId == null) {
            return;
        }
        physicianMapper.deleteMedicalRecordDiseases(medicalRecordId);
        diseaseIds.forEach(diseaseId -> physicianMapper.insertMedicalRecordDisease(medicalRecordId, diseaseId));
    }

    private List<Long> diseaseIds(Map<String, Object> request) {
        Object value = request.get("diseaseIds");
        if (value instanceof List<?>) {
            return ((List<?>) value).stream().map(this::toLong).filter(Objects::nonNull).toList();
        }
        return List.of();
    }

    private Map<String, Object> loadPreliminaryAiMeta(Long registerId) {
        Map<String, Object> log = physicianMapper.selectLatestAiMedicalRecordLogBySourceType(registerId, "preliminary_diagnosis");
        if (log == null) {
            return Map.of();
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("aiDiagnosis", log.get("aiDiagnosis"));
        meta.put("modelId", log.get("modelId"));
        Object raw = log.get("doctorModification");
        if (raw instanceof String text && !text.isBlank()) {
            try {
                Map<String, Object> parsed = JSON.readValue(text, new TypeReference<>() {
                });
                meta.putAll(parsed);
            } catch (Exception ignored) {
                // ignore malformed meta
            }
        }
        return meta;
    }

    private Map<String, Object> fallbackReview(Long registerId) {
        Map<String, Object> stored = physicianMapper.selectLatestPrescriptionReview(registerId);
        if (stored != null) {
            return stored;
        }
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("reviewResult", "passed");
        review.put("riskScore", 0);
        review.put("riskDetails", null);
        review.put("status", "not_called");
        return review;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        return BigDecimal.ZERO;
    }
}
