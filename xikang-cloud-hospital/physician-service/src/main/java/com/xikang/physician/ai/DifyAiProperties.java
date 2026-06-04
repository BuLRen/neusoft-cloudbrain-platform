package com.xikang.physician.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xikang.ai.dify")
public class DifyAiProperties {

    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String workflowW1 = "";
    private String workflowW2 = "";
    private String workflowW2b = "";
    private String workflowW3 = "";
    private String workflowW4 = "";
    private String workflowPreliminary = "";
    /** W2 检查推荐 Workflow App 的 API Key（app-xxx）；为空时回退 api-key */
    private String apiKeyW2 = "";
    private String ctInferenceUrl = "";
    /** Dify blocking 工作流读取超时（毫秒），慢模型建议 300000（5 分钟） */
    private int readTimeoutMs = 300_000;
    /** 连接 Dify 超时（毫秒） */
    private int connectTimeoutMs = 30_000;
    private PreliminaryOutputKeys preliminaryOutputKeys = new PreliminaryOutputKeys();
    private W2OutputKeys w2OutputKeys = new W2OutputKeys();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWorkflowW1() {
        return workflowW1;
    }

    public void setWorkflowW1(String workflowW1) {
        this.workflowW1 = workflowW1;
    }

    public String getWorkflowW2() {
        return workflowW2;
    }

    public void setWorkflowW2(String workflowW2) {
        this.workflowW2 = workflowW2;
    }

    public String getWorkflowW2b() {
        return workflowW2b;
    }

    public void setWorkflowW2b(String workflowW2b) {
        this.workflowW2b = workflowW2b;
    }

    public String getWorkflowW3() {
        return workflowW3;
    }

    public void setWorkflowW3(String workflowW3) {
        this.workflowW3 = workflowW3;
    }

    public String getWorkflowW4() {
        return workflowW4;
    }

    public void setWorkflowW4(String workflowW4) {
        this.workflowW4 = workflowW4;
    }

    public String getWorkflowPreliminary() {
        return workflowPreliminary;
    }

    public void setWorkflowPreliminary(String workflowPreliminary) {
        this.workflowPreliminary = workflowPreliminary;
    }

    public String getApiKeyW2() {
        return apiKeyW2;
    }

    public void setApiKeyW2(String apiKeyW2) {
        this.apiKeyW2 = apiKeyW2;
    }

    public W2OutputKeys getW2OutputKeys() {
        return w2OutputKeys;
    }

    public void setW2OutputKeys(W2OutputKeys w2OutputKeys) {
        this.w2OutputKeys = w2OutputKeys == null ? new W2OutputKeys() : w2OutputKeys;
    }

    /**
     * W2 开关：仅 {@code true}/{@code 1}/{@code yes}/{@code on} 视为启用（勿将 app-xxx 写在此项）。
     */
    public boolean isW2WorkflowSwitchOn() {
        if (workflowW2 == null || workflowW2.isBlank()) {
            return false;
        }
        String value = workflowW2.trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    public boolean isDifyBaseConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public String resolveW2ApiKey() {
        if (apiKeyW2 != null && !apiKeyW2.isBlank()) {
            return apiKeyW2.trim();
        }
        return apiKey == null ? "" : apiKey.trim();
    }

    public String getCtInferenceUrl() {
        return ctInferenceUrl;
    }

    public void setCtInferenceUrl(String ctInferenceUrl) {
        this.ctInferenceUrl = ctInferenceUrl;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public PreliminaryOutputKeys getPreliminaryOutputKeys() {
        return preliminaryOutputKeys;
    }

    public void setPreliminaryOutputKeys(PreliminaryOutputKeys preliminaryOutputKeys) {
        this.preliminaryOutputKeys = preliminaryOutputKeys == null ? new PreliminaryOutputKeys() : preliminaryOutputKeys;
    }

    public static class PreliminaryOutputKeys {

        /** Dify 结束节点根变量，默认 output_structured */
        private String outputStructured = "output_structured";
        /** 嵌套内诊断正文，默认 answer */
        private String diagnosisText = "answer";
        private String diagnosisBasis = "";
        private String confidence = "";
        /** 嵌套内疾病列表，默认 diseaseDetail */
        private String suggestedDiseases = "diseaseDetail";
        private boolean parseJsonFromText = true;

        public String getOutputStructured() {
            return outputStructured;
        }

        public void setOutputStructured(String outputStructured) {
            this.outputStructured = outputStructured;
        }

        public String getDiagnosisText() {
            return diagnosisText;
        }

        public void setDiagnosisText(String diagnosisText) {
            this.diagnosisText = diagnosisText;
        }

        public String getDiagnosisBasis() {
            return diagnosisBasis;
        }

        public void setDiagnosisBasis(String diagnosisBasis) {
            this.diagnosisBasis = diagnosisBasis;
        }

        public String getConfidence() {
            return confidence;
        }

        public void setConfidence(String confidence) {
            this.confidence = confidence;
        }

        public String getSuggestedDiseases() {
            return suggestedDiseases;
        }

        public void setSuggestedDiseases(String suggestedDiseases) {
            this.suggestedDiseases = suggestedDiseases;
        }

        public boolean isParseJsonFromText() {
            return parseJsonFromText;
        }

        public void setParseJsonFromText(boolean parseJsonFromText) {
            this.parseJsonFromText = parseJsonFromText;
        }
    }

    public static class W2OutputKeys {

        /** Dify 结束节点根变量；为空则自动尝试 output_structured / 平铺字段 */
        private String outputRoot = "";

        public String getOutputRoot() {
            return outputRoot;
        }

        public void setOutputRoot(String outputRoot) {
            this.outputRoot = outputRoot;
        }
    }
}
