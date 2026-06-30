package com.xikang.physician.copilot;

import com.xikang.physician.ai.PhysicianAiPipelineService;
import com.xikang.physician.service.PhysicianService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PhysicianCopilotTools {

    private static final ThreadLocal<Long> REGISTER_ID = new ThreadLocal<>();

    private final PhysicianAiPipelineService pipelineService;
    private final PhysicianService physicianService;
    private final PhysicianCopilotContextBuilder contextBuilder;

    public PhysicianCopilotTools(
        PhysicianAiPipelineService pipelineService,
        PhysicianService physicianService,
        PhysicianCopilotContextBuilder contextBuilder
    ) {
        this.pipelineService = pipelineService;
        this.physicianService = physicianService;
        this.contextBuilder = contextBuilder;
    }

    public static void bindRegisterId(Long registerId) {
        REGISTER_ID.set(registerId);
    }

    public static void clearRegisterId() {
        REGISTER_ID.remove();
    }

    private Long currentRegisterId() {
        Long registerId = REGISTER_ID.get();
        if (registerId == null) {
            throw new IllegalStateException("当前会话未绑定就诊号");
        }
        return registerId;
    }

    @Tool(description = "为当前患者触发 W2 检查检验推荐，返回推荐项目与初步判断")
    public String triggerW2Recommendations() {
        Long registerId = currentRegisterId();
        Map<String, Object> result = pipelineService.runW2(registerId);
        return contextBuilder.toJsonSafe(result);
    }

    @Tool(description = "为当前患者触发 W4 门诊确诊建议，返回鉴别诊断与概率")
    public String triggerW4Diagnosis() {
        Long registerId = currentRegisterId();
        Map<String, Object> result = pipelineService.runW4(registerId);
        return contextBuilder.toJsonSafe(result);
    }

    @Tool(description = "为当前患者触发 W5 智能荐药，返回荐药方案（需已保存确诊病名）")
    public String triggerW5DrugRecommendation() {
        Long registerId = currentRegisterId();
        Map<String, Object> result = pipelineService.runW5(registerId);
        return contextBuilder.toJsonSafe(result);
    }

    @Tool(description = "获取当前患者检查检验结果摘要")
    public String getLabResultsSummary() {
        Long registerId = currentRegisterId();
        List<Map<String, Object>> checks = physicianService.getCheckResults(registerId);
        List<Map<String, Object>> inspections = physicianService.getInspectionResults(registerId);
        return contextBuilder.toJsonSafe(Map.of(
            "checkResults", checks,
            "inspectionResults", inspections
        ));
    }

    @Tool(description = "获取当前患者病历与初步诊断文字摘要")
    public String getMedicalRecordSummary() {
        Long registerId = currentRegisterId();
        Map<String, Object> record = physicianService.getMedicalRecord(registerId);
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
