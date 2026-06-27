package com.xikang.ai.triage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 导诊结构化结果 —— 由 Spring AI 结构化输出（{@code ChatClient.entity()}）直接填充。
 *
 * <p>字段命名与 {@code prompts/triage-prompt.st} 的输出契约保持一致；
 * Controller 层据此组装兼容旧前端的响应 Map。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriageResult(
        String urgencyLevel,                // I/II/III/IV/V
        String urgencyAdvice,
        String recommendedDepartment,
        Long recommendedDepartmentId,
        String departmentReason,
        Long recommendedRegistLevelId,      // 1=普通号 2=专家号 3=主任医师号
        String registLevelReason,
        List<String> alternativeDepartments,
        String confidenceLevel,             // high/medium/low
        String confidenceReason,
        List<String> redFlags,
        String selfCareAdvice,
        AiAnalysis aiAnalysis,
        // 领域护栏：用户输入与医疗无关时为 true；正常分诊结果不带这个字段或为 false
        Boolean isOutOfScope,
        // 话题外时给用户的引导语（如"请告诉我您的症状..."）
        String outOfScopeMessage
) {

    /**
     * AI 分析子结构：可能疾病 / 建议检查。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiAnalysis(
            List<String> possibleConditions,
            List<String> suggestedExaminations
    ) {
    }
}
