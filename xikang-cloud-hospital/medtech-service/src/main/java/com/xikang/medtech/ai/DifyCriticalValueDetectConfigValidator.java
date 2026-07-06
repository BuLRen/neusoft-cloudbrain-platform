package com.xikang.medtech.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DifyCriticalValueDetectConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DifyCriticalValueDetectConfigValidator.class);

    private final DifyAiProperties properties;
    private final DifyWorkflowClient difyWorkflowClient;

    public DifyCriticalValueDetectConfigValidator(DifyAiProperties properties, DifyWorkflowClient difyWorkflowClient) {
        this.properties = properties;
        this.difyWorkflowClient = difyWorkflowClient;
    }

    @PostConstruct
    public void validate() {
        String workflowSwitch = properties.getWorkflowCriticalValueDetect();
        if (workflowSwitch != null && workflowSwitch.startsWith("app-")) {
            log.warn(
                "配置错误：workflow-critical-value-detect 填了 API Key (app-xxx)。请将其移到 "
                    + "api-key-critical-value-detect / 环境变量 DIFY_API_KEY_CRITICAL_VALUE_DETECT，"
                    + "workflow-critical-value-detect 仅填 true。"
            );
        }

        if (difyWorkflowClient.isCriticalValueDetectEnabled()) {
            log.info(
                "危急值 AI 识别：已启用 Dify Workflow（base-url={}，api-key 已配置，入参 tech_code/result_payload）",
                properties.getBaseUrl()
            );
        } else if (isSwitchOn(workflowSwitch) && properties.resolveCriticalValueDetectApiKey().isBlank()) {
            log.warn(
                "危急值 AI 识别：开关已开但 API Key 未配置，将仅走规则引擎。请在 .env 填写 "
                    + "DIFY_API_KEY_CRITICAL_VALUE_DETECT=app-xxx 并重启 medtech-service。"
            );
        } else {
            log.info("危急值 AI 识别：未启用 Dify，仅使用规则引擎（critical_value_rule）。");
        }
    }

    private static boolean isSwitchOn(String switchValue) {
        if (switchValue == null || switchValue.isBlank()) {
            return false;
        }
        String value = switchValue.trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }
}
