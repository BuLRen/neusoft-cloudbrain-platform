package com.xikang.physician.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.physician.context.PhysicianAuthContext;
import com.xikang.physician.mapper.ClinicalRecordMapper;
import com.xikang.physician.mapper.PhysicianMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ClinicalRecordService {

    private final ClinicalRecordMapper clinicalRecordMapper;
    private final PhysicianMapper physicianMapper;

    public ClinicalRecordService(ClinicalRecordMapper clinicalRecordMapper, PhysicianMapper physicianMapper) {
        this.clinicalRecordMapper = clinicalRecordMapper;
        this.physicianMapper = physicianMapper;
    }

    public Map<String, Object> getVisitTimeline(Long registerId) {
        assertRegisterAccess(registerId);
        Map<String, Object> header = clinicalRecordMapper.selectRegisterHeader(registerId);
        if (header == null) {
            throw new BusinessException(404, "就诊记录不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("clinicalArchivedAt", header.get("clinicalArchivedAt"));
        result.put("archived", header.get("clinicalArchivedAt") != null);
        result.put("timeline", buildTimeline(registerId, true));
        return result;
    }

    public Map<String, Object> getVisitNotebook(Long registerId) {
        assertRegisterAccess(registerId);
        Map<String, Object> header = clinicalRecordMapper.selectRegisterHeader(registerId);
        if (header == null) {
            throw new BusinessException(404, "就诊记录不存在");
        }
        Map<String, Object> result = buildNotebook(registerId, header);
        result.put("registerId", registerId);
        result.put("clinicalArchivedAt", header.get("clinicalArchivedAt"));
        result.put("archived", header.get("clinicalArchivedAt") != null);
        return result;
    }

    public Map<String, Object> getPatientProfile(Long patientId) {
        Map<String, Object> basic = clinicalRecordMapper.selectPatientBasic(patientId);
        if (basic == null) {
            throw new BusinessException(404, "患者不存在");
        }
        Map<String, Object> profile = clinicalRecordMapper.selectPatientClinicalProfile(patientId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientId", patientId);
        result.put("realName", basic.get("realName"));
        if (profile != null) {
            result.putAll(profile);
        } else {
            result.put("allergySummary", basic.get("allergyHistory"));
            result.put("chronicConditions", null);
            result.put("pastDiagnosisSummary", null);
            result.put("lastVisitAt", null);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updatePatientProfile(Long patientId, Map<String, Object> request) {
        Map<String, Object> basic = clinicalRecordMapper.selectPatientBasic(patientId);
        if (basic == null) {
            throw new BusinessException(404, "患者不存在");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("patientId", patientId);
        profile.put("allergySummary", request.get("allergySummary"));
        profile.put("chronicConditions", request.get("chronicConditions"));
        profile.put("pastDiagnosisSummary", request.get("pastDiagnosisSummary"));
        profile.put("lastVisitAt", request.get("lastVisitAt"));
        clinicalRecordMapper.upsertPatientClinicalProfile(profile);
        return getPatientProfile(patientId);
    }

    @Transactional
    public Map<String, Object> archiveVisit(Long registerId) {
        assertRegisterAccess(registerId);
        Map<String, Object> header = clinicalRecordMapper.selectRegisterHeader(registerId);
        if (header == null) {
            throw new BusinessException(404, "就诊记录不存在");
        }
        if (header.get("clinicalArchivedAt") != null) {
            return getVisitTimeline(registerId);
        }
        Long employeeId = PhysicianAuthContext.employeeIdOrNull();
        clinicalRecordMapper.archiveRegister(registerId, employeeId);
        syncProfileOnArchive(header);
        return getVisitTimeline(registerId);
    }

    private void assertRegisterAccess(Long registerId) {
        if (registerId == null) {
            throw new BusinessException(400, "挂号记录不存在");
        }
        if (PhysicianAuthContext.isAdminAllAccess()) {
            return;
        }
        Long currentEmployeeId = PhysicianAuthContext.employeeIdOrNull();
        if (currentEmployeeId == null) {
            throw new BusinessException(403, "无权访问该患者");
        }
        Long ownerEmployeeId = physicianMapper.selectRegisterEmployeeId(registerId);
        if (ownerEmployeeId == null || !ownerEmployeeId.equals(currentEmployeeId)) {
            throw new BusinessException(403, "无权访问该患者");
        }
    }

    private void syncProfileOnArchive(Map<String, Object> header) {
        Long patientId = toLong(header.get("patientId"));
        if (patientId == null) {
            return;
        }
        Long registerId = toLong(header.get("registerId"));
        Map<String, Object> medicalRecord = clinicalRecordMapper.selectMedicalRecord(registerId);
        Map<String, Object> basic = clinicalRecordMapper.selectPatientBasic(patientId);
        Map<String, Object> existing = clinicalRecordMapper.selectPatientClinicalProfile(patientId);

        String diagnosis = medicalRecord != null ? stringValue(medicalRecord.get("diagnosis")) : null;
        String pastSummary = existing != null ? stringValue(existing.get("pastDiagnosisSummary")) : "";
        if (diagnosis != null && !diagnosis.isBlank()) {
            String visitLabel = formatVisitLabel(header);
            String entry = visitLabel + "：" + diagnosis;
            pastSummary = pastSummary == null || pastSummary.isBlank()
                ? entry
                : pastSummary + "\n" + entry;
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("patientId", patientId);
        profile.put("allergySummary", existing != null && existing.get("allergySummary") != null
            ? existing.get("allergySummary")
            : basic != null ? basic.get("allergyHistory") : null);
        profile.put("chronicConditions", existing != null ? existing.get("chronicConditions") : null);
        profile.put("pastDiagnosisSummary", pastSummary);
        profile.put("lastVisitAt", header.get("visitDate"));
        clinicalRecordMapper.upsertPatientClinicalProfile(profile);
    }

    private List<Map<String, Object>> buildTimeline(Long registerId, boolean includeDetail) {
        List<Map<String, Object>> events = new ArrayList<>();

        Map<String, Object> header = clinicalRecordMapper.selectRegisterHeader(registerId);
        if (header != null && header.get("visitDate") != null) {
            events.add(event("visit_start", header.get("visitDate"), "挂号就诊",
                stringValue(header.get("departmentName")) + " / " + stringValue(header.get("physicianName")),
                "completed", "register", toLong(header.get("registerId")),
                includeDetail ? Map.of(
                    "departmentName", nullSafe(header.get("departmentName")),
                    "physicianName", nullSafe(header.get("physicianName")),
                    "visitDate", header.get("visitDate")
                ) : null));
        }

        Map<String, Object> triage = clinicalRecordMapper.selectAiTriageByRegister(registerId);
        if (triage != null) {
            events.add(event("triage", firstNonNull(triage.get("triageTime"), header != null ? header.get("visitDate") : null),
                "AI 导诊", stringValue(triage.get("symptomDescription")),
                "completed", "ai_triage_record", toLong(triage.get("id")),
                includeDetail ? triage : null));
        }

        Map<String, Object> preConsult = clinicalRecordMapper.selectLatestPreConsultation(registerId);
        if (preConsult != null) {
            String summary = firstNonBlank(
                stringValue(preConsult.get("chiefComplaint")),
                stringValue(preConsult.get("aiSummary"))
            );
            events.add(event("pre_consultation",
                firstNonNull(preConsult.get("completionTime"), preConsult.get("creationTime")),
                "AI 预问诊", summary, "completed", "ai_consultation_record", toLong(preConsult.get("id")),
                includeDetail ? preConsult : null));
        }

        Map<String, Object> medicalRecord = clinicalRecordMapper.selectMedicalRecord(registerId);
        if (medicalRecord != null) {
            boolean hasContent = hasAnyText(medicalRecord, "readme", "present", "history", "allergy", "physique", "proposal");
            if (hasContent) {
                events.add(event("medical_record", header != null ? header.get("visitDate") : null,
                    "门诊病历", firstNonBlank(stringValue(medicalRecord.get("readme")), "病历已保存"),
                    "completed", "medical_record", toLong(medicalRecord.get("id")),
                    includeDetail ? medicalRecord : null));
            }
            if (hasText(medicalRecord.get("preliminaryDiagnosis"))) {
                events.add(event("preliminary_diagnosis", header != null ? header.get("visitDate") : null,
                    "初步诊断", stringValue(medicalRecord.get("preliminaryDiagnosis")),
                    "completed", "medical_record", toLong(medicalRecord.get("id")),
                    includeDetail ? Map.of("preliminaryDiagnosis", medicalRecord.get("preliminaryDiagnosis")) : null));
            }
            if (hasText(medicalRecord.get("diagnosis"))) {
                Long medicalRecordId = toLong(medicalRecord.get("id"));
                Map<String, Object> diagnosisDetail = new LinkedHashMap<>();
                if (includeDetail) {
                    diagnosisDetail.put("diagnosis", medicalRecord.get("diagnosis"));
                    diagnosisDetail.put("cure", medicalRecord.get("cure"));
                    diagnosisDetail.put("careful", medicalRecord.get("careful"));
                    if (medicalRecordId != null) {
                        diagnosisDetail.put("diseases", clinicalRecordMapper.selectDiseasesByMedicalRecordId(medicalRecordId));
                    }
                }
                events.add(event("diagnosis", header != null ? header.get("visitDate") : null,
                    "门诊确诊", stringValue(medicalRecord.get("diagnosis")),
                    "completed", "medical_record", medicalRecordId, includeDetail ? diagnosisDetail : null));
            }
        }

        for (Map<String, Object> row : clinicalRecordMapper.selectCheckRequests(registerId)) {
            events.add(event("check_order", row.get("creationTime"), "检查申请",
                stringValue(row.get("techName")) + (hasText(row.get("checkPosition")) ? "（" + row.get("checkPosition") + "）" : ""),
                stateToStatus(stringValue(row.get("checkState"))),
                "check_request", toLong(row.get("id")), includeDetail ? row : null));
            if (hasText(row.get("checkResult"))) {
                events.add(event("check_result", firstNonNull(row.get("checkTime"), row.get("creationTime")),
                    "检查结果", stringValue(row.get("techName")),
                    "completed", "check_request", toLong(row.get("id")),
                    includeDetail ? Map.of(
                        "techName", row.get("techName"),
                        "checkResult", row.get("checkResult"),
                        "checkState", row.get("checkState"),
                        "checkTime", row.get("checkTime")
                    ) : null));
            }
        }

        for (Map<String, Object> row : clinicalRecordMapper.selectInspectionRequests(registerId)) {
            events.add(event("inspection_order", row.get("creationTime"), "检验申请",
                stringValue(row.get("techName")),
                stateToStatus(stringValue(row.get("inspectionState"))),
                "inspection_request", toLong(row.get("id")), includeDetail ? row : null));
            if (hasText(row.get("inspectionResult"))) {
                events.add(event("inspection_result", firstNonNull(row.get("inspectionTime"), row.get("creationTime")),
                    "检验结果", stringValue(row.get("techName")),
                    "completed", "inspection_request", toLong(row.get("id")),
                    includeDetail ? Map.of(
                        "techName", row.get("techName"),
                        "inspectionResult", row.get("inspectionResult"),
                        "inspectionState", row.get("inspectionState"),
                        "inspectionTime", row.get("inspectionTime")
                    ) : null));
            }
        }

        for (Map<String, Object> row : clinicalRecordMapper.selectDisposalRequests(registerId)) {
            events.add(event("disposal_order", row.get("creationTime"), "处置申请",
                stringValue(row.get("techName")),
                stateToStatus(stringValue(row.get("disposalState"))),
                "disposal_request", toLong(row.get("id")), includeDetail ? row : null));
            if (hasText(row.get("disposalResult"))) {
                events.add(event("disposal_result", firstNonNull(row.get("disposalTime"), row.get("creationTime")),
                    "处置结果", stringValue(row.get("techName")),
                    "completed", "disposal_request", toLong(row.get("id")),
                    includeDetail ? Map.of(
                        "techName", row.get("techName"),
                        "disposalResult", row.get("disposalResult"),
                        "disposalState", row.get("disposalState"),
                        "disposalTime", row.get("disposalTime")
                    ) : null));
            }
        }

        List<Map<String, Object>> prescriptions = clinicalRecordMapper.selectPrescriptions(registerId);
        if (!prescriptions.isEmpty()) {
            Object occurredAt = prescriptions.get(0).get("creationTime");
            String summary = prescriptions.stream()
                .map(p -> stringValue(p.get("drugName")))
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "、" + b)
                .orElse("处方");
            events.add(event("prescription", occurredAt, "处方",
                summary, "completed", "prescription", toLong(prescriptions.get(0).get("id")),
                includeDetail ? Map.of("items", prescriptions) : null));
        }

        if (header != null && header.get("clinicalArchivedAt") != null) {
            events.add(event("visit_archived", header.get("clinicalArchivedAt"), "病历归档",
                "医生已归档并发布给患者", "completed", "register", registerId,
                includeDetail ? Map.of("clinicalArchivedAt", header.get("clinicalArchivedAt")) : null));
        }

        events.sort(Comparator.comparing(
            e -> parseTime(e.get("occurredAt")),
            Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return events;
    }

    private Map<String, Object> buildNotebook(Long registerId, Map<String, Object> header) {
        Map<String, Object> notebook = new LinkedHashMap<>();

        Map<String, Object> headerSection = new LinkedHashMap<>();
        headerSection.put("caseNumber", header.get("caseNumber"));
        headerSection.put("realName", header.get("realName"));
        headerSection.put("gender", header.get("gender"));
        headerSection.put("age", header.get("age"));
        headerSection.put("departmentName", header.get("departmentName"));
        headerSection.put("physicianName", header.get("physicianName"));
        headerSection.put("visitDate", header.get("visitDate"));
        notebook.put("header", headerSection);

        Map<String, Object> medicalRecord = clinicalRecordMapper.selectMedicalRecord(registerId);
        Map<String, Object> medicalSummary = new LinkedHashMap<>();
        if (medicalRecord != null) {
            medicalSummary.put("readme", medicalRecord.get("readme"));
            medicalSummary.put("present", medicalRecord.get("present"));
            medicalSummary.put("history", medicalRecord.get("history"));
            medicalSummary.put("allergy", medicalRecord.get("allergy"));
            medicalSummary.put("physique", medicalRecord.get("physique"));
        }
        notebook.put("medicalSummary", medicalSummary);
        notebook.put("preliminaryDiagnosis", medicalRecord != null
            ? stringValue(medicalRecord.get("preliminaryDiagnosis"))
            : "");

        Map<Long, String> aiByCheckId = new HashMap<>();
        Map<Long, String> aiByInspectionId = new HashMap<>();
        for (Map<String, Object> row : physicianMapper.selectCheckResults(registerId)) {
            Long id = toLong(row.get("id"));
            String analysis = extractAiAnalysisText(row);
            if (id != null && analysis != null) {
                aiByCheckId.put(id, analysis);
            }
        }
        for (Map<String, Object> row : physicianMapper.selectInspectionResults(registerId)) {
            Long id = toLong(row.get("id"));
            String analysis = extractAiAnalysisText(row);
            if (id != null && analysis != null) {
                aiByInspectionId.put(id, analysis);
            }
        }
        Map<String, String> aiByTechName = buildAiAnalysisByTechName(registerId);

        List<Map<String, Object>> examItems = new ArrayList<>();
        for (Map<String, Object> row : clinicalRecordMapper.selectCheckRequests(registerId)) {
            examItems.add(buildExamItem(row, "check", aiByCheckId, aiByTechName));
        }
        for (Map<String, Object> row : clinicalRecordMapper.selectInspectionRequests(registerId)) {
            examItems.add(buildExamItem(row, "inspection", aiByInspectionId, aiByTechName));
        }
        notebook.put("examItems", examItems);

        notebook.put("w3Analysis", buildW3AnalysisSection(registerId));

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        if (medicalRecord != null) {
            diagnosis.put("diagnosis", medicalRecord.get("diagnosis"));
            diagnosis.put("cure", medicalRecord.get("cure"));
            diagnosis.put("careful", medicalRecord.get("careful"));
            Long medicalRecordId = toLong(medicalRecord.get("id"));
            if (medicalRecordId != null) {
                diagnosis.put("diseases", clinicalRecordMapper.selectDiseasesByMedicalRecordId(medicalRecordId));
            } else {
                diagnosis.put("diseases", List.of());
            }
        } else {
            diagnosis.put("diseases", List.of());
        }
        notebook.put("diagnosis", diagnosis);

        List<Map<String, Object>> prescriptionItems = clinicalRecordMapper.selectPrescriptions(registerId).stream()
            .map(item -> {
                Map<String, Object> mapped = new LinkedHashMap<>();
                mapped.put("drugName", item.get("drugName"));
                mapped.put("drugUsage", item.get("drugUsage"));
                mapped.put("drugNumber", item.get("drugNumber"));
                return mapped;
            })
            .toList();
        notebook.put("prescription", Map.of("items", prescriptionItems));

        return notebook;
    }

    private Map<String, Object> buildExamItem(
        Map<String, Object> row,
        String category,
        Map<Long, String> aiById,
        Map<String, String> aiByTechName
    ) {
        Long id = toLong(row.get("id"));
        String techName = stringValue(row.get("techName"));
        String state = "check".equals(category)
            ? stringValue(row.get("checkState"))
            : stringValue(row.get("inspectionState"));
        Object resultRaw = "check".equals(category) ? row.get("checkResult") : row.get("inspectionResult");
        Object completedAt = "check".equals(category) ? row.get("checkTime") : row.get("inspectionTime");

        String aiAnalysis = id != null ? aiById.get(id) : null;
        if ((aiAnalysis == null || aiAnalysis.isBlank()) && techName != null) {
            aiAnalysis = aiByTechName.get(techName);
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("techName", techName);
        item.put("category", category);
        item.put("state", state != null ? state : "");
        item.put("resultSummary", summarizeResult(resultRaw, category));
        item.put("resultRaw", resultRaw != null ? String.valueOf(resultRaw) : null);
        item.put("aiAnalysis", aiAnalysis);
        item.put("orderedAt", row.get("creationTime"));
        item.put("completedAt", completedAt);
        return item;
    }

    private Map<String, Object> buildW3AnalysisSection(Long registerId) {
        List<Map<String, Object>> analyses = physicianMapper.selectExamAnalysisByRegisterId(registerId);
        Map<String, Object> section = new LinkedHashMap<>();
        boolean completed = !analyses.isEmpty();
        section.put("completed", completed);

        String overallAnalysis = "";
        for (Map<String, Object> row : analyses) {
            Map<String, Object> indicators = parseJsonMap(row.get("abnormalIndicators"));
            Map<String, Object> w3Global = indicators != null
                ? castMap(indicators.get("w3Global"))
                : null;
            if (w3Global != null) {
                String fromGlobal = stringValue(w3Global.get("overallAnalysis"));
                if (fromGlobal != null && !fromGlobal.isBlank()) {
                    overallAnalysis = fromGlobal.trim();
                    break;
                }
            }
            String correlation = stringValue(row.get("correlationAnalysis"));
            if (correlation != null && !correlation.isBlank()) {
                overallAnalysis = correlation.trim();
                break;
            }
        }
        if (!overallAnalysis.isBlank()) {
            section.put("overallAnalysis", overallAnalysis);
        }
        return section;
    }

    private Map<String, String> buildAiAnalysisByTechName(Long registerId) {
        Map<String, String> map = new HashMap<>();
        for (Map<String, Object> row : physicianMapper.selectExamAnalysisByRegisterId(registerId)) {
            Map<String, Object> indicators = parseJsonMap(row.get("abnormalIndicators"));
            String techName = indicators != null ? stringValue(indicators.get("techName")) : null;
            if (techName == null || techName.isBlank()) {
                continue;
            }
            String report = stringValue(row.get("analysisReport"));
            if (report != null && !report.isBlank()) {
                map.put(techName.trim(), report.trim());
            }
        }
        return map;
    }

    private String extractAiAnalysisText(Map<String, Object> row) {
        String report = stringValue(row.get("analysisReport"));
        return report != null && !report.isBlank() ? report.trim() : null;
    }

    private String summarizeResult(Object resultRaw, String category) {
        if (resultRaw == null) {
            return "";
        }
        String raw = String.valueOf(resultRaw).trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return "";
        }
        return raw.length() > 120 ? raw.substring(0, 120) + "…" : raw;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || !text.startsWith("{")) {
            return null;
        }
        try {
            return (Map<String, Object>) new com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Map.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private Map<String, Object> event(String eventType, Object occurredAt, String title, String summary,
                                      String status, String sourceType, Long sourceId, Map<String, Object> detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("occurredAt", occurredAt);
        event.put("title", title);
        event.put("summary", summary != null ? summary : "");
        event.put("status", status);
        event.put("sourceType", sourceType);
        event.put("sourceId", sourceId);
        if (detail != null) {
            event.put("detail", detail);
        }
        return event;
    }

    private String stateToStatus(String state) {
        if (state == null) {
            return "pending";
        }
        if (state.contains("完成") || state.contains("已归档")) {
            return "completed";
        }
        return "pending";
    }

    private String formatVisitLabel(Map<String, Object> header) {
        Object visitDate = header.get("visitDate");
        String date = visitDate instanceof LocalDateTime ldt
            ? ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            : String.valueOf(visitDate);
        return date + " " + nullSafe(header.get("departmentName"));
    }

    private LocalDateTime parseTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private boolean hasAnyText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (hasText(map.get(key))) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object nullSafe(Object value) {
        return value == null ? "" : value;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
