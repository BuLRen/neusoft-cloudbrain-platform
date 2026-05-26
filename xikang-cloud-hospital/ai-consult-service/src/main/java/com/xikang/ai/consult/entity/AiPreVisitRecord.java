package com.xikang.ai.consult.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Pre-visit Record Entity - AI预问诊记录
 */
@Data
public class AiPreVisitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;               // 患者ID
    private Long registerId;              // 挂号ID
    private String sessionId;             // 会话ID
    private String chiefComplaint;        // 主诉
    private String presentIllness;        // 现病史
    private String pastHistory;           // 既往史
    private String allergyHistory;        // 过敏史
    private String physicalExamination;    // 体格检查
    private String preliminaryDiagnosis;  // 初步诊断
    private String summary;               // 预问诊摘要
    private String rawConversation;       // 原始对话JSON
    private Integer status;               // 状态：0进行中/1已完成
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
