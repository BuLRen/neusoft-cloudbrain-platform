package com.xikang.ai.triage.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Triage Record Entity - AI导诊记录
 */
@Data
public class AiTriageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;               // 患者ID
    private String sessionId;            // 会话ID
    private String symptoms;             // 症状描述（文本）
    private String symptomsJson;         // 症状JSON
    private String conversationHistory;  // 对话历史JSON
    private Long recommendedDepartmentId; // 推荐科室ID
    private String recommendedDepartment; // 推荐科室名称
    private Long recommendedPhysicianId;  // 推荐医生ID
    private String recommendedPhysicianName; // 推荐医生姓名
    private String riskLevel;           // 风险等级：low/medium/high
    private String aiAnalysis;           // AI分析结果JSON
    private String possibleConditions;   // 可能疾病JSON
    private String suggestedExaminations; // 建议检查JSON
    private Integer status;              // 状态：0进行中/1已完成
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
