package com.xikang.physician.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.ai.W2ClinicalContextBuilder;
import com.xikang.physician.service.PhysicianService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class PhysicianCopilotContextBuilder {

    private static final int MAX_FIELD_CHARS = 2000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PhysicianService physicianService;
    private final PhysicianAiPipelineService pipelineService;
    private final W2ClinicalContextBuilder w2ClinicalContextBuilder;

    public PhysicianCopilotContextBuilder(
        PhysicianService physicianService,
        PhysicianAiPipelineService pipelineService,
        W2ClinicalContextBuilder w2ClinicalContextBuilder
    ) {
        this.physicianService = physicianService;
        this.pipelineService = pipelineService;
        this.w2ClinicalContextBuilder = w2ClinicalContextBuilder;
    }

    /**
     * Dify Agent 应用输入变量（与编排指南一致，值均为 String）。
     */
    public Map<String, String> buildAgentInputs(Long registerId) {
        return buildAgentInputs(registerId, null);
    }

    public Map<String, String> buildAgentInputs(Long registerId, Long sessionId) {
        Map<String, Object> patient = physicianService.getPatient(registerId);
        Map<String, Object> record = physicianService.getMedicalRecord(registerId);

        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("register_id", String.valueOf(registerId));
        inputs.put("clinical_context_text", buildClinicalContextText(record, patient));
        inputs.put("patient_name", valueOrEmpty(patient, "realName"));
        inputs.put("visit_state", formatVisitState(patient));
        Long doctorId = com.xikang.physician.context.PhysicianAuthContext.employeeIdOrNull();
        if (doctorId != null) {
            inputs.put("doctor_id", String.valueOf(doctorId));
        }
        if (sessionId != null) {
            inputs.put("session_id", String.valueOf(sessionId));
        }
        try {
            Map<String, Object> w2Context = w2ClinicalContextBuilder.build(registerId);
            inputs.put("clinical_context_json", JSON.writeValueAsString(w2Context));
        } catch (Exception ex) {
            inputs.put("clinical_context_json", "{}");
        }
        return inputs;
    }

    private String buildClinicalContextText(Map<String, Object> record, Map<String, Object> patient) {
        StringJoiner joiner = new StringJoiner("\n\n");
        String consult = formatAiConsult(patient);
        if (!consult.isBlank()) {
            joiner.add(consult);
        }
        String medical = formatMedicalRecord(record);
        if (!medical.isBlank()) {
            joiner.add(medical);
        }
        String preliminary = formatPreliminaryAi(record);
        if (!preliminary.isBlank()) {
            joiner.add(preliminary);
        }
        return joiner.toString().trim();
    }

    private String formatVisitState(Map<String, Object> patient) {
        if (patient == null) {
            return "";
        }
        Object state = patient.get("visitState");
        if (state == null) {
            return "";
        }
        int code;
        try {
            code = Integer.parseInt(String.valueOf(state));
        } catch (NumberFormatException ex) {
            return String.valueOf(state);
        }
        return switch (code) {
            case 1 -> "待接诊";
            case 2 -> "接诊中";
            case 3 -> "已结束";
            case 4 -> "已取消";
            case 5 -> "待检查";
            case 6 -> "检查完成";
            default -> String.valueOf(code);
        };
    }

    private String valueOrEmpty(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object val = map.get(key);
        return val == null ? "" : String.valueOf(val).trim();
    }

    public String build(Long registerId) {
        Map<String, Object> patient = physicianService.getPatient(registerId);
        Map<String, Object> record = physicianService.getMedicalRecord(registerId);
        Map<String, Object> w3Status = safeW3Status(registerId);

        StringJoiner joiner = new StringJoiner("\n\n");
        joiner.add("""
            你是一位经验丰富的临床 AI 助手，协助医生处理当前患者。
            请基于下方上下文回答问题；若信息不足请明确说明，不要捏造未提供的数据。
            回答使用简体中文，条理清晰，必要时给出可操作的临床建议。
            """);

        joiner.add(section("患者基本信息", formatPatient(patient)));
        joiner.add(section("AI 预问诊摘要", formatAiConsult(patient)));
        joiner.add(section("病历与主诉", formatMedicalRecord(record)));
        joiner.add(section("AI 初步诊断", formatPreliminaryAi(record)));
        joiner.add(section("检查检验结果", formatExamResults(registerId)));
        joiner.add(section("W3 结果解读", formatW3(w3Status)));
        joiner.add(section("确诊与处方相关", formatDiagnosisAndRx(record, registerId)));
        joiner.add(agentInstructions());

        return joiner.toString();
    }

    private String agentInstructions() {
        return """
            【Agent 操作能力】
            你是临床 Agent 助手。除回答问题外，可在必要时向医生提议执行以下工作流操作。
            医生需在界面上点击确认后才会真正执行，你不得声称已自动执行。

            可用工作流操作（type 字段，只读推理）：
            - trigger_preliminary_diagnosis：运行初步诊断工作流
            - trigger_w2：运行检查检验推荐
            - trigger_w3：运行检查检验结果 AI 解读
            - trigger_w4：运行门诊确诊推理
            - trigger_w5：运行智能荐药（需已保存确诊病名）

            可用确认提交操作（type 字段，写库，需医生在界面确认）：
            - commit_medical_record：保存病历字段
            - commit_preliminary_diagnosis：保存初步诊断
            - commit_check_requests / commit_inspection_requests / commit_disposal_requests：提交医技申请
            - commit_diagnosis：提交门诊确诊
            - commit_prescription：开立处方
            - commit_archive_visit：归档病历给患者

            触发时机：
            - 仅在医生明确请求或上下文明显缺失且操作能补足信息时提议
            - 每次回复最多附加 2 个操作卡片
            - 写库操作必须先给出草案说明，再用确认卡片等待医生点击

            工作流操作卡片格式（放在回复正文末尾）：
            ```action
            {
              "type": "trigger_preliminary_diagnosis",
              "label": "运行初步诊断",
              "description": "基于当前病历调用初步诊断工作流",
              "reason": "病历已填写，尚无 AI 初步诊断"
            }
            ```

            确认提交卡片格式（写库，需含 payload）：
            ```confirm
            {
              "type": "commit_medical_record",
              "label": "保存病历",
              "description": "将以下修改写入病历",
              "reason": "医生已审核草案",
              "payload": { "readme": "...", "present": "..." }
            }
            ```

            只读数据可通过内置工具获取（病历、患者、检验结果、医技目录等），无需操作卡片。
            """;
    }

    private Map<String, Object> safeW3Status(Long registerId) {
        try {
            return pipelineService.getW3Status(registerId);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String section(String title, String body) {
        if (body == null || body.isBlank()) {
            return "【" + title + "】\n暂无数据";
        }
        return "【" + title + "】\n" + body;
    }

    private String formatPatient(Map<String, Object> patient) {
        if (patient == null || patient.isEmpty()) {
            return "";
        }
        return """
            姓名: %s
            性别: %s
            年龄: %s
            病历号: %s
            就诊状态: %s
            """.formatted(
            value(patient, "realName"),
            value(patient, "gender"),
            value(patient, "age"),
            value(patient, "caseNumber"),
            value(patient, "visitState")
        );
    }

    @SuppressWarnings("unchecked")
    private String formatAiConsult(Map<String, Object> patient) {
        if (patient == null) {
            return "";
        }
        Object summaryObj = patient.get("aiConsultSummary");
        if (!(summaryObj instanceof Map<?, ?> summary)) {
            return patient.get("hasAiConsultation") == Boolean.TRUE ? "已完成 AI 预问诊（摘要未加载）" : "";
        }
        return """
            主诉: %s
            病史摘要: %s
            过敏史: %s
            AI 摘要: %s
            建议检查: %s
            """.formatted(
            truncate(value((Map<String, Object>) summary, "chiefComplaint")),
            truncate(value((Map<String, Object>) summary, "historySummary")),
            truncate(value((Map<String, Object>) summary, "allergySummary")),
            truncate(value((Map<String, Object>) summary, "aiSummary")),
            truncate(value((Map<String, Object>) summary, "suggestedExam"))
        );
    }

    private String formatMedicalRecord(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return "";
        }
        return """
            主诉: %s
            现病史: %s
            现病治疗情况: %s
            既往史: %s
            过敏史: %s
            体格检查: %s
            检查/检验建议: %s
            """.formatted(
            truncate(value(record, "readme")),
            truncate(value(record, "present")),
            truncate(value(record, "presentTreat")),
            truncate(value(record, "history")),
            truncate(value(record, "allergy")),
            truncate(value(record, "physique")),
            truncate(value(record, "proposal"))
        );
    }

    @SuppressWarnings("unchecked")
    private String formatPreliminaryAi(Map<String, Object> record) {
        if (record == null) {
            return "";
        }
        Object metaObj = record.get("preliminaryAiMeta");
        if (!(metaObj instanceof Map<?, ?> meta)) {
            return "";
        }
        Map<String, Object> preliminary = (Map<String, Object>) meta;
        return """
            临床摘要: %s
            初步诊断: %s
            诊断依据: %s
            建议疾病: %s
            红旗征: %s
            """.formatted(
            truncate(value(preliminary, "clinicalSummary")),
            truncate(value(preliminary, "primaryDiagnosis")),
            truncate(value(preliminary, "diagnosisBasis")),
            truncate(String.valueOf(preliminary.getOrDefault("suggestedDiseaseNames", ""))),
            truncate(String.valueOf(preliminary.getOrDefault("redFlags", "")))
        );
    }

    private String formatExamResults(Long registerId) {
        List<Map<String, Object>> checks = physicianService.getCheckResults(registerId);
        List<Map<String, Object>> inspections = physicianService.getInspectionResults(registerId);
        if (checks.isEmpty() && inspections.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : checks) {
            sb.append("- 检查 ").append(value(item, "techName"))
                .append(": ").append(truncate(value(item, "resultText"))).append('\n');
        }
        for (Map<String, Object> item : inspections) {
            sb.append("- 检验 ").append(value(item, "techName"))
                .append(": ").append(truncate(value(item, "resultText"))).append('\n');
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String formatW3(Map<String, Object> w3Status) {
        if (w3Status == null || w3Status.isEmpty()) {
            return "";
        }
        Object output = w3Status.get("w3Output");
        if (!(output instanceof Map<?, ?> w3)) {
            return truncate(String.valueOf(w3Status.getOrDefault("clinicalImpression", "")));
        }
        return """
            临床印象: %s
            综合分析: %s
            非最终诊断声明: %s
            """.formatted(
            truncate(value((Map<String, Object>) w3, "clinicalImpression")),
            truncate(value((Map<String, Object>) w3, "overallAnalysis")),
            truncate(value((Map<String, Object>) w3, "explicitNonDiagnosis"))
        );
    }

    private String formatDiagnosisAndRx(Map<String, Object> record, Long registerId) {
        StringBuilder sb = new StringBuilder();
        if (record != null && !record.isEmpty()) {
            sb.append("确诊病名: ").append(value(record, "diagnosis")).append('\n');
            sb.append("治疗方案: ").append(truncate(value(record, "cure"))).append('\n');
            sb.append("注意事项: ").append(truncate(value(record, "careful"))).append('\n');
        }
        List<Map<String, Object>> suggestions = pipelineService.getDrugSuggestions(registerId);
        if (!suggestions.isEmpty()) {
            sb.append("W5 荐药建议条数: ").append(suggestions.size()).append('\n');
            suggestions.stream().limit(3).forEach(item ->
                sb.append("- ").append(value(item, "drugName"))
                    .append(" / ").append(value(item, "drugUsage"))
                    .append('\n')
            );
        }
        return sb.toString().trim();
    }

    private String value(Map<String, Object> map, String key) {
        if (map == null) {
            return "-";
        }
        Object val = map.get(key);
        if (val == null || Objects.toString(val, "").isBlank()) {
            return "-";
        }
        return Objects.toString(val);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_FIELD_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_FIELD_CHARS) + "…";
    }

    public String toJsonSafe(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
