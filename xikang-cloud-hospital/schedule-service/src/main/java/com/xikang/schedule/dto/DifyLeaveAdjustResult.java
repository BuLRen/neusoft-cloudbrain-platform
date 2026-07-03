package com.xikang.schedule.dto;

import lombok.Data;

/**
 * Dify 请假替班工作流解析结果 DTO
 * <p>对应 Dify 节点 5 LLM 输出的 JSON 结构（经节点 6 守门员校验后）：
 * <pre>{@code
 * {
 *   "substitute_id": "457",
 *   "substitute_name": "王医生",
 *   "adjust_type": "临时替班",
 *   "reason": "本周无排班且全天空闲，建议接替",
 *   "patient_notification": "已为您改约...",
 *   "source": "ai|fallback|error"
 * }
 * }</pre>
 */
@Data
public class DifyLeaveAdjustResult {

    /** 替班医生 ID（已校验在候选清单内） */
    private Long substitutePhysicianId;

    /** 替班医生姓名 */
    private String substitutePhysicianName;

    /** 调整类型（默认 "临时替班"） */
    private String adjustType;

    /** 选择理由 */
    private String reason;

    /** 给患者的话术（用于短信/通知） */
    private String patientNotification;

    /** 结果来源：ai=LLM 推荐 / fallback=降级首位 / error=解析失败 */
    private String source;

    /** Dify 原始 JSON 字符串（落库到 ai_suggestion 字段供审计） */
    private String rawJson;
}
