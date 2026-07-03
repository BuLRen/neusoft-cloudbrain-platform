package com.xikang.medtech.ai;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.entity.CheckRequest;
import com.xikang.medtech.entity.InspectionRequest;
import com.xikang.medtech.entity.MedicalTechnology;
import com.xikang.medtech.mapper.CheckRequestMapper;
import com.xikang.medtech.mapper.InspectionRequestMapper;
import com.xikang.medtech.mapper.MedicalTechnologyMapper;
import com.xikang.medtech.service.MedtechService;
import com.xikang.medtech.service.ResultFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckSimulationService {

    private static final int DIFY_MAX_ATTEMPTS = 2;

    private final CheckRequestMapper checkRequestMapper;
    private final InspectionRequestMapper inspectionRequestMapper;
    private final MedicalTechnologyMapper medicalTechnologyMapper;
    private final ResultFormService resultFormService;
    private final DifyWorkflowClient difyWorkflowClient;
    private final CheckSimulationFallbackEngine fallbackEngine;
    private final CheckSimulationOutputMapper outputMapper;
    private final CheckSimulationContextBuilder contextBuilder;
    private final CtInferenceService ctInferenceService;
    private final MedtechService medtechService;

    public Map<String, Object> simulateCheck(Long checkRequestId, Map<String, Object> requestBody) {
        CheckContext ctx = requireInProgressCheckContext(checkRequestId);
        if (isCtCategory(ctx.aiCategoryCode())) {
            throw new BusinessException(400, "CT 影像请使用 CT 影像分析，不支持工作流模拟");
        }

        Map<String, Object> schema = resultFormService.resolveByCheckRequestId(checkRequestId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");

        boolean normalStatus = parseNormalStatus(requestBody);
        Map<String, Object> inputs = buildWorkflowInputs(
            ctx.technology().getTechName(),
            ctx.technology().getTechCode(),
            ctx.request().getCheckInfo(),
            ctx.request().getRegisterId(),
            normalStatus
        );
        String user = "check-" + checkRequestId;

        Map<String, Object> response = runWorkflowSimulation(
            checkRequestId,
            inputs,
            user,
            fields,
            "Check simulate"
        );
        persistCheckSimulationDraft(checkRequestId, response);
        return response;
    }

    public Map<String, Object> simulateInspection(Long inspectionRequestId, Map<String, Object> requestBody) {
        InspectionContext ctx = requireInProgressInspectionContext(inspectionRequestId);

        Map<String, Object> schema = resultFormService.resolveByInspectionRequestId(inspectionRequestId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");

        boolean normalStatus = parseNormalStatus(requestBody);
        Map<String, Object> inputs = buildWorkflowInputs(
            ctx.technology().getTechName(),
            ctx.technology().getTechCode(),
            ctx.request().getInspectionInfo(),
            ctx.request().getRegisterId(),
            normalStatus
        );
        String user = "inspection-" + inspectionRequestId;

        Map<String, Object> response = runWorkflowSimulation(
            inspectionRequestId,
            inputs,
            user,
            fields,
            "Inspection simulate"
        );
        persistInspectionSimulationDraft(inspectionRequestId, response);
        return response;
    }

    public Map<String, Object> inferCtCheck(Long checkRequestId) {
        CheckRequest request = medtechService.requireCtImagingContext(checkRequestId, true);
        CheckContext ctx = toCheckContext(request);

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
        ctInput.put("volumeId", request.getImagingVolumeId());
        if (request.getImagingAnalysisResult() != null && !request.getImagingAnalysisResult().isBlank()) {
            ctInput.put("imagingAnalysisResult", request.getImagingAnalysisResult());
        }

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

    private Map<String, Object> runWorkflowSimulation(
        Long requestId,
        Map<String, Object> inputs,
        String user,
        List<Map<String, Object>> fields,
        String logPrefix
    ) {
        Map<String, Object> outputs;
        String source;
        String workflowRunId = null;
        Double elapsedTime = null;
        String difyError = null;

        if (difyWorkflowClient.isCheckSimulateEnabled()) {
            DifyInvocation invocation = invokeDifyWithRetry(requestId, inputs, user, logPrefix);
            outputs = invocation.outputs();
            source = invocation.source();
            workflowRunId = invocation.workflowRunId();
            elapsedTime = invocation.elapsedTime();
            difyError = invocation.difyError();
        } else {
            log.info("{} Dify disabled, using fallback | requestId={}", logPrefix, requestId);
            outputs = fallbackEngine.simulateSingleExam(inputs);
            source = "fallback";
        }

        return buildSimulationResponse(outputs, fields, source, workflowRunId, elapsedTime, difyError);
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

    private Map<String, Object> buildWorkflowInputs(
        String examName,
        String techCode,
        String purpose,
        Long registerId,
        boolean normalStatus
    ) {
        String normalFlag = normalStatus ? "true" : "false";
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("checkName", nullToEmpty(examName));
        inputs.put("techCode", nullToEmpty(techCode));
        // v2.0 Start 变量 isNormal：String 传 "true"/"false"；Boolean 传 true/false（Dify 控制台类型不同）
        inputs.put("isNormal", normalFlag);
        inputs.put("normal_status", normalFlag);
        inputs.put("possibleDiseases", contextBuilder.serializePossibleDiseases(registerId));
        inputs.put("patientContext", contextBuilder.buildPatientContext(registerId));
        inputs.put("checkPurpose", nullToEmpty(purpose));
        return inputs;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CheckContext requireInProgressCheckContext(Long checkRequestId) {
        CheckRequest request = checkRequestMapper.selectById(checkRequestId);
        if (request == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        if (!"检查中".equals(request.getCheckState())) {
            throw new BusinessException(400, "请先开始检查后再运行模拟");
        }
        medtechService.assertCheckDepartmentAccess(request.getMedicalTechnologyId());
        MedicalTechnology technology = medicalTechnologyMapper.selectById(request.getMedicalTechnologyId());
        if (technology == null) {
            throw new BusinessException(404, "检查项目不存在");
        }
        if (!"check".equals(technology.getTechType())) {
            throw new BusinessException(400, "当前申请不是检查类型");
        }
        return new CheckContext(request, technology);
    }

    private CheckContext toCheckContext(CheckRequest request) {
        MedicalTechnology technology = medicalTechnologyMapper.selectById(request.getMedicalTechnologyId());
        if (technology == null) {
            throw new BusinessException(404, "检查项目不存在");
        }
        if (!"check".equals(technology.getTechType())) {
            throw new BusinessException(400, "当前申请不是检查类型");
        }
        return new CheckContext(request, technology);
    }

    private InspectionContext requireInProgressInspectionContext(Long inspectionRequestId) {
        InspectionRequest request = inspectionRequestMapper.selectById(inspectionRequestId);
        if (request == null) {
            throw new BusinessException(404, "检验申请不存在");
        }
        if (!"检验中".equals(request.getInspectionState())) {
            throw new BusinessException(400, "请先开始检验后再运行模拟");
        }
        MedicalTechnology technology = medicalTechnologyMapper.selectById(request.getMedicalTechnologyId());
        if (technology == null) {
            throw new BusinessException(404, "检验项目不存在");
        }
        if (!"inspection".equals(technology.getTechType())) {
            throw new BusinessException(400, "当前申请不是检验类型");
        }
        return new InspectionContext(request, technology);
    }

    static boolean isCtCategory(String aiCategoryCode) {
        return aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct");
    }

    private static boolean parseNormalStatus(Map<String, Object> requestBody) {
        if (requestBody == null || !requestBody.containsKey("normal_status")) {
            return false;
        }
        Object value = requestBody.get("normal_status");
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

    private DifyInvocation invokeDifyWithRetry(
        Long requestId,
        Map<String, Object> inputs,
        String user,
        String logPrefix
    ) {
        DifyWorkflowException lastError = null;
        String lastRunId = null;
        Double lastElapsed = null;

        for (int attempt = 1; attempt <= DIFY_MAX_ATTEMPTS; attempt++) {
            try {
                log.info(
                    "{} invoking Dify | requestId={} checkName={} isNormal={} normal_status={} techCode={} attempt={}",
                    logPrefix,
                    requestId,
                    inputs.get("checkName"),
                    inputs.get("isNormal"),
                    inputs.get("normal_status"),
                    inputs.get("techCode"),
                    attempt
                );
                DifyWorkflowRunResult result = difyWorkflowClient.runCheckSimulateBlocking(inputs, user, null);
                lastRunId = result.getWorkflowRunId();
                lastElapsed = result.getElapsedTime();
                Map<String, Object> candidateOutputs = result.getOutputs();
                if (outputMapper.hasUsableStructuredOutput(candidateOutputs)) {
                    if (shouldRetryForValidationWarning(candidateOutputs) && attempt < DIFY_MAX_ATTEMPTS) {
                        log.warn(
                            "{} Dify validation warning, retrying | requestId={} attempt={} workflowRunId={}",
                            logPrefix,
                            requestId,
                            attempt,
                            lastRunId
                        );
                        continue;
                    }
                    log.info(
                        "{} Dify succeeded | requestId={} workflowRunId={} attempt={}",
                        logPrefix,
                        requestId,
                        lastRunId,
                        attempt
                    );
                    return new DifyInvocation(candidateOutputs, "workflow", lastRunId, lastElapsed, null);
                }
                log.warn(
                    "{} Dify returned unusable structured output | requestId={} attempt={} workflowRunId={} outputKeys={}",
                    logPrefix,
                    requestId,
                    attempt,
                    lastRunId,
                    candidateOutputs == null ? List.of() : candidateOutputs.keySet()
                );
            } catch (DifyWorkflowException ex) {
                lastError = ex;
                log.warn(
                    "{} Dify failed | requestId={} attempt={} reason={}",
                    logPrefix,
                    requestId,
                    attempt,
                    ex.getMessage()
                );
            }
        }

        String error = lastError == null
            ? "Dify 工作流未返回可用的结构化检查结果"
            : lastError.getMessage();
        log.warn("{} using fallback after Dify attempts exhausted | requestId={}", logPrefix, requestId);
        return new DifyInvocation(
            fallbackEngine.simulateSingleExam(inputs),
            "fallback",
            lastRunId,
            lastElapsed,
            error
        );
    }

    private boolean shouldRetryForValidationWarning(Map<String, Object> outputs) {
        Map<String, Object> structured = outputMapper.extractStructuredOutput(outputs);
        Object warning = structured.get("_validationWarning");
        return warning instanceof String text && !text.isBlank();
    }

    private void persistCheckSimulationDraft(Long checkRequestId, Map<String, Object> response) {
        try {
            medtechService.saveCheckSimulationDraft(checkRequestId, response);
        } catch (Exception ex) {
            log.warn("检查模拟草稿持久化失败 | checkRequestId={}", checkRequestId, ex);
        }
    }

    private void persistInspectionSimulationDraft(Long inspectionRequestId, Map<String, Object> response) {
        try {
            medtechService.saveInspectionSimulationDraft(inspectionRequestId, response);
        } catch (Exception ex) {
            log.warn("检验模拟草稿持久化失败 | inspectionRequestId={}", inspectionRequestId, ex);
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

    private record InspectionContext(InspectionRequest request, MedicalTechnology technology) {}
}
