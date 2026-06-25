package com.xikang.physician.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Warns when Dify YAML mixes up API key and workflow-preliminary switch.
 */
@Component
public class DifyAiConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DifyAiConfigValidator.class);

    private final DifyAiProperties properties;
    private final DifyWorkflowClient difyClient;

    public DifyAiConfigValidator(DifyAiProperties properties, DifyWorkflowClient difyClient) {
        this.properties = properties;
        this.difyClient = difyClient;
    }

    @PostConstruct
    void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        String preliminary = properties.getWorkflowPreliminary();
        if (preliminary != null && preliminary.startsWith("app-")) {
            log.error(
                "配置错误：workflow-preliminary 填了 API Key (app-xxx)。请把该值移到 xikang.ai.dify.api-key / 环境变量 DIFY_API_KEY，"
                    + "workflow-preliminary 仅填 true 作为开关。"
            );
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn(
                "Dify 已启用但 api-key 为空，初步诊断将走 Fallback。请在环境变量设置 DIFY_API_KEY=app-xxx（与 Dify 文档 Bearer 一致）。"
            );
        } else if (difyClient.isPreliminaryEnabled()) {
            log.info(
                "初步诊断：已启用 Dify Workflow（base-url-preliminary={}）",
                properties.resolvePreliminaryBaseUrl()
            );
        } else if (properties.isPreliminaryBaseConfigured()) {
            log.info(
                "初步诊断：未启用 Dify（workflow-preliminary={}，需非空开关且配置 DIFY_API_KEY）",
                properties.getWorkflowPreliminary()
            );
        }
        String w2Switch = properties.getWorkflowW2();
        if (w2Switch != null && w2Switch.startsWith("app-")) {
            log.error(
                "配置错误：workflow-w2 填了 API Key (app-xxx)。请设置 DIFY_WORKFLOW_W2=true，"
                    + "并将 Key 放到 DIFY_API_KEY_W2（推荐）或 DIFY_API_KEY。"
            );
        }
        if (properties.isW2WorkflowSwitchOn() && properties.resolveW2ApiKey().isBlank()) {
            log.warn("workflow-w2 已启用但 api-key-w2 / api-key 为空，W2 将走内置 Fallback。");
        } else if (difyClient.isW2Enabled()) {
            log.info("W2 检查推荐：已启用 Dify Workflow（使用 api-key-w2）");
        } else if (properties.isDifyBaseConfigured()) {
            log.info(
                "W2 检查推荐：未启用 Dify（workflow-w2={}，需为 true/1/yes 且配置 DIFY_API_KEY_W2）",
                properties.getWorkflowW2()
            );
        }
        String w3Switch = properties.getWorkflowW3();
        if (w3Switch != null && w3Switch.startsWith("app-")) {
            log.error(
                "配置错误：workflow-w3 填了 API Key (app-xxx)。请设置 DIFY_WORKFLOW_W3=true，"
                    + "并将 Key 放到 DIFY_API_KEY_W3。"
            );
        }
        if (properties.isW3WorkflowSwitchOn() && properties.resolveW3ApiKey().isBlank()) {
            log.warn("workflow-w3 已启用但 api-key-w3 / DIFY_API_KEY_W3 为空，W3 将走内置 Fallback。");
        } else if (difyClient.isW3Enabled()) {
            log.info("W3 结果解读：已启用 Dify Workflow（使用 api-key-w3）");
        } else if (properties.isDifyBaseConfigured()) {
            log.info(
                "W3 结果解读：未启用 Dify（workflow-w3={}，需为 true/1/yes 且配置 DIFY_API_KEY_W3）",
                properties.getWorkflowW3()
            );
        }
    }
}
