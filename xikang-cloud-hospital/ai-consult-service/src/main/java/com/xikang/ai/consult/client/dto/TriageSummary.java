package com.xikang.ai.consult.client.dto;

/**
 * 导诊小结本地 DTO（ai-consult-service 侧）。
 *
 * <p>这是 ai-triage-service 的 {@code TriageSummary} 在本服务内的镜像副本，
 * 用于 Feign 反序列化，避免本服务直接依赖 triage-service 的包。
 *
 * <p>字段与 triage 侧 {@code com.xikang.ai.triage.dto.TriageSummary} 严格对齐：
 * <ul>
 *   <li>{@code symptomDescription} - 患者主诉原文（预问诊判断"已采集维度"的核心信息源）</li>
 *   <li>{@code recommendDeptName} - 推荐科室名</li>
 *   <li>{@code recommendDeptId} - 推荐科室 ID</li>
 *   <li>{@code riskLevel} - 紧迫度 normal/urgent/critical</li>
 *   <li>{@code aiAnalysisJson} - AI 分析 JSON（possibleConditions / suggestedExaminations 等，仅供模型参考，不对患者复述）</li>
 * </ul>
 */
public record TriageSummary(
        String symptomDescription,
        String recommendDeptName,
        Long recommendDeptId,
        String riskLevel,
        String aiAnalysisJson
) {
}
