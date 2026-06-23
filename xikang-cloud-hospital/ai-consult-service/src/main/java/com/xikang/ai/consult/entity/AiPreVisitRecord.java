package com.xikang.ai.consult.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Pre-visit Record Entity
 * 对应数据库表 ai_consultation_record，一行 = 一轮对话
 * 同一 sessionUuid 的多轮共享，结束时由最后一条的汇总字段填入结构化病历
 */
@Data
public class AiPreVisitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer registerId;              // 挂号ID
    private Integer roundNumber;             // 轮次（从1开始）
    private String aiQuestion;                // AI提问
    private String patientAnswer;             // 患者回答
    private String consultationState;         // in_progress / completed / cancelled
    private String chiefComplaint;            // 主诉（汇总时填）
    private String symptomDuration;           // 症状时长
    private String historySummary;            // 既往史
    private String allergySummary;            // 过敏史
    private String medicationSummary;         // 用药史
    private String aiSummary;                 // AI总结
    private String suggestedExam;             // 建议检查
    private String modelId;                   // 模型ID
    private Integer patientId;                // 患者ID
    private String sessionUuid;               // 会话UUID
    private LocalDateTime creationTime;
    private LocalDateTime completionTime;
    private LocalDateTime updatedAt;
}
