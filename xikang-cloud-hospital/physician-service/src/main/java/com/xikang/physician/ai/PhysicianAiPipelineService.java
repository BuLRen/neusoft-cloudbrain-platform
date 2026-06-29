package com.xikang.physician.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.physician.mapper.PhysicianMapper;
import com.xikang.physician.service.PhysicianService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PhysicianAiPipelineService {

    private static final Logger log = LoggerFactory.getLogger(PhysicianAiPipelineService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** W2 返回的 preliminaryAssessment，按 registerId 缓存（进程内，重启后回退到病历/推荐理由）。 */
    private final Map<Long, String> w2PreliminaryAssessmentByRegister = new ConcurrentHashMap<>();

    private final PhysicianMapper physicianMapper;
    private final PhysicianService physicianService;
    private final DifyWorkflowClient difyClient;
    private final DifyAiProperties difyProperties;
    private final DifyPreliminaryOutputMapper preliminaryOutputMapper;
    private final DifyW2OutputMapper w2OutputMapper;
    private final W2ClinicalContextBuilder w2ClinicalContextBuilder;
    private final W2RecommendNormalizer w2RecommendNormalizer;
    private final W3AnalyzeNormalizer w3AnalyzeNormalizer;
    private final W3DifyInputBuilder w3DifyInputBuilder;
    private final DifyW3OutputMapper w3OutputMapper;
    private final W4DifyInputBuilder w4DifyInputBuilder;
    private final DifyW4OutputMapper w4OutputMapper;
    private final W5DifyInputBuilder w5DifyInputBuilder;
    private final DifyW5OutputMapper w5OutputMapper;
    private final FallbackWorkflowEngine fallbackEngine;
    private final CtInferenceService ctInferenceService;

    public PhysicianAiPipelineService(
        PhysicianMapper physicianMapper,
        @Lazy PhysicianService physicianService,
        DifyWorkflowClient difyClient,
        DifyAiProperties difyProperties,
        DifyPreliminaryOutputMapper preliminaryOutputMapper,
        DifyW2OutputMapper w2OutputMapper,
        W2ClinicalContextBuilder w2ClinicalContextBuilder,
        W2RecommendNormalizer w2RecommendNormalizer,
        W3AnalyzeNormalizer w3AnalyzeNormalizer,
        W3DifyInputBuilder w3DifyInputBuilder,
        DifyW3OutputMapper w3OutputMapper,
        W4DifyInputBuilder w4DifyInputBuilder,
        DifyW4OutputMapper w4OutputMapper,
        W5DifyInputBuilder w5DifyInputBuilder,
        DifyW5OutputMapper w5OutputMapper,
        FallbackWorkflowEngine fallbackEngine,
        CtInferenceService ctInferenceService
    ) {
        this.physicianMapper = physicianMapper;
        this.physicianService = physicianService;
        this.difyClient = difyClient;
        this.difyProperties = difyProperties;
        this.preliminaryOutputMapper = preliminaryOutputMapper;
        this.w2OutputMapper = w2OutputMapper;
        this.w2ClinicalContextBuilder = w2ClinicalContextBuilder;
        this.w2RecommendNormalizer = w2RecommendNormalizer;
        this.w3AnalyzeNormalizer = w3AnalyzeNormalizer;
        this.w3DifyInputBuilder = w3DifyInputBuilder;
        this.w3OutputMapper = w3OutputMapper;
        this.w4DifyInputBuilder = w4DifyInputBuilder;
        this.w4OutputMapper = w4OutputMapper;
        this.w5DifyInputBuilder = w5DifyInputBuilder;
        this.w5OutputMapper = w5OutputMapper;
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
            log.info("初步诊断 registerId={} 调用 Dify（base-url-preliminary）", registerId);
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
            log.warn(
                "初步诊断 registerId={} 未启用 Dify，走内置 Fallback（请检查 DIFY_WORKFLOW_PRELIMINARY 与 DIFY_API_KEY_PRELIMINARY）",
                registerId
            );
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
        if (registerId == null) {
            throw new IllegalArgumentException("registerId 不能为空");
        }

        List<Map<String, Object>> availableExaminations = getAvailableExaminations();
        Map<String, Object> output;

        if (difyClient.isW2Enabled()) {
            log.info("W2 registerId={} using Dify workflow (api-key-w2)", registerId);
            output = runW2ViaDify(registerId, availableExaminations);
        } else {
            log.warn(
                "W2 registerId={} using fallback (difyEnabled={}, w2Switch={}, w2ApiKeyConfigured={})",
                registerId,
                difyProperties.isDifyBaseConfigured(),
                difyProperties.isW2WorkflowSwitchOn(),
                !difyProperties.resolveW2ApiKey().isBlank()
            );
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("registerId", registerId);
            input.put("structuredRecord", loadStructuredRecord(registerId));
            input.put("availableExaminations", availableExaminations);
            output = fallbackEngine.runW2(input);
            output.put("modelId", "fallback-w2");
        }

        persistW2(output, registerId);
        return output;
    }

    private Map<String, Object> runW2ViaDify(Long registerId, List<Map<String, Object>> availableExaminations) {
        try {
            Map<String, Object> clinicalContext = w2ClinicalContextBuilder.build(registerId);
            Map<String, Object> difyInputs = new LinkedHashMap<>();
            difyInputs.put("clinical_context_json", JSON.writeValueAsString(clinicalContext));
            difyInputs.put("available_examinations_json", JSON.writeValueAsString(availableExaminations));

            String user = "physician-reg-" + registerId;
            String traceId = "w2-" + registerId + "-" + System.currentTimeMillis();
            DifyWorkflowRunResult run = difyClient.runWorkflowBlockingWithApiKey(
                difyProperties.resolveW2ApiKey(),
                difyInputs,
                user,
                traceId
            );

            Map<String, Object> mapped = w2OutputMapper.toW2Result(run.getOutputs());
            Map<String, Object> normalized = w2RecommendNormalizer.normalize(mapped, availableExaminations);
            normalized.put("registerId", registerId);
            normalized.put("modelId", "dify-w2");
            normalized.put("workflowRunId", run.getWorkflowRunId());
            log.info("W2 registerId={} Dify completed runId={} recommendations={}",
                registerId,
                run.getWorkflowRunId(),
                listOfMaps(normalized.get("recommendedExaminations")).size());
            return normalized;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("W2 registerId={} Dify call failed: {}", registerId, ex.toString());
            throw new DifyWorkflowException("W2 检查推荐工作流调用失败，请稍后重试");
        }
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
        if (registerId == null) {
            throw new IllegalArgumentException("registerId 不能为空");
        }

        Map<String, Object> rawOutput;
        if (difyClient.isW3Enabled()) {
            log.info("W3 registerId={} using Dify workflow (api-key-w3)", registerId);
            rawOutput = runW3ViaDify(registerId);
        } else {
            log.warn(
                "W3 registerId={} using fallback (difyEnabled={}, w3Switch={}, w3ApiKeyConfigured={})",
                registerId,
                difyProperties.isDifyBaseConfigured(),
                difyProperties.isW3WorkflowSwitchOn(),
                !difyProperties.resolveW3ApiKey().isBlank()
            );
            Map<String, Object> input = buildW3Input(registerId);
            rawOutput = fallbackEngine.runW3(input);
            rawOutput.put("modelId", "fallback-w3");
        }

        Map<String, Object> output = w3AnalyzeNormalizer.normalize(rawOutput, registerId);
        persistW3(output, registerId, rawOutput.get("modelId"));
        return output;
    }

    private Map<String, Object> runW3ViaDify(Long registerId) {
        try {
            Map<String, Object> structuredRecord = loadStructuredRecord(registerId);
            List<Map<String, Object>> allResults = buildAllResults(registerId);
            String preliminaryAssessment = loadLatestPreliminaryAssessment(registerId);

            Map<String, Object> difyInputs = w3DifyInputBuilder.build(
                registerId,
                structuredRecord,
                allResults,
                preliminaryAssessment
            );

            String user = "physician-reg-" + registerId;
            String traceId = "w3-" + registerId + "-" + System.currentTimeMillis();
            DifyWorkflowRunResult run = difyClient.runWorkflowBlockingWithApiKey(
                difyProperties.resolveW3ApiKey(),
                difyInputs,
                user,
                traceId
            );

            Map<String, Object> mapped = w3OutputMapper.toW3Result(run.getOutputs());
            mapped.put("modelId", "dify-w3");
            mapped.put("workflowRunId", run.getWorkflowRunId());
            log.info(
                "W3 registerId={} Dify completed runId={} summaries={}",
                registerId,
                run.getWorkflowRunId(),
                listOfMaps(mapped.get("examSummaries")).size()
            );
            return mapped;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("W3 registerId={} Dify call failed: {}", registerId, ex.toString());
            throw new DifyWorkflowException("W3 结果解读工作流调用失败，请稍后重试");
        }
    }

    public Map<String, Object> getW3Status(Long registerId) {
        List<Map<String, Object>> analyses = physicianMapper.selectExamAnalysisByRegisterId(registerId);
        Map<String, Object> w3Output = loadW3Output(registerId);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("registerId", registerId);
        status.put("completed", !analyses.isEmpty());
        status.put("examSummaryCount", listOfMaps(w3Output.get("examSummaries")).size());
        status.put("clinicalImpression", w3Output.get("clinicalImpression"));
        status.put("overallAnalysis", w3Output.get("overallAnalysis"));
        status.put("explicitNonDiagnosis", true);
        status.put("w3Output", w3Output);
        return status;
    }

    @Transactional
    public Map<String, Object> runW4(Long registerId) {
        Map<String, Object> rawOutput;
        if (difyClient.isW4Enabled()) {
            log.info("W4 registerId={} using Dify workflow (api-key-w4)", registerId);
            rawOutput = runW4ViaDify(registerId);
        } else {
            log.warn(
                "W4 registerId={} using fallback (difyEnabled={}, w4Switch={}, w4ApiKeyConfigured={})",
                registerId,
                difyProperties.isDifyBaseConfigured(),
                difyProperties.isW4WorkflowSwitchOn(),
                !difyProperties.resolveW4ApiKey().isBlank()
            );
            rawOutput = adaptFallbackW4(fallbackEngine.runW4(buildW4FallbackInput(registerId)));
            rawOutput.put("modelId", "fallback-w4");
        }
        persistW4(rawOutput, registerId);
        return rawOutput;
    }

    public List<Map<String, Object>> getDrugSuggestions(Long registerId) {
        return enrichW5SuggestionsWithLiveStock(physicianMapper.selectDrugSuggestions(registerId));
    }

    private List<Map<String, Object>> enrichW5SuggestionsWithLiveStock(List<Map<String, Object>> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> item : suggestions) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            Long drugId = toLong(copy.get("drugId"));
            if (drugId == null) {
                enriched.add(copy);
                continue;
            }
            Map<String, Object> drug = physicianMapper.selectDrugById(drugId);
            if (drug == null) {
                copy.put("stockQuantity", 0);
                copy.put("drugUnit", "盒");
                copy.put("lowStockThreshold", 20);
                enriched.add(copy);
                continue;
            }
            copy.put("stockQuantity", toInt(drug.get("stockQuantity")));
            copy.put("drugUnit", String.valueOf(drug.getOrDefault("drugUnit", "盒")));
            copy.put("lowStockThreshold", toInt(drug.get("lowStockThreshold")) <= 0 ? 20 : toInt(drug.get("lowStockThreshold")));
            enriched.add(copy);
        }
        return enriched;
    }

    @Transactional
    public Map<String, Object> runW5(Long registerId) {
        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        String diagnosis = record == null ? "" : String.valueOf(record.getOrDefault("diagnosis", "")).trim();
        if (diagnosis.isEmpty()) {
            throw new IllegalArgumentException("请先完成门诊确诊并保存确诊病名");
        }

        Map<String, Object> rawOutput;
        if (difyClient.isW5Enabled()) {
            log.info("W5 registerId={} using Dify workflow (api-key-w5)", registerId);
            rawOutput = runW5ViaDify(registerId);
        } else {
            log.warn(
                "W5 registerId={} using fallback (w5Switch={}, w5ApiKeyConfigured={})",
                registerId,
                difyProperties.isW5WorkflowSwitchOn(),
                !difyProperties.resolveW5ApiKey().isBlank()
            );
            rawOutput = adaptFallbackW5(fallbackEngine.runW5(buildW5FallbackInput(registerId)));
            rawOutput.put("modelId", "fallback-w5");
        }
        sanitizeW5Suggestions(rawOutput);
        persistW5(rawOutput, registerId);
        return rawOutput;
    }

    public void markDrugSuggestionAdopted(Long suggestionId) {
        if (suggestionId == null) {
            return;
        }
        physicianMapper.updateDrugSuggestionAdopted(suggestionId, 1);
    }

    private Map<String, Object> runW5ViaDify(Long registerId) {
        try {
            Map<String, Object> difyInputs = w5DifyInputBuilder.build(registerId);
            String user = "physician-reg-" + registerId;
            String traceId = "w5-" + registerId + "-" + System.currentTimeMillis();
            DifyWorkflowRunResult run = difyClient.runWorkflowBlockingWithApiKey(
                difyProperties.resolveW5ApiKey(),
                difyInputs,
                user,
                traceId
            );

            Map<String, Object> mapped = w5OutputMapper.toW5Result(run.getOutputs());
            if (mapped.get("registerId") == null) {
                mapped.put("registerId", registerId);
            }
            mapped.put("modelId", "dify-w5");
            mapped.put("workflowRunId", run.getWorkflowRunId());
            log.info(
                "W5 registerId={} Dify completed runId={} status={} suggestions={}",
                registerId,
                run.getWorkflowRunId(),
                mapped.get("status"),
                listOfMaps(mapped.get("suggestions")).size()
            );
            return mapped;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("W5 registerId={} Dify call failed: {}", registerId, ex.toString());
            throw new DifyWorkflowException("W5 智能荐药工作流调用失败，请稍后重试");
        }
    }

    private Map<String, Object> buildW5FallbackInput(Long registerId) {
        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("registerId", registerId);
        input.put("confirmedDiagnosis", record == null ? "" : record.get("diagnosis"));
        input.put("allergyHistory", record == null ? "" : record.get("allergy"));
        input.put("drugCatalog", physicianMapper.selectDrugs(null));
        return input;
    }

    private Map<String, Object> adaptFallbackW5(Map<String, Object> fallbackOutput) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", fallbackOutput.getOrDefault("status", "success"));
        out.put("registerId", fallbackOutput.get("registerId"));
        out.put("suggestions", listOfMaps(fallbackOutput.get("suggestions")));
        out.put("fallbackSuggestions", listOfMaps(fallbackOutput.get("fallbackSuggestions")));
        out.put("clinicalSummaryForDoctor", fallbackOutput.get("clinicalSummaryForDoctor"));
        out.put("allergyWarnings", fallbackOutput.get("allergyWarnings"));
        out.put("searchAdvice", fallbackOutput.getOrDefault("searchAdvice", ""));
        return out;
    }

    private void persistW5(Map<String, Object> output, Long registerId) {
        String status = String.valueOf(output.getOrDefault("status", "success")).trim().toLowerCase();
        if ("fallback".equals(status)) {
            log.info("W5 registerId={} status=fallback, skip ai_drug_suggestion persist", registerId);
            return;
        }

        List<Map<String, Object>> suggestions = listOfMaps(output.get("suggestions"));
        if (suggestions.isEmpty()) {
            if ("success".equals(status)) {
                output.put("status", "fallback");
                output.put("searchAdvice", "候选药品均无可用库存，请手动搜索选药");
            }
            return;
        }

        physicianMapper.deleteDrugSuggestionsByRegisterId(registerId);
        String modelId = String.valueOf(output.getOrDefault("modelId", difyClient.isW5Enabled() ? "dify-w5" : "fallback-w5"));
        int order = 1;
        for (Map<String, Object> item : suggestions) {
            insertDrugSuggestion(registerId, item, order++, modelId);
        }
    }

    private void sanitizeW5Suggestions(Map<String, Object> output) {
        if (output == null) {
            return;
        }
        String status = String.valueOf(output.getOrDefault("status", "success")).trim().toLowerCase();
        if ("fallback".equals(status)) {
            return;
        }

        List<Map<String, Object>> suggestions = listOfMaps(output.get("suggestions"));
        if (suggestions.isEmpty()) {
            return;
        }

        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Map<String, Object> item : suggestions) {
            Map<String, Object> normalized = normalizeDrugSuggestionForStock(item);
            if (normalized != null) {
                cleaned.add(normalized);
            }
        }

        output.put("suggestions", cleaned);
        if (cleaned.isEmpty()) {
            output.put("status", "fallback");
            output.put("fallbackSuggestions", List.of());
            output.put("searchAdvice", "候选药品均无可用库存，请手动搜索选药");
            output.put(
                "clinicalSummaryForDoctor",
                String.valueOf(output.getOrDefault("clinicalSummaryForDoctor", ""))
                    + "（系统已过滤无库存药品）"
            );
        }
    }

    private Map<String, Object> normalizeDrugSuggestionForStock(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        Long drugId = toLong(item.get("drugId"));
        if (drugId == null) {
            return null;
        }

        Map<String, Object> drug = physicianMapper.selectDrugById(drugId);
        if (drug == null) {
            return null;
        }

        int stock = toInt(drug.get("stockQuantity"));
        if (stock <= 0) {
            log.info("W5 skip drugId={} name={} due to zero stock", drugId, drug.get("drugName"));
            return null;
        }

        Map<String, Object> normalized = new LinkedHashMap<>(item);
        int requestedQty = toInt(item.getOrDefault("recommendQuantity", item.get("recommend_quantity")));
        if (requestedQty < 1) {
            requestedQty = 1;
        }
        normalized.put("recommendQuantity", Math.min(requestedQty, stock));

        int lowStockThreshold = toInt(drug.get("lowStockThreshold"));
        if (lowStockThreshold <= 0) {
            lowStockThreshold = 20;
        }
        String unit = String.valueOf(drug.getOrDefault("drugUnit", "盒"));
        normalized.put("stockQuantity", stock);
        normalized.put("drugUnit", unit);
        normalized.put("lowStockThreshold", lowStockThreshold);
        if (stock <= lowStockThreshold) {
            String caution = String.valueOf(normalized.getOrDefault("cautionNotes", "")).trim();
            String stockNote = "库存紧张（当前可用 " + stock + " "
                + String.valueOf(drug.getOrDefault("drugUnit", "盒")) + "）";
            normalized.put("cautionNotes", caution.isEmpty() ? stockNote : caution + "；" + stockNote);
        }
        return normalized;
    }

    private void insertDrugSuggestion(Long registerId, Map<String, Object> item, int sortOrder, String modelId) {
        Map<String, Object> row = new HashMap<>();
        row.put("registerId", registerId);
        row.put("drugId", item.get("drugId"));
        row.put("drugName", item.getOrDefault("drugName", ""));
        row.put("recommendUsage", item.getOrDefault("recommendUsage", item.get("recommend_usage")));
        row.put("recommendQuantity", item.getOrDefault("recommendQuantity", item.get("recommend_quantity")));
        row.put("confidence", item.get("confidence"));
        row.put("recommendationBasis", item.getOrDefault("recommendationBasis", item.get("recommendation_basis")));
        row.put("cautionNotes", item.getOrDefault("cautionNotes", item.get("caution_notes")));
        Object itemSort = item.get("sortOrder");
        row.put("sortOrder", itemSort == null ? sortOrder : itemSort);
        row.put("modelId", modelId);
        physicianMapper.insertDrugSuggestion(row);
    }

    private Map<String, Object> runW4ViaDify(Long registerId) {
        try {
            Map<String, Object> difyInputs = w4DifyInputBuilder.build(registerId);
            String user = "physician-reg-" + registerId;
            String traceId = "w4-" + registerId + "-" + System.currentTimeMillis();
            DifyWorkflowRunResult run = difyClient.runWorkflowBlockingWithApiKey(
                difyProperties.resolveW4ApiKey(),
                difyInputs,
                user,
                traceId
            );

            Map<String, Object> mapped = w4OutputMapper.toW4Result(run.getOutputs());
            if (mapped.get("registerId") == null) {
                mapped.put("registerId", registerId);
            }
            mapped.put("modelId", "dify-w4");
            mapped.put("workflowRunId", run.getWorkflowRunId());
            log.info(
                "W4 registerId={} Dify completed runId={} status={} suggestions={}",
                registerId,
                run.getWorkflowRunId(),
                mapped.get("status"),
                listOfMaps(mapped.get("suggestions")).size()
            );
            return mapped;
        } catch (DifyWorkflowException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("W4 registerId={} Dify call failed: {}", registerId, ex.toString());
            throw new DifyWorkflowException("W4 诊断推理工作流调用失败，请稍后重试");
        }
    }

    private Map<String, Object> buildW4FallbackInput(Long registerId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("registerId", registerId);
        input.put("structuredRecord", loadStructuredRecord(registerId));
        input.put("w3Output", loadW3Output(registerId));
        input.put("diseaseCatalog", physicianMapper.selectDiseases(null));
        return input;
    }

    private Map<String, Object> adaptFallbackW4(Map<String, Object> fallbackOutput) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "success");
        out.put("registerId", fallbackOutput.get("registerId"));

        List<Map<String, Object>> suggestions = new ArrayList<>();
        Map<String, Object> primary = JsonMapUtils.asMap(fallbackOutput.get("primaryDiagnosis"));
        if (!primary.isEmpty()) {
            Map<String, Object> primarySuggestion = new LinkedHashMap<>(primary);
            primarySuggestion.put("diagnosisName", primary.getOrDefault("diseaseName", primary.get("diagnosisName")));
            primarySuggestion.put("treatmentDirection", fallbackOutput.get("clinicalAdvice"));
            primarySuggestion.put("sortOrder", 1);
            suggestions.add(primarySuggestion);
        }
        int order = 2;
        for (Map<String, Object> diff : listOfMaps(fallbackOutput.get("differentialDiagnoses"))) {
            Map<String, Object> item = new LinkedHashMap<>(diff);
            item.put("diagnosisName", diff.getOrDefault("diseaseName", diff.get("diagnosisName")));
            item.put("sortOrder", order++);
            suggestions.add(item);
        }

        out.put("suggestions", suggestions);
        out.put("fallbackSuggestions", List.of());
        out.put("clinicalSummaryForDoctor", fallbackOutput.get("clinicalAdvice"));
        out.put("differentialDiagnosis", listOfMaps(fallbackOutput.get("differentialDiagnoses")).stream()
            .map(diff -> Map.<String, Object>of(
                "diagnosisName", diff.getOrDefault("diseaseName", diff.get("diagnosisName")),
                "reason", diff.getOrDefault("diagnosisBasis", "")
            ))
            .toList());
        out.put("warningSigns", List.of());
        out.put("searchAdvice", "");
        return out;
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
        String preliminary = String.valueOf(output.getOrDefault("preliminaryAssessment", "")).trim();
        if (!preliminary.isEmpty()) {
            w2PreliminaryAssessmentByRegister.put(registerId, preliminary);
        }
        physicianMapper.deleteExamSuggestionsByRegisterId(registerId);
        String modelId = String.valueOf(output.getOrDefault("modelId", resolveW2ModelIdLabel()));
        for (Map<String, Object> item : listOfMaps(output.get("recommendedExaminations"))) {
            Map<String, Object> row = new HashMap<>();
            row.put("registerId", registerId);
            row.put("techId", item.get("techId"));
            row.put("techName", item.get("techName"));
            row.put("suggestType", item.get("techType"));
            row.put("suggestReason", item.get("reason"));
            row.put("priority", item.getOrDefault("priority", 1));
            row.put("modelId", modelId);
            physicianMapper.insertExamSuggestion(row);
        }
    }

    private String resolveW2ModelIdLabel() {
        return difyClient.isW2Enabled() ? "dify-w2" : "fallback-w2";
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

    private void persistW3(Map<String, Object> output, Long registerId, Object modelIdHint) {
        physicianMapper.deleteExamAnalysisByRegisterId(registerId);
        String modelId = modelIdHint != null
            ? String.valueOf(modelIdHint)
            : (difyClient.isW3Enabled() ? "dify-w3" : "fallback-w3");
        Map<String, String> techTypeByName = buildTechTypeByName(registerId);
        Map<String, Long> checkRequestIdByTechName = buildCheckRequestIdByTechName(registerId);
        Map<String, Long> inspectionRequestIdByTechName = buildInspectionRequestIdByTechName(registerId);
        String globalClinicalImpression = String.valueOf(output.getOrDefault("clinicalImpression", "")).trim();
        String overallAnalysis = String.valueOf(output.getOrDefault("overallAnalysis", "")).trim();

        List<Map<String, Object>> summaries = listOfMaps(output.get("examSummaries"));
        if (summaries.isEmpty()) {
            if (!overallAnalysis.isEmpty()) {
                Map<String, Object> row = new HashMap<>();
                row.put("registerId", registerId);
                row.put("analysisType", "check");
                row.put("originalResult", "");
                row.put(
                    "abnormalIndicators",
                    buildW3AbnormalIndicatorsJson(
                        "综合解读",
                        "",
                        "",
                        List.of(),
                        List.of(),
                        globalClinicalImpression,
                        overallAnalysis
                    )
                );
                row.put("riskLevel", "normal");
                row.put("analysisReport", overallAnalysis);
                row.put("correlationAnalysis", overallAnalysis);
                row.put("modelId", modelId);
                physicianMapper.insertExamAnalysis(row);
            }
            return;
        }

        for (int index = 0; index < summaries.size(); index++) {
            Map<String, Object> summary = summaries.get(index);
            String techName = String.valueOf(summary.getOrDefault("techName", "")).trim();
            String techType = String.valueOf(summary.getOrDefault("techType", "")).trim();
            String clinicalImpression = String.valueOf(summary.getOrDefault("clinicalImpression", "")).trim();
            List<String> keyFindings = listOfStrings(summary.get("keyFindings"));
            List<Map<String, Object>> indicatorRows = listOfMaps(summary.get("indicatorRows"));
            String dbAnalysisType = resolveDbAnalysisType(summary, techTypeByName);
            boolean isFirst = index == 0;

            Map<String, Object> row = new HashMap<>();
            row.put("registerId", registerId);
            if ("check".equals(dbAnalysisType) && !techName.isEmpty()) {
                row.put("checkRequestId", checkRequestIdByTechName.get(techName));
            } else if ("inspection".equals(dbAnalysisType) && !techName.isEmpty()) {
                row.put("inspectionRequestId", inspectionRequestIdByTechName.get(techName));
            }
            row.put("analysisType", dbAnalysisType);
            row.put("originalResult", String.join("；", keyFindings));
            row.put(
                "abnormalIndicators",
                buildW3AbnormalIndicatorsJson(
                    techName,
                    techType,
                    clinicalImpression,
                    keyFindings,
                    indicatorRows,
                    isFirst ? globalClinicalImpression : "",
                    isFirst ? overallAnalysis : ""
                )
            );
            row.put("riskLevel", normalizeRiskLevelForDb(summary.getOrDefault("riskLevel", "normal")));
            row.put("analysisReport", summary.get("interpretation"));
            row.put("correlationAnalysis", isFirst ? overallAnalysis : null);
            row.put("modelId", modelId);
            physicianMapper.insertExamAnalysis(row);
        }
    }

    private void persistW4(Map<String, Object> output, Long registerId) {
        String status = String.valueOf(output.getOrDefault("status", "success")).trim().toLowerCase();
        if ("fallback".equals(status)) {
            log.info("W4 registerId={} status=fallback, skip ai_diagnosis_suggestion persist", registerId);
            return;
        }

        List<Map<String, Object>> suggestions = listOfMaps(output.get("suggestions"));
        if (suggestions.isEmpty() && "empty".equals(status)) {
            physicianMapper.deleteDiagnosisSuggestionsByRegisterId(registerId);
            return;
        }
        if (suggestions.isEmpty()) {
            return;
        }

        physicianMapper.deleteDiagnosisSuggestionsByRegisterId(registerId);
        String modelId = String.valueOf(output.getOrDefault("modelId", difyClient.isW4Enabled() ? "dify-w4" : "fallback-w4"));
        int order = 1;
        for (Map<String, Object> item : suggestions) {
            insertDiagnosisSuggestion(registerId, item, order++, modelId);
        }
    }

    private void insertDiagnosisSuggestion(Long registerId, Map<String, Object> item, int sortOrder, String modelId) {
        Map<String, Object> row = new HashMap<>();
        row.put("registerId", registerId);
        row.put("diseaseId", item.get("diseaseId"));
        String diseaseName = String.valueOf(item.getOrDefault("diagnosisName", item.getOrDefault("diseaseName", ""))).trim();
        row.put("diseaseName", diseaseName);
        row.put("recommendIcd", item.getOrDefault("recommendIcd", item.get("recommend_icd")));
        row.put("probability", item.get("probability"));
        String riskLevel = String.valueOf(item.getOrDefault("riskLevel", item.getOrDefault("risk_level", "low"))).trim();
        row.put("riskLevel", riskLevel.isEmpty() ? "low" : riskLevel);
        row.put("treatmentDirection", item.getOrDefault("treatmentDirection", item.get("treatment_direction")));
        row.put("diagnosisBasis", item.getOrDefault("diagnosisBasis", item.get("diagnosis_basis")));
        Object itemSort = item.get("sortOrder");
        row.put("sortOrder", itemSort == null ? sortOrder : itemSort);
        row.put("modelId", modelId);
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
        String overallAnalysis = "";
        String globalClinicalImpression = "";

        for (Map<String, Object> row : analyses) {
            Map<String, Object> indicators = parseW3AbnormalIndicators(row.get("abnormalIndicators"));
            String techName = String.valueOf(indicators.getOrDefault("techName", "")).trim();
            if (techName.isEmpty()) {
                String legacyType = String.valueOf(row.getOrDefault("analysisType", "")).trim();
                if (!isDbAnalysisType(legacyType)) {
                    techName = legacyType;
                }
            }

            Map<String, Object> w3Global = JsonMapUtils.asMap(indicators.get("w3Global"));
            if (globalClinicalImpression.isEmpty()) {
                globalClinicalImpression = String.valueOf(w3Global.getOrDefault("clinicalImpression", "")).trim();
            }
            if (overallAnalysis.isEmpty()) {
                String fromGlobal = String.valueOf(w3Global.getOrDefault("overallAnalysis", "")).trim();
                if (!fromGlobal.isEmpty()) {
                    overallAnalysis = fromGlobal;
                }
            }
            if (overallAnalysis.isEmpty() && row.get("correlationAnalysis") != null) {
                String correlation = String.valueOf(row.get("correlationAnalysis")).trim();
                if (!correlation.isEmpty()) {
                    overallAnalysis = correlation;
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("techName", techName);
            summary.put("techType", indicators.get("techType"));
            summary.put("clinicalImpression", indicators.get("clinicalImpression"));
            summary.put("indicatorRows", listOfMaps(indicators.get("indicatorRows")));
            summary.put("keyFindings", listOfStrings(indicators.get("keyFindings")));
            summary.put("interpretation", row.get("analysisReport"));
            summary.put("riskLevel", row.get("riskLevel"));
            if (!techName.isEmpty() || !listOfStrings(indicators.get("keyFindings")).isEmpty()) {
                summaries.add(summary);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clinicalImpression", globalClinicalImpression);
        out.put("examSummaries", summaries);
        out.put("overallAnalysis", overallAnalysis.isEmpty() ? "暂无分析" : overallAnalysis);
        out.put("explicitNonDiagnosis", true);
        return out;
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
        String cached = w2PreliminaryAssessmentByRegister.get(registerId);
        if (cached != null && !cached.isBlank()) {
            return cached.trim();
        }

        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (record != null) {
            String preliminaryDiagnosis = String.valueOf(record.getOrDefault("preliminaryDiagnosis", "")).trim();
            if (!preliminaryDiagnosis.isEmpty()) {
                return preliminaryDiagnosis;
            }
        }

        Map<String, Object> structured = loadStructuredRecord(registerId);
        String impression = String.valueOf(structured.getOrDefault("preliminaryImpression", "")).trim();
        if (!impression.isEmpty()) {
            return impression;
        }

        List<Map<String, Object>> suggestions = physicianMapper.selectExamSuggestions(registerId);
        if (suggestions.isEmpty()) {
            return "";
        }

        StringBuilder merged = new StringBuilder();
        int limit = Math.min(3, suggestions.size());
        for (int i = 0; i < limit; i++) {
            String reason = String.valueOf(suggestions.get(i).getOrDefault("suggestReason", "")).trim();
            if (reason.isEmpty()) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append("；");
            }
            merged.append(reason);
        }
        return merged.toString();
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

    private Map<String, String> buildTechTypeByName(Long registerId) {
        Map<String, String> techTypeByName = new HashMap<>();
        for (Map<String, Object> result : buildAllResults(registerId)) {
            String name = String.valueOf(result.getOrDefault("techName", "")).trim();
            String type = String.valueOf(result.getOrDefault("techType", "")).trim();
            if (!name.isEmpty() && isDbAnalysisType(type)) {
                techTypeByName.put(name, type);
            }
        }
        return techTypeByName;
    }

    private Map<String, Long> buildCheckRequestIdByTechName(Long registerId) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : physicianMapper.selectCheckResults(registerId)) {
            String name = String.valueOf(row.getOrDefault("techName", "")).trim();
            Long id = toLong(row.get("id"));
            if (!name.isEmpty() && id != null) {
                map.put(name, id);
            }
        }
        return map;
    }

    private Map<String, Long> buildInspectionRequestIdByTechName(Long registerId) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : physicianMapper.selectInspectionResults(registerId)) {
            String name = String.valueOf(row.getOrDefault("techName", "")).trim();
            Long id = toLong(row.get("id"));
            if (!name.isEmpty() && id != null) {
                map.put(name, id);
            }
        }
        return map;
    }

    private static String resolveDbAnalysisType(Map<String, Object> summary, Map<String, String> techTypeByName) {
        String techType = String.valueOf(summary.getOrDefault("techType", "")).trim();
        if ("check".equals(techType) || "inspection".equals(techType)) {
            return techType;
        }
        String techName = String.valueOf(summary.getOrDefault("techName", "")).trim();
        if (!techName.isEmpty()) {
            String mapped = techTypeByName.get(techName);
            if (mapped != null) {
                return mapped;
            }
        }
        return inferDbAnalysisTypeFromTechName(techName);
    }

    private static String inferDbAnalysisTypeFromTechName(String techName) {
        String name = techName == null ? "" : techName.toUpperCase();
        if (name.contains("血") || name.contains("CRP") || name.contains("蛋白") || name.contains("检验")) {
            return "inspection";
        }
        return "check";
    }

    private static boolean isDbAnalysisType(String value) {
        return "check".equals(value) || "inspection".equals(value);
    }

    private static String buildW3AbnormalIndicatorsJson(
        String techName,
        String techType,
        String clinicalImpression,
        List<String> keyFindings,
        List<Map<String, Object>> indicatorRows,
        String globalClinicalImpression,
        String overallAnalysis
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("techName", techName == null ? "" : techName);
        payload.put("techType", techType == null ? "" : techType);
        payload.put("clinicalImpression", clinicalImpression == null ? "" : clinicalImpression);
        payload.put("keyFindings", keyFindings == null ? List.of() : keyFindings);
        payload.put("indicatorRows", indicatorRows == null ? List.of() : indicatorRows);
        if ((globalClinicalImpression != null && !globalClinicalImpression.isBlank())
            || (overallAnalysis != null && !overallAnalysis.isBlank())) {
            Map<String, Object> w3Global = new LinkedHashMap<>();
            w3Global.put("clinicalImpression", globalClinicalImpression == null ? "" : globalClinicalImpression);
            w3Global.put("overallAnalysis", overallAnalysis == null ? "" : overallAnalysis);
            payload.put("w3Global", w3Global);
        }
        return JsonMapUtils.toJson(payload);
    }

    private Map<String, Object> parseW3AbnormalIndicators(Object raw) {
        Map<String, Object> parsed = JsonMapUtils.asMap(raw);
        if (parsed.containsKey("keyFindings") || parsed.containsKey("techName") || parsed.containsKey("indicatorRows")) {
            return parsed;
        }
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("techName", "");
        legacy.put("keyFindings", listOfStrings(raw));
        legacy.put("indicatorRows", List.of());
        return legacy;
    }

    private static String normalizeRiskLevelForDb(Object raw) {
        String level = String.valueOf(raw == null ? "normal" : raw).trim().toLowerCase();
        return switch (level) {
            case "attention", "warning", "danger" -> level;
            case "high" -> "warning";
            default -> "normal";
        };
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

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
