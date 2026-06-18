package com.xikang.medtech.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DifyCheckSimulateConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DifyCheckSimulateConfigValidator.class);

    private final DifyAiProperties properties;
    private final DifyWorkflowClient difyWorkflowClient;

    public DifyCheckSimulateConfigValidator(DifyAiProperties properties, DifyWorkflowClient difyWorkflowClient) {
        this.properties = properties;
        this.difyWorkflowClient = difyWorkflowClient;
    }

    @PostConstruct
    public void validate() {
        String workflowSwitch = properties.getWorkflowCheckSimulate();
        if (workflowSwitch != null && workflowSwitch.startsWith("app-")) {
            log.warn(
                "配置错误：workflow-check-simulate 填了 API Key (app-xxx)。请将其移到 api-key-check-simulate / "
                    + "环境变量 DIFY_API_KEY_CHECK_SIMULATE 或 DIFY_API_KEY_SIMULATE，workflow-check-simulate 仅填 true。"
            );
        }

        if (difyWorkflowClient.isCheckSimulateEnabled()) {
            log.info(
                "模拟检查：已启用 Dify Workflow（base-url={}，api-key 已配置）",
                properties.getBaseUrl()
            );
        } else {
            log.warn(
                "模拟检查：未启用 Dify，将使用内置 Fallback。请确认 DIFY_ENABLED=true、DIFY_BASE_URL、"
                    + "DIFY_API_KEY_CHECK_SIMULATE（或 DIFY_API_KEY_SIMULATE）以及 DIFY_WORKFLOW_CHECK_SIMULATE=true。"
            );
        }
    }
}
