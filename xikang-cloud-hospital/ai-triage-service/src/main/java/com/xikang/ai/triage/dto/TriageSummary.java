package com.xikang.ai.triage.dto;

/**
 * AI 导诊小结 —— 按 {@code registerId} 反查导诊记录后返回给下游服务（如预问诊）的精简视图。
 *
 * <p>本 DTO 是"导诊 → 预问诊"上下文串联的契约：预问诊服务据此把导诊阶段已采集到的
 * 症状与推荐科室注入到自己的 prompt 中，避免患者重复描述病情。
 */
public record TriageSummary(
        String symptomDescription,     // 患者主诉原文
        String recommendDeptName,      // 推荐科室名
        Long recommendDeptId,          // 推荐科室 ID
        String riskLevel,              // normal/urgent/critical
        String aiAnalysisJson          // 完整 AI 分析 JSON（含 possibleConditions / redFlags 等）
) {
}
