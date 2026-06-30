package com.xikang.physician.agent;

import com.xikang.common.exception.BusinessException;
import com.xikang.physician.ai.DifyWorkflowException;
import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.service.PhysicianService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify Agent Custom API 工具执行器（由 {@link PhysicianAgentToolController} 暴露）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicianAgentToolService {

    private final PhysicianAiPipelineService pipelineService;
    private final PhysicianService physicianService;

    public Map<String, Object> runPreliminaryDiagnosis(Map<String, Object> body) {
        return execute(body, registerId -> {
            Map<String, Object> record = physicianService.getMedicalRecord(registerId);
            Map<String, Object> patient = physicianService.getPatient(registerId);
            String text = buildClinicalText(record, patient);
            if (text.isBlank()) {
                throw new IllegalArgumentException("暂无病历或预问诊内容，无法运行初步诊断");
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("registerId", registerId);
            request.put("text", text);
            request.put("preHandle", true);
            return pipelineService.runPreliminaryDiagnosis(request);
        });
    }

    public Map<String, Object> runW1(Map<String, Object> body) {
        return execute(body, registerId -> {
            Map<String, Object> record = physicianService.getMedicalRecord(registerId);
            Map<String, Object> patient = physicianService.getPatient(registerId);
            String longText = buildClinicalText(record, patient);
            if (longText.isBlank()) {
                throw new IllegalArgumentException("暂无病历或预问诊内容，无法运行 W1");
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("registerId", registerId);
            request.put("inputMode", "long_text");
            request.put("longText", longText);
            return pipelineService.runW1(request);
        });
    }

    public Map<String, Object> runW2(Map<String, Object> body) {
        return execute(body, pipelineService::runW2);
    }

    public Map<String, Object> runW3(Map<String, Object> body) {
        return execute(body, pipelineService::runW3);
    }

    public Map<String, Object> runW4(Map<String, Object> body) {
        return execute(body, pipelineService::runW4);
    }

    public Map<String, Object> runW5(Map<String, Object> body) {
        return execute(body, pipelineService::runW5);
    }

    public Map<String, Object> getMedicalRecord(Map<String, Object> body) {
        return execute(body, registerId -> {
            Map<String, Object> record = physicianService.getMedicalRecord(registerId);
            if (record == null || record.isEmpty()) {
                return Map.of("registerId", registerId, "empty", true, "message", "暂无病历记录");
            }
            return record;
        });
    }

    public Map<String, Object> getLabResults(Map<String, Object> body) {
        return execute(body, registerId -> {
            List<Map<String, Object>> checks = physicianService.getCheckResults(registerId);
            List<Map<String, Object>> inspections = physicianService.getInspectionResults(registerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("checkResults", checks);
            result.put("inspectionResults", inspections);
            return result;
        });
    }

    public Map<String, Object> savePreliminaryDiagnosis(Map<String, Object> body) {
        return execute(body, registerId -> {
            String diagnosis = textValue(body.get("preliminary_diagnosis"));
            if (diagnosis == null || diagnosis.isBlank()) {
                throw new IllegalArgumentException("preliminary_diagnosis 不能为空");
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("registerId", registerId);
            request.put("preliminaryDiagnosis", diagnosis.trim());
            Object diseaseIds = body.get("disease_ids");
            if (diseaseIds == null) {
                diseaseIds = body.get("diseaseIds");
            }
            if (diseaseIds != null) {
                request.put("diseaseIds", diseaseIds);
            }
            physicianService.savePreliminaryDiagnosis(request);
            Map<String, Object> saved = new LinkedHashMap<>();
            saved.put("registerId", registerId);
            saved.put("preliminaryDiagnosis", diagnosis.trim());
            saved.put("saved", true);
            return saved;
        });
    }

    private Map<String, Object> execute(Map<String, Object> body, ToolAction action) {
        Long registerId = parseRegisterId(body);
        if (registerId == null) {
            return error(400, "register_id 无效或缺失");
        }
        AgentToolExecutionContext.enable();
        try {
            Map<String, Object> data = action.run(registerId);
            return success(registerId, data);
        } catch (IllegalArgumentException ex) {
            log.warn("Agent tool bad request registerId={}: {}", registerId, ex.getMessage());
            return error(400, ex.getMessage());
        } catch (BusinessException ex) {
            log.warn("Agent tool business error registerId={}: {}", registerId, ex.getMessage());
            return error(ex.getCode() > 0 ? ex.getCode() : 400, ex.getMessage());
        } catch (DifyWorkflowException ex) {
            log.warn("Agent tool Dify error registerId={}: {}", registerId, ex.getMessage());
            return error(502, ex.getMessage());
        } catch (Exception ex) {
            log.error("Agent tool failed registerId={}", registerId, ex);
            return error(500, "工具执行失败，请稍后重试");
        } finally {
            AgentToolExecutionContext.clear();
        }
    }

    private Map<String, Object> success(Long registerId, Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("registerId", registerId);
        response.put("data", data);
        return response;
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("code", code);
        response.put("error", message);
        return response;
    }

    private Long parseRegisterId(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object value = body.get("register_id");
        if (value == null) {
            value = body.get("registerId");
        }
        return toLong(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String buildClinicalText(Map<String, Object> record, Map<String, Object> patient) {
        if (record != null && !record.isEmpty()) {
            String recordText = buildRecordText(record);
            if (!recordText.isBlank()) {
                return recordText;
            }
        }
        return buildPreConsultationText(patient);
    }

    private String buildRecordText(Map<String, Object> record) {
        List<String> parts = new ArrayList<>();
        appendLine(parts, "主诉", record.get("readme"));
        appendLine(parts, "现病史", record.get("present"));
        appendLine(parts, "现病治疗", record.get("presentTreat"));
        appendLine(parts, "既往史", record.get("history"));
        appendLine(parts, "过敏史", record.get("allergy"));
        appendLine(parts, "体格检查", record.get("physique"));
        return String.join("\n", parts);
    }

    @SuppressWarnings("unchecked")
    private String buildPreConsultationText(Map<String, Object> patient) {
        if (patient == null) {
            return "";
        }
        Object summaryObj = patient.get("aiConsultSummary");
        if (!(summaryObj instanceof Map<?, ?> summary)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        appendLine(parts, "主诉", ((Map<String, Object>) summary).get("chiefComplaint"));
        appendLine(parts, "摘要", ((Map<String, Object>) summary).get("aiSummary"));
        return String.join("\n", parts);
    }

    private void appendLine(List<String> parts, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            parts.add(label + "：" + text);
        }
    }

    @FunctionalInterface
    private interface ToolAction {
        Map<String, Object> run(Long registerId) throws Exception;
    }
}
