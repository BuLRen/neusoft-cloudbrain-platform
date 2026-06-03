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

    public DifyAiConfigValidator(DifyAiProperties properties) {
        this.properties = properties;
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
        }
    }
}
