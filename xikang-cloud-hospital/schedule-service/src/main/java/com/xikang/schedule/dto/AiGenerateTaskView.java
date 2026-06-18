package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 排班生成异步任务视图。
 * 前端通过 GET /plan/ai-generate/active 拉取，用于排班页横幅展示。
 */
@Data
public class AiGenerateTaskView implements Serializable {

    /** running | success | failed | cancelled */
    private String status;

    /** validating | loading_doctors | calling_coze | parsing_ai | saving_plan */
    private String stage;

    /** 0~100 百分比 */
    private Integer percent;

    /** 当前阶段中文提示 */
    private String message;

    /** 成功时填入 */
    private Long planId;

    /** 失败时填入 */
    private String error;

    private Long createdAt;
    private Long updatedAt;
}
