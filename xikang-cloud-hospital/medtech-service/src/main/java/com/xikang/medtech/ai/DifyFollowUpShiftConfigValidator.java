package com.xikang.medtech.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DifyFollowUpShiftConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DifyFollowUpShiftConfigValidator.class);

    private final DifyAiProperties properties;

    public DifyFollowUpShiftConfigValidator(DifyAiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        String workflowSwitch = properties.getWorkflowFollowUpShiftSchedule();
        if (workflowSwitch != null && workflowSwitch.startsWith("app-")) {
            log.warn(
                "配置错误：workflow-follow-up-shift-schedule 填了 API Key (app-xxx)。"
                    + "请设置 DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE=true，"
                    + "并将 Key 放到 DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE。"
            );
        }

        if (properties.isFollowUpShiftScheduleEnabled()) {
            log.info(
                "随访月度排班：已启用 Dify Workflow（base-url={}，api-key 已配置，End 输出 validated_shifts_json）",
                properties.getBaseUrl()
            );
        } else {
            log.warn(
                "随访月度排班：未启用 Dify，管理端 AI 生成将走内置规则排班。请确认 DIFY_ENABLED=true、"
                    + "DIFY_BASE_URL、DIFY_API_KEY_FOLLOW_UP_SHIFT_SCHEDULE 以及 "
                    + "DIFY_WORKFLOW_FOLLOW_UP_SHIFT_SCHEDULE=true。"
            );
        }

        String enqueueSwitch = properties.getWorkflowFollowUpEnqueue();
        if (enqueueSwitch != null && enqueueSwitch.startsWith("app-")) {
            log.warn(
                "配置错误：workflow-follow-up-enqueue 填了 API Key (app-xxx)。"
                    + "请设置 DIFY_WORKFLOW_FOLLOW_UP_ENQUEUE=true，"
                    + "并将 Key 放到 DIFY_API_KEY_FOLLOW_UP_ENQUEUE。"
            );
        }

        if (properties.isFollowUpEnqueueEnabled()) {
            log.info(
                "随访单患者入队：已启用 Dify Workflow（base-url={}，End 输出 enqueue_result_json）",
                properties.getBaseUrl()
            );
        } else {
            log.warn(
                "随访单患者入队：未启用 Dify，看诊结束入队将走规则降级。请配置 "
                    + "DIFY_API_KEY_FOLLOW_UP_ENQUEUE 与 DIFY_WORKFLOW_FOLLOW_UP_ENQUEUE=true。"
            );
        }
    }
}
