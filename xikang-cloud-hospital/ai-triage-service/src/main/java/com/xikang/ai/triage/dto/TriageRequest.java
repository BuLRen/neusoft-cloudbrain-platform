package com.xikang.ai.triage.dto;

/**
 * AI 导诊请求入参。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@code symptoms}     —— 患者主诉/症状描述（必填）</li>
 *   <li>{@code patientId}    —— 患者 ID（可选但推荐传入；用于挂号后回填 register_id 定位导诊记录）</li>
 *   <li>{@code patientName}  —— 患者姓名（可选，默认"匿名患者"）</li>
 *   <li>{@code patientAge}   —— 患者年龄（可选）</li>
 *   <li>{@code patientGender} —— 患者性别（可选，"男"/"女"）</li>
 *   <li>{@code sessionId}    —— 前端会话 ID（可选，不传则服务端生成）</li>
 *   <li>{@code registerId}   —— 挂号 ID（可选；导诊常发生在挂号前，故允许为空）</li>
 * </ul>
 */
public record TriageRequest(
        String symptoms,
        Long patientId,
        String patientName,
        Integer patientAge,
        String patientGender,
        String sessionId,
        Integer registerId
) {
}
