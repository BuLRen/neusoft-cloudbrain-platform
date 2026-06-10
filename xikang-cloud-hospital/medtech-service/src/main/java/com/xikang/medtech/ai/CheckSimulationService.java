package com.xikang.medtech.ai;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.entity.CheckRequest;
import com.xikang.medtech.entity.MedicalTechnology;
import com.xikang.medtech.mapper.CheckRequestMapper;
import com.xikang.medtech.mapper.MedicalTechnologyMapper;
import com.xikang.medtech.service.ResultFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckSimulationService {

    private static final int DIFY_MAX_ATTEMPTS = 2;

    private final CheckRequestMapper checkRequestMapper;
    private final MedicalTechnologyMapper medicalTechnologyMapper;
    private final ResultFormService resultFormService;
    private final DifyWorkflowClient difyWorkflowClient;
    private final CheckSimulationFallbackEngine fallbackEngine;
    private final CheckSimulationOutputMapper outputMapper;
    private final CheckSimulationContextBuilder contextBuilder;
    private final CtInferenceService ctInferenceService;

    public Map<String, Object> simulateCheck(Long checkRequestId, Map<String, Object> requestBody) {
        CheckContext ctx = requireInProgressContext(checkRequestId);
        if (isCtCategory(ctx.aiCategoryCode())) {
            throw new BusinessException(400, "CT 影像请使用 CT 影像分析，不支持工作流模拟");
        }

        Map<String, Object> schema = resultFormService.resolveByCheckRequestId(checkRequestId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");

        boolean isNormal = parseIsNormal(requestBody);
        Map<String, Object> inputs = buildWorkflowInputs(ctx, isNormal);
        String user = "check-" + checkRequestId;

        Map<String, Object> outputs;
        String source;
        String workflowRunId = null;
        Double elapsedTime = null;
        String difyError = null;

        if (difyWorkflowClient.isCheckSimulateEnabled()) {
            DifyInvocation invocation = invokeDifyWithRetry(checkRequestId, inputs, user);
            outputs = invocation.outputs();
            source = invocation.source();
            workflowRunId = invocation.workflowRunId();
            elapsedTime = invocation.elapsedTime();
            difyError = invocation.difyError();
        } else {
            log.info("Check simulate Dify disabled, using fallback | checkRequestId={}", checkRequestId);
            outputs = fallbackEngine.simulateSingleExam(inputs);
            source = "fallback";
        }

        return buildSimulationResponse(outputs, fields, source, workflowRunId, elapsedTime, difyError);
    }

    public Map<String, Object> inferCtCheck(Long checkRequestId) {
        CheckContext ctx = requireInProgressContext(checkRequestId);
        if (!isCtCategory(ctx.aiCategoryCode())) {
            throw new BusinessException(400, "当前检查项目不是 CT 影像，请使用模拟检查");
        }

        Map<String, Object> schema = resultFormService.resolveByCheckRequestId(checkRequestId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");

        Map<String, Object> ctInput = new LinkedHashMap<>();
        ctInput.put("checkRequestId", checkRequestId);
        ctInput.put("registerId", ctx.request().getRegisterId());
        ctInput.put("techId", ctx.technology().getId());
        ctInput.put("examName", ctx.technology().getTechName());
        ctInput.put("bodyPart", ctx.aiCategoryCode().contains("brain") ? "brain" : "chest");
        ctInput.put("randomSeed", ctx.request().getRegisterId());

        Map<String, Object> ctResult = ctInferenceService.infer(ctInput);
        Map<String, Object> simulatedValues = outputMapper.mapCtInferenceToFormValues(ctResult);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "ct-inference");
        response.put("simulatedValues", simulatedValues);
        response.put("resultText", ctResult.get("resultText"));
        response.put("riskLevel", ctResult.get("riskLevel"));
        response.put("limitations", ctResult.get("limitations"));
        response.put("fields", fields);
        return response;
    }

    private Map<String, Object> buildSimulationResponse(
        Map<String, Object> outputs,
        List<Map<String, Object>> fields,
        String source,
        String workflowRunId,
        Double elapsedTime,
        String difyError
    ) {
        Map<String, Object> structuredOutput = outputMapper.extractStructuredOutput(outputs);
        Map<String, Object> simulatedValues = outputMapper.mapToFormValues(outputs, fields);
        // #region agent log
        logAgentExtraction(outputs, structuredOutput, simulatedValues, source);
        // #endregion

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", source);
        if (!structuredOutput.isEmpty()) {
            response.put("structuredOutput", structuredOutput);
        }
        response.put("simulatedValues", simulatedValues);
        response.put("resultText", firstNonBlank(
            outputMapper.extractResultText(outputs),
            asString(structuredOutput.get("conclusion")),
            asString(outputs.get("resultText"))
        ));
        if (workflowRunId != null) {
            response.put("workflowRunId", workflowRunId);
        }
        if (elapsedTime != null) {
            response.put("elapsedTime", elapsedTime);
        }
        if (difyError != null && !difyError.isBlank()) {
            response.put("difyError", difyError);
        }
        response.put("fields", fields);
        return response;
    }

    private Map<String, Object> buildWorkflowInputs(CheckContext ctx, boolean isNormal) {
        CheckRequest request = ctx.request();
        MedicalTechnology tech = ctx.technology();
        Long registerId = request.getRegisterId();

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("checkName", tech.getTechName());
        inputs.put("isNormal", isNormal ? "true" : "false");
        inputs.put("possibleDiseases", contextBuilder.serializePossibleDiseases(registerId));
        inputs.put("checkPurpose", request.getCheckInfo() == null ? "" : request.getCheckInfo());
        inputs.put("patientContext", contextBuilder.buildPatientContext(registerId));
        inputs.put("randomSeed", String.valueOf(request.getId()));
        return inputs;
    }

    private CheckContext requireInProgressContext(Long checkRequestId) {
        CheckRequest request = checkRequestMapper.selectById(checkRequestId);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!"检查中".equals(request.getCheckState())) {
            throw new BusinessException(400, "请先开始检查后再运行模拟");
        }
        MedicalTechnology technology = medicalTechnologyMapper.selectById(request.getMedicalTechnologyId());
        if (technology == null) {
            throw new BusinessException(404, "检查项目不存在");
        }
        if (!"check".equals(technology.getTechType())) {
            throw new BusinessException(400, "当前申请不是检查类型");
        }
        return new CheckContext(request, technology);
    }

    static boolean isCtCategory(String aiCategoryCode) {
        return aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct");
    }

    private static boolean parseIsNormal(Map<String, Object> requestBody) {
        if (requestBody == null || !requestBody.containsKey("isNormal")) {
            return false;
        }
        Object value = requestBody.get("isNormal");
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private DifyInvocation invokeDifyWithRetry(Long checkRequestId, Map<String, Object> inputs, String user) {
        DifyWorkflowException lastError = null;
        String lastRunId = null;
        Double lastElapsed = null;

        for (int attempt = 1; attempt <= DIFY_MAX_ATTEMPTS; attempt++) {
            try {
                log.info(
                    "Check simulate invoking Dify | checkRequestId={} checkName={} attempt={}",
                    checkRequestId,
                    inputs.get("checkName"),
                    attempt
                );
                DifyWorkflowRunResult result = difyWorkflowClient.runCheckSimulateBlocking(inputs, user, null);
                lastRunId = result.getWorkflowRunId();
                lastElapsed = result.getElapsedTime();
                Map<String, Object> candidateOutputs = result.getOutputs();
                if (outputMapper.hasUsableStructuredOutput(candidateOutputs)) {
                    log.info(
                        "Check simulate Dify succeeded | checkRequestId={} workflowRunId={} attempt={}",
                        checkRequestId,
                        lastRunId,
                        attempt
                    );
                    return new DifyInvocation(candidateOutputs, "workflow", lastRunId, lastElapsed, null);
                }
                log.warn(
                    "Check simulate Dify returned unusable structured output | checkRequestId={} attempt={} workflowRunId={} outputKeys={}",
                    checkRequestId,
                    attempt,
                    lastRunId,
                    candidateOutputs == null ? List.of() : candidateOutputs.keySet()
                );
            } catch (DifyWorkflowException ex) {
                lastError = ex;
                log.warn(
                    "Check simulate Dify failed | checkRequestId={} attempt={} reason={}",
                    checkRequestId,
                    attempt,
                    ex.getMessage()
                );
            }
        }

        String error = lastError == null
            ? "Dify 工作流未返回可用的结构化检查结果"
            : lastError.getMessage();
        log.warn("Check simulate using fallback after Dify attempts exhausted | checkRequestId={}", checkRequestId);
        return new DifyInvocation(
            fallbackEngine.simulateSingleExam(inputs),
            "fallback",
            lastRunId,
            lastElapsed,
            error
        );
    }

    private static final ObjectMapper DEBUG_MAPPER = new ObjectMapper();

    private static void logAgentExtraction(
        Map<String, Object> outputs,
        Map<String, Object> structuredOutput,
        Map<String, Object> simulatedValues,
        String source
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", "95b2d9");
            payload.put("location", "CheckSimulationService.java:buildSimulationResponse");
            payload.put("message", "structured output extraction result");
            payload.put(
                "data",
                Map.of(
                    "source", source == null ? "" : source,
                    "outputKeys", outputs == null ? List.of() : outputs.keySet().stream().sorted().collect(Collectors.toList()),
                    "structuredKeys", structuredOutput == null ? List.of() : structuredOutput.keySet().stream().sorted().collect(Collectors.toList()),
                    "resultItemsLen", structuredOutput != null && structuredOutput.get("resultItems") instanceof List<?> list ? list.size() : 0,
                    "simulatedValueKeys", simulatedValues == null ? List.of() : simulatedValues.keySet().stream().sorted().collect(Collectors.toList())
                )
            );
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("hypothesisId", "F");
            Files.writeString(
                Path.of("/Users/zanderc/Code/neusoft-cloudbrain-platform/neusoft-cloudbrain-platform/.cursor/debug-95b2d9.log"),
                DEBUG_MAPPER.writeValueAsString(payload) + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // ignore debug logging failures
        }
    }

    private record DifyInvocation(
        Map<String, Object> outputs,
        String source,
        String workflowRunId,
        Double elapsedTime,
        String difyError
    ) {}

    private record CheckContext(CheckRequest request, MedicalTechnology technology) {
        String aiCategoryCode() {
            String code = technology.getAiCategoryCode();
            return code == null ? "" : code;
        }
    }
}
