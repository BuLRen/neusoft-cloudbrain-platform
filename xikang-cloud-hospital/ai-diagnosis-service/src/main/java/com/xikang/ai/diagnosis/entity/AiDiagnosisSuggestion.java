package com.xikang.ai.diagnosis.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI Diagnosis Suggestion Entity - AI诊断建议
 */
@Data
public class AiDiagnosisSuggestion implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long patientId;               // 患者ID
    private Long registerId;              // 挂号ID
    private String requestType;           // 请求类型：check/inspection/diagnosis
    private Long requestId;               // 关联申请ID
    private String resultType;            // 结果类型：异常指标/风险等级/分析报告
    private String abnormalIndicators;    // 异常指标JSON
    private String riskLevel;             // 风险等级：low/medium/high
    private String analysisReport;       // 分析报告
    private String suggestions;           // 建议JSON
    private Integer status;               // 状态：0生成中/1成功/2失败
    private String errorMessage;          // 错误信息
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
