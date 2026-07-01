package com.xikang.physician.agent;
import com.xikang.common.agent.AgentToolExecutionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.physician.ai.DifyWorkflowException;
import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.copilot.entity.PhysicianAiChatSession;
import com.xikang.physician.copilot.mapper.PhysicianAiChatSessionMapper;

import com.xikang.physician.client.PhysicianClinicalClient;
import com.xikang.physician.mapper.PhysicianAiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Dify Agent Custom API 工具执行器（由 {@link PhysicianAgentToolController} 暴露）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicianAgentToolService {

    private final PhysicianAiPipelineService pipelineService;
    private final PhysicianClinicalClient physicianClinicalClient;
    private final PhysicianAiMapper physicianAiMapper;
    private final AgentToolAuditService auditService;
    private final AgentConfirmationService confirmationService;
    private final AgentCommitExecutor commitExecutor;
    private final PhysicianAiChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    // --- Workflow tools ---

    public Map<String, Object> runPreliminaryDiagnosis(Map<String, Object> body) {
        return execute("tool_run_preliminary_diagnosis", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
            Map<String, Object> patient = physicianClinicalClient.getPatient(registerId);
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
        return execute("tool_run_w1", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
            Map<String, Object> patient = physicianClinicalClient.getPatient(registerId);
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
        return execute("tool_run_w2", AgentToolExecutionContext.RiskLevel.READ, body, pipelineService::runW2);
    }

    public Map<String, Object> runW3(Map<String, Object> body) {
        return execute("tool_run_w3", AgentToolExecutionContext.RiskLevel.READ, body, pipelineService::runW3);
    }

    public Map<String, Object> runW4(Map<String, Object> body) {
        return execute("tool_run_w4", AgentToolExecutionContext.RiskLevel.READ, body, pipelineService::runW4);
    }

    public Map<String, Object> runW5(Map<String, Object> body) {
        return execute("tool_run_w5", AgentToolExecutionContext.RiskLevel.READ, body, pipelineService::runW5);
    }

    // --- Read tools ---

    public Map<String, Object> getMedicalRecord(Map<String, Object> body) {
        return execute("tool_get_medical_record", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
            if (record == null || record.isEmpty()) {
                return Map.of("registerId", registerId, "empty", true, "message", "暂无病历记录");
            }
            return record;
        });
    }

    public Map<String, Object> getLabResults(Map<String, Object> body) {
        return execute("tool_get_lab_results", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            List<Map<String, Object>> checks = physicianClinicalClient.getCheckResults(registerId);
            List<Map<String, Object>> inspections = physicianClinicalClient.getInspectionResults(registerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("checkResults", checks);
            result.put("inspectionResults", inspections);
            return result;
        });
    }

    public Map<String, Object> getPatient(Map<String, Object> body) {
        return execute("tool_get_patient", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            Map<String, Object> patient = physicianClinicalClient.getPatient(registerId);
            if (patient == null) {
                return Map.of("registerId", registerId, "empty", true);
            }
            return patient;
        });
    }

    public Map<String, Object> getMedicalTechnologies(Map<String, Object> body) {
        return execute("tool_get_medical_technologies", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            String techType = textValue(body.get("tech_type"));
            if (techType == null) {
                techType = textValue(body.get("techType"));
            }
            String keyword = textValue(body.get("keyword"));
            List<Map<String, Object>> list = physicianClinicalClient.getMedicalTechnologies(techType, keyword);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("techType", techType);
            result.put("items", list);
            result.put("count", list.size());
            return result;
        });
    }

    public Map<String, Object> getDiseases(Map<String, Object> body) {
        return execute("tool_get_diseases", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            String keyword = textValue(body.get("keyword"));
            List<Map<String, Object>> list = physicianClinicalClient.getDiseases(keyword);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("items", list);
            result.put("count", list.size());
            return result;
        });
    }

    public Map<String, Object> getDrugs(Map<String, Object> body) {
        return execute("tool_get_drugs", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            String keyword = textValue(body.get("keyword"));
            Integer page = toInt(body.get("page"));
            Integer pageSize = toInt(body.get("page_size"));
            if (pageSize == null) {
                pageSize = toInt(body.get("pageSize"));
            }
            Map<String, Object> pageResult = physicianClinicalClient.getDrugsPage(keyword, page, pageSize);
            pageResult.put("registerId", registerId);
            return pageResult;
        });
    }

    public Map<String, Object> getPrescriptions(Map<String, Object> body) {
        return execute("tool_get_prescriptions", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            List<Map<String, Object>> list = physicianClinicalClient.getPrescriptionList(registerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("items", list);
            result.put("count", list.size());
            return result;
        });
    }

    public Map<String, Object> getVisitTimeline(Map<String, Object> body) {
        return execute("tool_get_visit_timeline", AgentToolExecutionContext.RiskLevel.READ, body,
            physicianClinicalClient::getVisitTimeline);
    }

    public Map<String, Object> getExamSuggestions(Map<String, Object> body) {
        return execute("tool_get_exam_suggestions", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            List<Map<String, Object>> list = physicianAiMapper.selectExamSuggestions(registerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("items", list);
            result.put("count", list.size());
            return result;
        });
    }

    public Map<String, Object> getDiagnosisSuggestions(Map<String, Object> body) {
        return execute("tool_get_diagnosis_suggestions", AgentToolExecutionContext.RiskLevel.READ, body, registerId -> {
            List<Map<String, Object>> list = physicianAiMapper.selectDiagnosisSuggestions(registerId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("items", list);
            result.put("count", list.size());
            return result;
        });
    }

    // --- Draft tools ---

    public Map<String, Object> draftMedicalRecord(Map<String, Object> body) {
        return execute("tool_draft_medical_record", AgentToolExecutionContext.RiskLevel.DRAFT, body, registerId -> {
            Map<String, Object> current = physicianClinicalClient.getMedicalRecord(registerId);
            Map<String, Object> effectiveBody = new LinkedHashMap<>(body == null ? Map.of() : body);
            if (shouldMergeFromPreconsultation(body)) {
                mergePreconsultationIntoBody(registerId, effectiveBody, explicitBodyKeys(body));
            }
            Map<String, Object> proposed = mergeRecordDraft(current, effectiveBody);
            Map<String, Object> diff = buildRecordDiff(current, proposed);
            Map<String, Object> confirmPayload = buildMedicalRecordConfirmPayload(proposed, diff);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("current", current == null ? Map.of() : current);
            result.put("proposed", proposed);
            result.put("diff", diff);
            result.put("confirmActionType", "commit_medical_record");
            result.put("confirmPayload", confirmPayload);
            result.put("mergedFromPreconsultation", shouldMergeFromPreconsultation(body));
            if (diff.isEmpty()) {
                result.put("message", "未发现需要更新的病历字段；可设置 merge_from_preconsultation=true 或传入 allergy/history 等字段");
            } else {
                result.put("message", "草案已生成，请在回复末尾输出 ```confirm``` 块，供医生在前端确认后保存");
            }
            return result;
        });
    }

    public Map<String, Object> draftOrderBasket(Map<String, Object> body) {
        return execute("tool_draft_order_basket", AgentToolExecutionContext.RiskLevel.DRAFT, body, registerId -> {
            List<Map<String, Object>> items = extractOrderItems(body);
            if (items.isEmpty()) {
                Object fromW2 = body.get("use_w2");
                if (fromW2 == null) {
                    fromW2 = body.get("useW2");
                }
                if (Boolean.TRUE.equals(fromW2) || "true".equalsIgnoreCase(String.valueOf(fromW2))) {
                    Map<String, Object> w2 = pipelineService.runW2(registerId);
                    items = mapW2ToOrderItems(w2);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("请提供 items 或设置 use_w2=true");
            }
            String orderType = resolveOrderType(body);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("orderType", orderType);
            result.put("items", items);
            result.put("confirmActionType", confirmActionForOrderType(orderType));
            Map<String, Object> confirmPayload = new LinkedHashMap<>();
            confirmPayload.put("items", items);
            result.put("confirmPayload", confirmPayload);
            result.put("message", "检查检验申请草案已生成，请在回复末尾输出 ```confirm``` 块");
            return result;
        });
    }

    public Map<String, Object> draftDiagnosis(Map<String, Object> body) {
        return execute("tool_draft_diagnosis", AgentToolExecutionContext.RiskLevel.DRAFT, body, registerId -> {
            Map<String, Object> w4 = null;
            Object fromW4 = body.get("use_w4");
            if (fromW4 == null) {
                fromW4 = body.get("useW4");
            }
            if (Boolean.TRUE.equals(fromW4) || "true".equalsIgnoreCase(String.valueOf(fromW4))) {
                w4 = pipelineService.runW4(registerId);
            }
            Map<String, Object> proposed = buildDiagnosisDraft(registerId, body, w4);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("proposed", proposed);
            if (w4 != null) {
                result.put("w4Reference", w4);
            }
            result.put("confirmActionType", "commit_diagnosis");
            result.put("confirmPayload", proposed);
            result.put("message", "确诊草案已生成，请在回复末尾输出 ```confirm``` 块");
            return result;
        });
    }

    public Map<String, Object> draftPrescription(Map<String, Object> body) {
        return execute("tool_draft_prescription", AgentToolExecutionContext.RiskLevel.DRAFT, body, registerId -> {
            List<Map<String, Object>> items = extractPrescriptionItems(body);
            if (items.isEmpty()) {
                Object fromW5 = body.get("use_w5");
                if (fromW5 == null) {
                    fromW5 = body.get("useW5");
                }
                if (Boolean.TRUE.equals(fromW5) || "true".equalsIgnoreCase(String.valueOf(fromW5))) {
                    Map<String, Object> w5 = pipelineService.runW5(registerId);
                    items = mapW5ToPrescriptionItems(w5);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("请提供 items 或设置 use_w5=true");
            }
            Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
            String confirmedDiagnosis = textValue(body.get("confirmed_diagnosis"));
            if (confirmedDiagnosis == null) {
                confirmedDiagnosis = textValue(body.get("confirmedDiagnosis"));
            }
            if ((confirmedDiagnosis == null || confirmedDiagnosis.isBlank()) && record != null) {
                confirmedDiagnosis = textValue(record.get("diagnosis"));
            }
            Map<String, Object> proposed = new LinkedHashMap<>();
            proposed.put("confirmedDiagnosis", confirmedDiagnosis);
            proposed.put("items", items);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("proposed", proposed);
            result.put("confirmActionType", "commit_prescription");
            result.put("confirmPayload", proposed);
            result.put("message", "处方草案已生成，请在回复末尾输出 ```confirm``` 块");
            return result;
        });
    }

    public Map<String, Object> draftPreliminaryDiagnosis(Map<String, Object> body) {
        return execute("tool_draft_preliminary_diagnosis", AgentToolExecutionContext.RiskLevel.DRAFT, body, registerId -> {
            String diagnosis = textValue(body.get("preliminary_diagnosis"));
            if (diagnosis == null) {
                diagnosis = textValue(body.get("preliminaryDiagnosis"));
            }
            if (diagnosis == null || diagnosis.isBlank()) {
                Map<String, Object> prelim = runPreliminaryDiagnosis(body);
                if (Boolean.TRUE.equals(prelim.get("success"))) {
                    Object data = prelim.get("data");
                    if (data instanceof Map<?, ?> map) {
                        diagnosis = textValue(((Map<String, Object>) map).get("primaryDiagnosis"));
                    }
                }
            }
            if (diagnosis == null || diagnosis.isBlank()) {
                throw new IllegalArgumentException("无法生成初步诊断草案，请提供 preliminary_diagnosis 或先运行初步诊断");
            }
            Map<String, Object> proposed = new LinkedHashMap<>();
            proposed.put("preliminaryDiagnosis", diagnosis.trim());
            if (body.get("disease_ids") != null) {
                proposed.put("diseaseIds", body.get("disease_ids"));
            } else if (body.get("diseaseIds") != null) {
                proposed.put("diseaseIds", body.get("diseaseIds"));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("registerId", registerId);
            result.put("proposed", proposed);
            result.put("confirmActionType", "commit_preliminary_diagnosis");
            result.put("confirmPayload", proposed);
            result.put("message", "初步诊断草案已生成，请在回复末尾输出 ```confirm``` 块");
            return result;
        });
    }

    // --- Commit tools (require confirmation_token) ---

    public Map<String, Object> savePreliminaryDiagnosis(Map<String, Object> body) {
        return commitWithToken("tool_save_preliminary_diagnosis", "commit_preliminary_diagnosis", body);
    }

    public Map<String, Object> commitMedicalRecord(Map<String, Object> body) {
        return commitWithToken("tool_commit_medical_record", "commit_medical_record", body);
    }

    public Map<String, Object> commitCheckRequests(Map<String, Object> body) {
        return commitWithToken("tool_commit_check_requests", "commit_check_requests", body);
    }

    public Map<String, Object> commitInspectionRequests(Map<String, Object> body) {
        return commitWithToken("tool_commit_inspection_requests", "commit_inspection_requests", body);
    }

    public Map<String, Object> commitDisposalRequests(Map<String, Object> body) {
        return commitWithToken("tool_commit_disposal_requests", "commit_disposal_requests", body);
    }

    public Map<String, Object> commitDiagnosis(Map<String, Object> body) {
        return commitWithToken("tool_commit_diagnosis", "commit_diagnosis", body);
    }

    public Map<String, Object> commitPrescription(Map<String, Object> body) {
        return commitWithToken("tool_commit_prescription", "commit_prescription", body);
    }

    public Map<String, Object> commitArchiveVisit(Map<String, Object> body) {
        return commitWithToken("tool_commit_archive_visit", "commit_archive_visit", body);
    }

    private Map<String, Object> commitWithToken(String toolName, String actionType, Map<String, Object> body) {
        Long registerId = parseRegisterId(body);
        if (registerId == null) {
            return error(400, "register_id 无效或缺失");
        }
        String token = textValue(body.get("confirmation_token"));
        if (token == null) {
            token = textValue(body.get("confirmationToken"));
        }
        if (token == null || token.isBlank()) {
            return error(403, "写操作需要 confirmation_token，请医生在前端确认后提交");
        }

        AgentToolExecutionContext.enable(buildContext(toolName, AgentToolExecutionContext.RiskLevel.COMMIT, body));
        String beforeSnapshot = snapshotForAction(actionType, registerId);
        try {
            Map<String, Object> consumed = confirmationService.consume(token, registerId, extractPayload(body));
            Map<String, Object> payload = castMap(consumed.get("payload"));
            Map<String, Object> data = commitExecutor.execute(actionType, registerId, payload);
            String afterSnapshot = snapshotForAction(actionType, registerId);
            auditService.logSuccess(toolName, AgentToolExecutionContext.RiskLevel.COMMIT, registerId, body, data,
                beforeSnapshot, afterSnapshot, "dify-token", token);
            return success(registerId, data);
        } catch (BusinessException ex) {
            auditService.logFailure(toolName, AgentToolExecutionContext.RiskLevel.COMMIT, registerId, body, ex.getMessage());
            return error(ex.getCode() > 0 ? ex.getCode() : 400, ex.getMessage());
        } catch (Exception ex) {
            auditService.logFailure(toolName, AgentToolExecutionContext.RiskLevel.COMMIT, registerId, body, ex.getMessage());
            return error(500, ex.getMessage() != null ? ex.getMessage() : "提交失败");
        } finally {
            AgentToolExecutionContext.clear();
        }
    }

    private Map<String, Object> execute(
        String toolName,
        AgentToolExecutionContext.RiskLevel riskLevel,
        Map<String, Object> body,
        ToolAction action
    ) {
        Long registerId = parseRegisterId(body);
        if (registerId == null) {
            return error(400, "register_id 无效或缺失");
        }
        AgentToolExecutionContext.enable(buildContext(toolName, riskLevel, body));
        try {
            Map<String, Object> data = action.run(registerId);
            auditService.logSuccess(toolName, riskLevel, registerId, body, data, null, null, null, null);
            return success(registerId, data);
        } catch (IllegalArgumentException ex) {
            auditService.logFailure(toolName, riskLevel, registerId, body, ex.getMessage());
            log.warn("Agent tool bad request registerId={} tool={}: {}", registerId, toolName, ex.getMessage());
            return error(400, ex.getMessage());
        } catch (BusinessException ex) {
            auditService.logFailure(toolName, riskLevel, registerId, body, ex.getMessage());
            log.warn("Agent tool business error registerId={} tool={}: {}", registerId, toolName, ex.getMessage());
            return error(ex.getCode() > 0 ? ex.getCode() : 400, ex.getMessage());
        } catch (DifyWorkflowException ex) {
            auditService.logFailure(toolName, riskLevel, registerId, body, ex.getMessage());
            log.warn("Agent tool Dify error registerId={} tool={}: {}", registerId, toolName, ex.getMessage());
            return error(502, ex.getMessage());
        } catch (Exception ex) {
            auditService.logFailure(toolName, riskLevel, registerId, body, ex.getMessage());
            log.error("Agent tool failed registerId={} tool={}", registerId, toolName, ex);
            return error(500, "工具执行失败，请稍后重试");
        } finally {
            AgentToolExecutionContext.clear();
        }
    }

    private AgentToolExecutionContext.Context buildContext(
        String toolName,
        AgentToolExecutionContext.RiskLevel riskLevel,
        Map<String, Object> body
    ) {
        Long doctorId = parseLongField(body, "doctor_id", "doctorId");
        Long sessionId = parseLongField(body, "session_id", "sessionId");
        String requestId = textValue(body.get("request_id"));
        if (requestId == null) {
            requestId = textValue(body.get("requestId"));
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return new AgentToolExecutionContext.Context(doctorId, sessionId, requestId, toolName, riskLevel);
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
        Long registerId = parseLongField(body, "register_id", "registerId");
        if (registerId != null && registerId > 0) {
            return registerId;
        }
        // Dify Fixed 绑定可能只注入了 session_id，从会话反查 register_id
        Long sessionId = parseLongField(body, "session_id", "sessionId");
        if (sessionId != null && sessionId > 0) {
            PhysicianAiChatSession session = chatSessionMapper.selectById(sessionId);
            if (session != null && session.getRegisterId() != null && session.getRegisterId() > 0) {
                return session.getRegisterId();
            }
        }
        return null;
    }

    private Long parseLongField(Map<String, Object> body, String snakeKey, String camelKey) {
        Object value = body.get(snakeKey);
        if (value == null) {
            value = body.get(camelKey);
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

    private Integer toInt(Object value) {
        Long longValue = toLong(value);
        return longValue == null ? null : longValue.intValue();
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> body) {
        Object payload = body.get("payload");
        if (payload instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String snapshotForAction(String actionType, Long registerId) {
        try {
            return switch (actionType) {
                case "commit_medical_record", "commit_preliminary_diagnosis", "commit_diagnosis" ->
                    objectMapper.writeValueAsString(physicianClinicalClient.getMedicalRecord(registerId));
                case "commit_prescription" ->
                    objectMapper.writeValueAsString(physicianClinicalClient.getPrescriptionList(registerId));
                case "commit_check_requests", "commit_inspection_requests" -> {
                    Map<String, Object> lab = new LinkedHashMap<>();
                    lab.put("checkResults", physicianClinicalClient.getCheckResults(registerId));
                    lab.put("inspectionResults", physicianClinicalClient.getInspectionResults(registerId));
                    yield objectMapper.writeValueAsString(lab);
                }
                default -> null;
            };
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean shouldMergeFromPreconsultation(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        Object flag = body.get("merge_from_preconsultation");
        if (flag == null) {
            flag = body.get("mergeFromPreconsultation");
        }
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    private java.util.Set<String> explicitBodyKeys(Map<String, Object> body) {
        if (body == null) {
            return java.util.Set.of();
        }
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        List<String> fields = List.of(
            "readme", "present", "present_treat", "presentTreat", "history", "allergy", "physique", "proposal",
            "merge_from_preconsultation", "mergeFromPreconsultation", "register_id", "registerId",
            "doctor_id", "doctorId", "session_id", "sessionId", "request_id", "requestId"
        );
        for (String key : fields) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()
                && !"true".equalsIgnoreCase(String.valueOf(value))
                && !"false".equalsIgnoreCase(String.valueOf(value))) {
                keys.add(key);
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private void mergePreconsultationIntoBody(
        Long registerId,
        Map<String, Object> body,
        java.util.Set<String> explicitKeys
    ) {
        Map<String, Object> patient = physicianClinicalClient.getPatient(registerId);
        if (patient == null) {
            return;
        }
        Object summaryObj = patient.get("aiConsultSummary");
        if (!(summaryObj instanceof Map<?, ?> rawSummary)) {
            return;
        }
        Map<String, Object> summary = (Map<String, Object>) rawSummary;
        applyPreconsultField(body, explicitKeys, "readme", summary.get("chiefComplaint"));
        applyPreconsultField(body, explicitKeys, "present", firstNonBlank(
            textValue(summary.get("aiSummary")),
            textValue(summary.get("chiefComplaint"))
        ));
        applyPreconsultField(body, explicitKeys, "history", summary.get("historySummary"));
        applyPreconsultField(body, explicitKeys, "allergy", summary.get("allergySummary"));
        applyPreconsultField(body, explicitKeys, "proposal", summary.get("suggestedExam"));
    }

    private void applyPreconsultField(
        Map<String, Object> body,
        java.util.Set<String> explicitKeys,
        String key,
        Object value
    ) {
        if (explicitKeys.contains(key)) {
            return;
        }
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            body.put(key, text);
        }
    }

    private Map<String, Object> buildMedicalRecordConfirmPayload(
        Map<String, Object> proposed,
        Map<String, Object> diff
    ) {
        // 完整写入 proposed 中所有非空字段，而不是只写 diff 里有变化的字段。
        // 原因：commit 时通过 updateMedicalRecord 全量覆盖，若 payload 缺失某字段，
        // 该字段会以 null 写入数据库，导致已有数据被清除（如先改过敏史再改既往史时过敏史丢失）。
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> fields = List.of("readme", "present", "presentTreat", "history", "allergy", "physique", "proposal");
        for (String field : fields) {
            Object val = proposed.get(field);
            if (val != null) {
                payload.put(field, val);
            }
        }
        return payload;
    }

    private Map<String, Object> mergeRecordDraft(Map<String, Object> current, Map<String, Object> body) {
        Map<String, Object> proposed = new LinkedHashMap<>();
        if (current != null) {
            proposed.putAll(current);
        }
        copyDraftField(body, proposed, "readme");
        copyDraftField(body, proposed, "present");
        copyDraftField(body, proposed, "present_treat", "presentTreat");
        copyDraftField(body, proposed, "history");
        copyDraftField(body, proposed, "allergy");
        copyDraftField(body, proposed, "physique");
        copyDraftField(body, proposed, "proposal");
        if (body.get("disease_ids") != null) {
            proposed.put("diseaseIds", body.get("disease_ids"));
        } else if (body.get("diseaseIds") != null) {
            proposed.put("diseaseIds", body.get("diseaseIds"));
        }
        proposed.put("registerId", parseRegisterId(body));
        return proposed;
    }

    private void copyDraftField(Map<String, Object> from, Map<String, Object> to, String snakeKey, String... aliases) {
        if (from.get(snakeKey) != null) {
            to.put(camelCase(snakeKey), from.get(snakeKey));
            return;
        }
        for (String alias : aliases) {
            if (from.get(alias) != null) {
                to.put(alias, from.get(alias));
                return;
            }
        }
    }

    private String camelCase(String snake) {
        if ("present_treat".equals(snake)) {
            return "presentTreat";
        }
        return snake;
    }

    private Map<String, Object> buildRecordDiff(Map<String, Object> current, Map<String, Object> proposed) {
        List<String> fields = List.of("readme", "present", "presentTreat", "history", "allergy", "physique", "proposal");
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String field : fields) {
            String before = current == null ? "" : Objects.toString(current.get(field), "").trim();
            String after = Objects.toString(proposed.get(field), "").trim();
            if (!before.equals(after)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", before);
                change.put("after", after);
                diff.put(field, change);
            }
        }
        return diff;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractOrderItems(Map<String, Object> body) {
        Object items = body.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapW2ToOrderItems(Map<String, Object> w2) {
        Object recommended = w2.get("recommendedExaminations");
        if (!(recommended instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            Object techId = map.get("techId");
            if (techId == null) {
                techId = map.get("technologyId");
            }
            if (techId != null) {
                row.put("technologyId", techId);
            }
            Object techName = map.get("techName");
            if (techName == null) {
                techName = map.get("name");
            }
            if (techName != null) {
                row.put("technologyName", techName);
            }
            Object techType = map.get("techType");
            if (techType == null) {
                techType = map.get("type");
            }
            if (techType != null) {
                row.put("techType", techType);
            }
            items.add(row);
        }
        return items;
    }

    private String resolveOrderType(Map<String, Object> body) {
        String orderType = textValue(body.get("order_type"));
        if (orderType == null) {
            orderType = textValue(body.get("orderType"));
        }
        if (orderType == null || orderType.isBlank()) {
            return "check";
        }
        return orderType.trim().toLowerCase();
    }

    private String confirmActionForOrderType(String orderType) {
        return switch (orderType) {
            case "inspection" -> "commit_inspection_requests";
            case "disposal" -> "commit_disposal_requests";
            default -> "commit_check_requests";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDiagnosisDraft(Long registerId, Map<String, Object> body, Map<String, Object> w4) {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("registerId", registerId);
        proposed.put("diagnosis", firstNonBlank(
            textValue(body.get("diagnosis")),
            w4 == null ? null : textValue(w4.get("primaryDiagnosis")),
            w4SuggestionName(w4)
        ));
        proposed.put("cure", firstNonBlank(textValue(body.get("cure")), w4 == null ? null : textValue(w4.get("treatmentPlan"))));
        proposed.put("careful", firstNonBlank(textValue(body.get("careful")), w4 == null ? null : textValue(w4.get("precautions"))));
        if (body.get("disease_ids") != null) {
            proposed.put("diseaseIds", body.get("disease_ids"));
        } else if (body.get("diseaseIds") != null) {
            proposed.put("diseaseIds", body.get("diseaseIds"));
        } else if (w4 != null && w4.get("suggestions") instanceof List<?> suggestions && !suggestions.isEmpty()) {
            Object first = suggestions.get(0);
            if (first instanceof Map<?, ?> map && map.get("diseaseId") != null) {
                proposed.put("diseaseIds", List.of(map.get("diseaseId")));
            }
        }
        return proposed;
    }

    @SuppressWarnings("unchecked")
    private String w4SuggestionName(Map<String, Object> w4) {
        if (w4 == null || !(w4.get("suggestions") instanceof List<?> suggestions) || suggestions.isEmpty()) {
            return null;
        }
        Object first = suggestions.get(0);
        if (first instanceof Map<?, ?> map) {
            return textValue(map.get("diseaseName"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPrescriptionItems(Map<String, Object> body) {
        Object items = body.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapW5ToPrescriptionItems(Map<String, Object> w5) {
        Object suggestions = w5.get("suggestions");
        if (!(suggestions instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            Object drugId = map.get("drugId");
            if (drugId != null) {
                row.put("drugId", drugId);
            }
            Object drugUsage = map.get("drugUsage");
            if (drugUsage == null) {
                drugUsage = map.get("usage");
            }
            if (drugUsage != null) {
                row.put("drugUsage", drugUsage);
            }
            Object drugNumber = map.get("drugNumber");
            if (drugNumber == null) {
                drugNumber = map.get("quantity");
            }
            if (drugNumber != null) {
                row.put("drugNumber", drugNumber);
            }
            if (!row.isEmpty()) {
                items.add(row);
            }
        }
        return items;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
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
