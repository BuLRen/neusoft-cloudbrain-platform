package com.xikang.physician.copilot;

import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.client.PhysicianClinicalClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PhysicianCopilotTools {

    private static final ThreadLocal<Long> REGISTER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<Map<String, Object>>> TOOL_CALLS = new ThreadLocal<>();

    private final PhysicianAiPipelineService pipelineService;
    private final PhysicianClinicalClient physicianClinicalClient;
    private final PhysicianCopilotContextBuilder contextBuilder;

    public PhysicianCopilotTools(
        PhysicianAiPipelineService pipelineService,
        PhysicianClinicalClient physicianClinicalClient,
        PhysicianCopilotContextBuilder contextBuilder
    ) {
        this.pipelineService = pipelineService;
        this.physicianClinicalClient = physicianClinicalClient;
        this.contextBuilder = contextBuilder;
    }

    public static void bindRegisterId(Long registerId) {
        REGISTER_ID.set(registerId);
        TOOL_CALLS.set(new ArrayList<>());
    }

    public static void clearRegisterId() {
        REGISTER_ID.remove();
        TOOL_CALLS.remove();
    }

    public static List<Map<String, Object>> drainToolCalls() {
        List<Map<String, Object>> calls = TOOL_CALLS.get();
        TOOL_CALLS.remove();
        return calls == null ? List.of() : List.copyOf(calls);
    }

    private Long currentRegisterId() {
        Long registerId = REGISTER_ID.get();
        if (registerId == null) {
            throw new IllegalStateException("当前会话未绑定就诊号");
        }
        return registerId;
    }

    private void recordToolCall(String name) {
        List<Map<String, Object>> calls = TOOL_CALLS.get();
        if (calls == null) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("at", System.currentTimeMillis());
        calls.add(entry);
    }

    @Tool(description = "获取当前患者检查检验结果摘要")
    public String getLabResultsSummary() {
        recordToolCall("getLabResultsSummary");
        Long registerId = currentRegisterId();
        List<Map<String, Object>> checks = physicianClinicalClient.getCheckResults(registerId);
        List<Map<String, Object>> inspections = physicianClinicalClient.getInspectionResults(registerId);
        return contextBuilder.toJsonSafe(Map.of(
            "checkResults", checks,
            "inspectionResults", inspections
        ));
    }

    @Tool(description = "获取当前患者病历与初步诊断文字摘要")
    public String getMedicalRecordSummary() {
        recordToolCall("getMedicalRecordSummary");
        Long registerId = currentRegisterId();
        Map<String, Object> record = physicianClinicalClient.getMedicalRecord(registerId);
        if (record == null || record.isEmpty()) {
            return "暂无病历记录";
        }
        String summary = """
            主诉:%s; 现病史:%s; 既往史:%s; 过敏史:%s; 体格检查:%s; 初步诊断AI:%s
            """.formatted(
            text(record.get("readme")),
            text(record.get("present")),
            text(record.get("history")),
            text(record.get("allergy")),
            text(record.get("physique")),
            text(record.get("preliminaryAiMeta"))
        );
        return summary.trim();
    }

    @Tool(description = "获取当前患者 W3 检验检查 AI 解读状态与摘要")
    public String getW3AnalysisSummary() {
        recordToolCall("getW3AnalysisSummary");
        Long registerId = currentRegisterId();
        Map<String, Object> status = pipelineService.getW3Status(registerId);
        return contextBuilder.toJsonSafe(status);
    }

    private String text(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = Objects.toString(value);
        return raw.length() > 500 ? raw.substring(0, 500) + "…" : raw;
    }
}
