package com.xikang.physician.ai;

import com.xikang.physician.mapper.PhysicianMapper;
import com.xikang.physician.service.PhysicianService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PhysicianAiPipelineService {

    private final PhysicianMapper physicianMapper;
    private final PhysicianService physicianService;
    private final DifyWorkflowClient difyClient;
    private final DifyAiProperties difyProperties;
    private final DifyPreliminaryOutputMapper preliminaryOutputMapper;
    private final FallbackWorkflowEngine fallbackEngine;
    private final CtInferenceService ctInferenceService;

    public PhysicianAiPipelineService(
        PhysicianMapper physicianMapper,
        @Lazy PhysicianService physicianService,
        DifyWorkflowClient difyClient,
        DifyAiProperties difyProperties,
        DifyPreliminaryOutputMapper preliminaryOutputMapper,
        FallbackWorkflowEngine fallbackEngine,
        CtInferenceService ctInferenceService
    ) {
        this.physicianMapper = physicianMapper;
        this.physicianService = physicianService;
        this.difyClient = difyClient;
        this.difyProperties = difyProperties;
        this.preliminaryOutputMapper = preliminaryOutputMapper;
        this.fallbackEngine = fallbackEngine;
        this.ctInferenceService = ctInferenceService;
    }

    public List<Map<String, Object>> getAvailableExaminations() {
        return physicianMapper.selectAvailableExaminations();
    }

    @Transactional
    public Map<String, Object> runPreliminaryDiagnosis(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        String text = String.valueOf(request.getOrDefault("text", "")).trim();
        if (registerId == null) {
            throw new IllegalArgumentException("registerId 不能为空");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        boolean preHandle = Boolean.TRUE.equals(request.get("preHandle"));
        List<Map<String, Object>> diseaseCatalog = physicianMapper.selectDiseases(null);

        Map<String, Object> fallbackInput = new LinkedHashMap<>();
        fallbackInput.put("registerId", registerId);
        fallbackInput.put("text", text);
        fallbackInput.put("preHandle", preHandle);
        fallbackInput.put("diseaseCatalog", diseaseCatalog);

        Map<String, Object> output;
        if (difyClient.isPreliminaryEnabled()) {
            String model = String.valueOf(request.getOrDefault("model", "")).trim();
            Map<String, Object> difyInputs = new LinkedHashMap<>();
            difyInputs.put("text", text);
            difyInputs.put("preHandle", preHandle);
            if (!model.isBlank()) {
                difyInputs.put("model", model);
            }
            String user = "physician-reg-" + registerId;
            String traceId = "prelim-" + registerId + "-" + System.currentTimeMillis();
            DifyWorkflowRunResult run = difyClient.runWorkflowBlocking(difyInputs, user, traceId);
            output = preliminaryOutputMapper.toPreliminaryResult(run.getOutputs());
            output.put("registerId", registerId);
            output.put("preHandle", preHandle);
            output.put("modelId", "dify-preliminary");
            output.put("llmModel", model.isBlank() ? null : model);
            output.put("workflowRunId", run.getWorkflowRunId());
        } else {
            output = fallbackEngine.runPreliminaryDiagnosis(fallbackInput);
            output.put("modelId", "fallback-preliminary");
        }

        persistPreliminaryAiLog(registerId, output);
        return output;
    }

    @Transactional
    public Map<String, Object> runW1(Map<String, Object> request) {
        Map<String, Object> output = invokeWorkflow(
            difyProperties.getWorkflowW1(),
            request,
            () -> fallbackEngine.runW1(request)
        );
        persistStructuredRecord(output);
        return output;
    }

    @Transactional
    public Map<String, Object> runW2(Long registerId) {
        Map<String, Object> structured = loadStructuredRecord(registerId);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("registerId", registerId);
        input.put("structuredRecord", structured);
        input.put("availableExaminations", getAvailableExaminations());

        Map<String, Object> output = invokeWorkflow(
            difyProperties.getWorkflowW2(),
            input,
            () -> fallbackEngine.runW2(input)
        );
        persistW2(output, registerId);
        return output;
    }

    @Transactional
    public Map<String, Object> runW2b(Long registerId, boolean autoCreateRequests) {
        if (autoCreateRequests) {
            createRequestsFromSuggestions(registerId);
        }

        Map<String, Object> structured = loadStructuredRecord(registerId);
        List<Map<String, Object>> ordered = physicianMapper.selectOpenRequestsForSimulation(registerId);
        List<Map<String, Object>> labExams = new ArrayList<>();
        List<Map<String, Object>> ctExams = new ArrayList<>();

        for (Map<String, Object> exam : ordered) {
            String category = String.valueOf(exam.getOrDefault("category", ""));
            if (category.contains("imaging_ct")) {
                ctExams.add(exam);
            } else {
                labExams.add(exam);
            }
        }

        Map<String, Object> w2bInput = new LinkedHashMap<>();
        w2bInput.put("registerId", registerId);
        w2bInput.put("structuredRecord", structured);
        w2bInput.put("preliminaryAssessment", loadLatestPreliminaryAssessment(registerId));
        w2bInput.put("orderedExaminations", labExams);
        w2bInput.put("simulationProfile", Map.of("severityHint", "mild_infection", "randomSeed", registerId));

        Map<String, Object> w2bOutput = invokeWorkflow(
            difyProperties.getWorkflowW2b(),
            w2bInput,
            () -> fallbackEngine.runW2b(w2bInput)
        );

        List<Map<String, Object>> allSimulated = new ArrayList<>(listOfMaps(w2bOutput.get("simulatedResults")));
        for (Map<String, Object> ctExam : ctExams) {
            Map<String, Object> ctInput = new LinkedHashMap<>();
            ctInput.put("checkRequestId", ctExam.get("requestId"));
            ctInput.put("registerId", registerId);
            ctInput.put("techId", ctExam.get("techId"));
            ctInput.put("examName", ctExam.get("techName"));
            ctInput.put("bodyPart", String.valueOf(ctExam.getOrDefault("category", "")).contains("brain") ? "brain" : "chest");
            ctInput.put("dicomSeriesOrImageUri", "sim://register/" + registerId + "/ct/" + ctExam.get("techId"));
            ctInput.put("structuredRecord", structured);
            ctInput.put("randomSeed", registerId);

            Map<String, Object> ctResult = ctInferenceService.infer(ctInput);
            persistCtResult(ctExam, ctResult);
            allSimulated.add(ctResult);
        }

        for (Map<String, Object> labResult : listOfMaps(w2bOutput.get("simulatedResults"))) {
            persistLabResult(registerId, labResult);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("registerId", registerId);
        response.put("simulatedResults", allSimulated);
        return response;
    }

    @Transactional
    public Map<String, Object> runW3(Long registerId) {
        Map<String, Object> input = buildW3Input(registerId);
        Map<String, Object> output = invokeWorkflow(
            difyProperties.getWorkflowW3(),
            input,
            () -> fallbackEngine.runW3(input)
        );
        persistW3(output, registerId);
        return output;
    }

    @Transactional
    public Map<String, Object> runW4(Long registerId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("registerId", registerId);
        input.put("structuredRecord", loadStructuredRecord(registerId));
        input.put("w3Output", loadW3Output(registerId));
        input.put("diseaseCatalog", physicianMapper.selectDiseases(null));

        Map<String, Object> output = invokeWorkflow(
            difyProperties.getWorkflowW4(),
            input,
            () -> fallbackEngine.runW4(input)
        );
        persistW4(output, registerId);
        return output;
    }

    @Transactional
    public Map<String, Object> runFullPipeline(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        String inputMode = String.valueOf(request.getOrDefault("inputMode", "pre_consultation"));

        Map<String, Object> w1;
        if ("pre_consultation".equals(inputMode)) {
            w1 = buildStructuredFromPreConsultation(registerId);
            if (w1.isEmpty()) {
                w1 = runW1(request);
            } else {
                persistStructuredRecord(w1);
            }
        } else {
            w1 = runW1(request);
        }

        Map<String, Object> w2 = runW2(registerId);
        Map<String, Object> w2b = runW2b(registerId, Boolean.TRUE.equals(request.get("autoCreateRequests")));
        Map<String, Object> w3 = runW3(registerId);
        Map<String, Object> w4 = runW4(registerId);

        return Map.of(
            "registerId", registerId,
            "w1", w1,
            "w2", w2,
            "w2b", w2b,
            "w3", w3,
            "w4", w4
        );
    }

    private Map<String, Object> buildStructuredFromPreConsultation(Long registerId) {
        Map<String, Object> consult = physicianMapper.selectLatestAiConsultation(registerId);
        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (consult == null || consult.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> patientInfo = new LinkedHashMap<>();
        if (register != null) {
            patientInfo.put("realName", register.get("realName"));
            patientInfo.put("gender", register.get("gender"));
            patientInfo.put("age", register.get("age"));
            patientInfo.put("caseNumber", register.get("caseNumber"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registerId", registerId);
        out.put("patientInfo", patientInfo);
        out.put("chiefComplaint", consult.get("chiefComplaint"));
        out.put("symptomDuration", consult.get("symptomDuration"));
        out.put("presentIllness", consult.get("aiSummary"));
        out.put("history", consult.get("historySummary"));
        out.put("allergy", consult.get("allergySummary"));
        out.put("presentTreat", consult.get("medicationSummary"));
        out.put("physique", "");
        out.put("preliminaryImpression", "急性呼吸道感染待排");
        out.put("rawSource", Map.of("inputMode", "pre_consultation"));
        return out;
    }

    private void persistPreliminaryAiLog(Long registerId, Map<String, Object> output) {
        if (registerId == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("diagnosisBasis", output.get("diagnosisBasis"));
        meta.put("knowledgeBaseRecall", output.get("knowledgeBaseRecall"));
        meta.put("isRecalled", output.get("isRecalled"));
        meta.put("confidence", output.get("confidence"));
        meta.put("clinicalSummary", output.get("clinicalSummary"));
        meta.put("primaryDiagnosis", output.get("primaryDiagnosis"));
        meta.put("suggestedDiseases", output.get("suggestedDiseases"));
        meta.put("excludedDiagnoses", output.get("excludedDiagnoses"));
        meta.put("redFlags", output.get("redFlags"));
        meta.put("preHandle", output.get("preHandle"));
        meta.put("workflowRunId", output.get("workflowRunId"));
        meta.put("llmModel", output.get("llmModel"));

        Map<String, Object> log = new HashMap<>();
        log.put("registerId", registerId);
        log.put("sourceType", "preliminary_diagnosis");
        log.put("aiDiagnosis", output.get("diagnosisText"));
        log.put("modelId", output.get("modelId"));
        log.put("doctorModification", JsonMapUtils.toJson(meta));
        physicianMapper.insertAiMedicalRecordLog(log);
    }

    private void persistStructuredRecord(Map<String, Object> structured) {
        Long registerId = toLong(structured.get("registerId"));
        if (registerId == null) {
            return;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("registerId", registerId);
        record.put("readme", structured.get("chiefComplaint"));
        record.put("present", structured.get("presentIllness"));
        record.put("presentTreat", structured.get("presentTreat"));
        record.put("history", structured.get("history"));
        record.put("allergy", structured.get("allergy"));
        record.put("physique", structured.get("physique"));

        Map<String, Object> existing = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (existing == null) {
            physicianMapper.insertMedicalRecord(record);
        } else {
            record.put("id", toLong(existing.get("id")));
            physicianMapper.updateMedicalRecord(record);
        }

        Map<String, Object> log = new HashMap<>();
        log.put("registerId", registerId);
        log.put("sourceType", "dictation");
        log.put("aiReadme", structured.get("chiefComplaint"));
        log.put("aiPresent", structured.get("presentIllness"));
        log.put("aiHistory", structured.get("history"));
        log.put("aiAllergy", structured.get("allergy"));
        log.put("aiPhysique", structured.get("physique"));
        log.put("aiDiagnosis", structured.get("preliminaryImpression"));
        log.put("modelId", difyClient.isReady() ? "dify-w1" : "fallback-w1");
        physicianMapper.insertAiMedicalRecordLog(log);
    }

    private void persistW2(Map<String, Object> output, Long registerId) {
        physicianMapper.deleteExamSuggestionsByRegisterId(registerId);
        for (Map<String, Object> item : listOfMaps(output.get("recommendedExaminations"))) {
            Map<String, Object> row = new HashMap<>();
            row.put("registerId", registerId);
            row.put("techId", item.get("techId"));
            row.put("techName", item.get("techName"));
            row.put("suggestType", item.get("techType"));
            row.put("suggestReason", item.get("reason"));
            row.put("priority", item.getOrDefault("priority", 1));
            row.put("modelId", difyClient.isReady() ? "dify-w2" : "fallback-w2");
            physicianMapper.insertExamSuggestion(row);
        }
    }

    private void createRequestsFromSuggestions(Long registerId) {
        List<Map<String, Object>> suggestions = physicianMapper.selectExamSuggestions(registerId);
        List<Map<String, Object>> checkItems = new ArrayList<>();
        List<Map<String, Object>> inspectionItems = new ArrayList<>();
        for (Map<String, Object> sug : suggestions) {
            Map<String, Object> item = new HashMap<>();
            item.put("medicalTechnologyId", sug.get("techId"));
            String type = String.valueOf(sug.get("suggestType"));
            if ("check".equals(type)) {
                item.put("checkInfo", sug.get("suggestReason"));
                item.put("checkPosition", "");
                item.put("checkRemark", "AI推荐");
                checkItems.add(item);
            } else {
                item.put("inspectionInfo", sug.get("suggestReason"));
                item.put("inspectionPosition", "");
                item.put("inspectionRemark", "AI推荐");
                inspectionItems.add(item);
            }
        }
        if (!checkItems.isEmpty()) {
            physicianService.createCheckRequest(Map.of("registerId", registerId, "items", checkItems));
        }
        if (!inspectionItems.isEmpty()) {
            physicianService.createInspectionRequest(Map.of("registerId", registerId, "items", inspectionItems));
        }
    }

    private void persistLabResult(Long registerId, Map<String, Object> labResult) {
        Long techId = toLong(labResult.get("techId"));
        String techType = String.valueOf(labResult.get("techType"));
        String resultText = String.valueOf(labResult.get("resultText"));
        if ("check".equals(techType)) {
            physicianMapper.updateCheckRequestResult(registerId, techId, resultText, "已完成");
        } else {
            physicianMapper.updateInspectionRequestResult(registerId, techId, resultText, "已完成");
        }
    }

    private void persistCtResult(Map<String, Object> ctExam, Map<String, Object> ctResult) {
        Long registerId = toLong(ctExam.get("registerId"));
        Long techId = toLong(ctExam.get("techId"));
        String resultText = String.valueOf(ctResult.get("resultText"));
        physicianMapper.updateCheckRequestResult(registerId, techId, resultText, "已完成");
    }

    private void persistW3(Map<String, Object> output, Long registerId) {
        physicianMapper.deleteExamAnalysisByRegisterId(registerId);
        for (Map<String, Object> summary : listOfMaps(output.get("examSummaries"))) {
            Map<String, Object> row = new HashMap<>();
            row.put("registerId", registerId);
            row.put("analysisType", resolveAnalysisType(summary));
            row.put("originalResult", String.join("；", listOfStrings(summary.get("keyFindings"))));
            row.put("abnormalIndicators", JsonMapUtils.toJson(summary.get("keyFindings")));
            row.put("riskLevel", summary.getOrDefault("riskLevel", "normal"));
            row.put("analysisReport", summary.get("interpretation"));
            row.put("correlationAnalysis", output.get("overallAnalysis"));
            row.put("modelId", difyClient.isReady() ? "dify-w3" : "fallback-w3");
            physicianMapper.insertExamAnalysis(row);
        }
    }

    private void persistW4(Map<String, Object> output, Long registerId) {
        physicianMapper.deleteDiagnosisSuggestionsByRegisterId(registerId);
        Map<String, Object> primary = JsonMapUtils.asMap(output.get("primaryDiagnosis"));
        primary.put("treatmentDirection", output.get("clinicalAdvice"));
        insertDiagnosisSuggestion(registerId, primary, 1);

        int order = 2;
        for (Map<String, Object> diff : listOfMaps(output.get("differentialDiagnoses"))) {
            insertDiagnosisSuggestion(registerId, diff, order++);
        }

        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (record != null) {
            Map<String, Object> dx = new HashMap<>();
            dx.put("medicalRecordId", toLong(record.get("id")));
            dx.put("diagnosis", primary.get("diseaseName"));
            dx.put("cure", output.get("clinicalAdvice"));
            dx.put("careful", "AI辅助诊断，请医生确认。");
            physicianMapper.updateDiagnosis(dx);
        }
    }

    private void insertDiagnosisSuggestion(Long registerId, Map<String, Object> item, int sortOrder) {
        Map<String, Object> row = new HashMap<>();
        row.put("registerId", registerId);
        row.put("diseaseId", item.get("diseaseId"));
        row.put("diseaseName", item.get("diseaseName"));
        row.put("recommendIcd", item.get("recommendIcd"));
        row.put("probability", item.get("probability"));
        row.put("riskLevel", "low");
        row.put("treatmentDirection", item.getOrDefault("treatmentDirection", item.get("clinicalAdvice")));
        row.put("diagnosisBasis", item.get("diagnosisBasis"));
        row.put("sortOrder", sortOrder);
        row.put("modelId", difyClient.isReady() ? "dify-w4" : "fallback-w4");
        physicianMapper.insertDiagnosisSuggestion(row);
    }

    private Map<String, Object> buildW3Input(Long registerId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("registerId", registerId);
        input.put("structuredRecord", loadStructuredRecord(registerId));
        input.put("preliminaryAssessment", loadLatestPreliminaryAssessment(registerId));
        input.put("allResults", buildAllResults(registerId));
        return input;
    }

    private List<Map<String, Object>> buildAllResults(Long registerId) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> row : physicianMapper.selectCheckResults(registerId)) {
            if (row.get("checkResult") == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("techId", row.get("id"));
            item.put("techName", row.get("techName"));
            item.put("techType", "check");
            item.put("resultText", row.get("checkResult"));
            all.add(item);
        }
        for (Map<String, Object> row : physicianMapper.selectInspectionResults(registerId)) {
            if (row.get("inspectionResult") == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("techId", row.get("id"));
            item.put("techName", row.get("techName"));
            item.put("techType", "inspection");
            item.put("resultText", row.get("inspectionResult"));
            all.add(item);
        }
        return all;
    }

    private Map<String, Object> loadW3Output(Long registerId) {
        List<Map<String, Object>> analyses = physicianMapper.selectExamAnalysisByRegisterId(registerId);
        List<Map<String, Object>> summaries = new ArrayList<>();
        StringBuilder overall = new StringBuilder();
        for (Map<String, Object> row : analyses) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("techName", row.get("analysisType"));
            summary.put("keyFindings", listOfStrings(row.get("abnormalIndicators")));
            summary.put("interpretation", row.get("analysisReport"));
            summary.put("riskLevel", row.get("riskLevel"));
            summaries.add(summary);
            if (row.get("correlationAnalysis") != null) {
                overall.append(row.get("correlationAnalysis"));
            }
        }
        return Map.of(
            "examSummaries", summaries,
            "overallAnalysis", overall.length() > 0 ? overall.toString() : "暂无分析"
        );
    }

    private Map<String, Object> loadStructuredRecord(Long registerId) {
        Map<String, Object> fromPre = buildStructuredFromPreConsultation(registerId);
        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (record != null && record.get("readme") != null) {
            Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
            Map<String, Object> out = new LinkedHashMap<>(fromPre.isEmpty() ? Map.of() : fromPre);
            out.put("registerId", registerId);
            if (register != null) {
                out.put("patientInfo", Map.of(
                    "realName", register.get("realName"),
                    "gender", register.get("gender"),
                    "age", register.get("age"),
                    "caseNumber", register.get("caseNumber")
                ));
            }
            out.put("chiefComplaint", record.get("readme"));
            out.put("presentIllness", record.get("present"));
            out.put("history", record.get("history"));
            out.put("allergy", record.get("allergy"));
            out.put("physique", record.get("physique"));
            out.put("preliminaryImpression", record.get("preliminaryDiagnosis"));
            return out;
        }
        if (!fromPre.isEmpty()) {
            return fromPre;
        }
        return Map.of("registerId", registerId);
    }

    private String loadLatestPreliminaryAssessment(Long registerId) {
        List<Map<String, Object>> suggestions = physicianMapper.selectExamSuggestions(registerId);
        if (suggestions.isEmpty()) {
            return "";
        }
        return String.valueOf(suggestions.get(0).getOrDefault("suggestReason", ""));
    }

    private Map<String, Object> invokeWorkflow(
        String workflowId,
        Map<String, Object> inputs,
        java.util.function.Supplier<Map<String, Object>> fallback
    ) {
        if (difyClient.isReady() && workflowId != null && !workflowId.isBlank()) {
            Map<String, Object> remote = difyClient.runWorkflow(workflowId, inputs);
            if (!remote.isEmpty()) {
                return remote;
            }
        }
        return fallback.get();
    }

    private static String resolveAnalysisType(Map<String, Object> summary) {
        String name = String.valueOf(summary.getOrDefault("techName", ""));
        return name.contains("血") || name.contains("CRP") ? "inspection" : "check";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static List<String> listOfStrings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        if (value instanceof String text && text.startsWith("[")) {
            return List.of(text);
        }
        return List.of();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }
}
