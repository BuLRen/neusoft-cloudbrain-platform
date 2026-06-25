package com.xikang.ai.consult.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 预问诊结构化总结 —— 由 Spring AI 结构化输出直接填充。
 *
 * 字段命名与 previsit-summary-prompt.st 输出契约保持一致。
 * 注意：ai_consultation_record 表里"现病史"列名是 ai_summary，
 * 这里用 presentIllness 字段对应，service 层会做映射。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreVisitSummary(
        String chiefComplaint,
        String symptomDuration,
        String presentIllness,
        String historySummary,
        String allergySummary,
        String medicationSummary,
        List<String> suggestedExam
) {
}
