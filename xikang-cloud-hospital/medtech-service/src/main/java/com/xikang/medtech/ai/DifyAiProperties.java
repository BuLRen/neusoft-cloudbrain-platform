package com.xikang.medtech.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xikang.ai.dify")
public class DifyAiProperties {

    private boolean enabled;
    private String baseUrl = "";
    private String apiKeyCheckSimulate = "";
    private String workflowCheckSimulate = "";
    private String apiKeyFollowUpCaseSummary = "";
    private String workflowFollowUpCaseSummary = "";
    private String apiKeyFollowUpMedicalChat = "";
    private String workflowFollowUpMedicalChat = "";
    private String apiKeyFollowUpShiftSchedule = "";
    private String workflowFollowUpShiftSchedule = "";
    private String apiKeyFollowUpEnqueue = "";
    private String workflowFollowUpEnqueue = "";
    private String apiKeyCriticalValueDetect = "";
    private String workflowCriticalValueDetect = "";
    private String ctInferenceUrl = "";
    private int readTimeoutMs = 300_000;
    private int connectTimeoutMs = 30_000;
    private CheckSimulateOutputKeys checkSimulateOutputKeys = new CheckSimulateOutputKeys();

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

    public String getApiKeyCheckSimulate() {
        return apiKeyCheckSimulate;
    }

    public void setApiKeyCheckSimulate(String apiKeyCheckSimulate) {
        this.apiKeyCheckSimulate = apiKeyCheckSimulate;
    }

    public String getWorkflowCheckSimulate() {
        return workflowCheckSimulate;
    }

    public void setWorkflowCheckSimulate(String workflowCheckSimulate) {
        this.workflowCheckSimulate = workflowCheckSimulate;
    }

    public String getApiKeyFollowUpCaseSummary() {
        return apiKeyFollowUpCaseSummary;
    }

    public void setApiKeyFollowUpCaseSummary(String apiKeyFollowUpCaseSummary) {
        this.apiKeyFollowUpCaseSummary = apiKeyFollowUpCaseSummary;
    }

    public String getWorkflowFollowUpCaseSummary() {
        return workflowFollowUpCaseSummary;
    }

    public void setWorkflowFollowUpCaseSummary(String workflowFollowUpCaseSummary) {
        this.workflowFollowUpCaseSummary = workflowFollowUpCaseSummary;
    }

    public String getApiKeyFollowUpMedicalChat() {
        return apiKeyFollowUpMedicalChat;
    }

    public void setApiKeyFollowUpMedicalChat(String apiKeyFollowUpMedicalChat) {
        this.apiKeyFollowUpMedicalChat = apiKeyFollowUpMedicalChat;
    }

    public String getWorkflowFollowUpMedicalChat() {
        return workflowFollowUpMedicalChat;
    }

    public void setWorkflowFollowUpMedicalChat(String workflowFollowUpMedicalChat) {
        this.workflowFollowUpMedicalChat = workflowFollowUpMedicalChat;
    }

    public String getApiKeyFollowUpShiftSchedule() {
        return apiKeyFollowUpShiftSchedule;
    }

    public void setApiKeyFollowUpShiftSchedule(String apiKeyFollowUpShiftSchedule) {
        this.apiKeyFollowUpShiftSchedule = apiKeyFollowUpShiftSchedule;
    }

    public String getWorkflowFollowUpShiftSchedule() {
        return workflowFollowUpShiftSchedule;
    }

    public void setWorkflowFollowUpShiftSchedule(String workflowFollowUpShiftSchedule) {
        this.workflowFollowUpShiftSchedule = workflowFollowUpShiftSchedule;
    }

    public String getApiKeyFollowUpEnqueue() {
        return apiKeyFollowUpEnqueue;
    }

    public void setApiKeyFollowUpEnqueue(String apiKeyFollowUpEnqueue) {
        this.apiKeyFollowUpEnqueue = apiKeyFollowUpEnqueue;
    }

    public String getWorkflowFollowUpEnqueue() {
        return workflowFollowUpEnqueue;
    }

    public void setWorkflowFollowUpEnqueue(String workflowFollowUpEnqueue) {
        this.workflowFollowUpEnqueue = workflowFollowUpEnqueue;
    }

    public String getApiKeyCriticalValueDetect() {
        return apiKeyCriticalValueDetect;
    }

    public void setApiKeyCriticalValueDetect(String apiKeyCriticalValueDetect) {
        this.apiKeyCriticalValueDetect = apiKeyCriticalValueDetect;
    }

    public String getWorkflowCriticalValueDetect() {
        return workflowCriticalValueDetect;
    }

    public void setWorkflowCriticalValueDetect(String workflowCriticalValueDetect) {
        this.workflowCriticalValueDetect = workflowCriticalValueDetect;
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

    public CheckSimulateOutputKeys getCheckSimulateOutputKeys() {
        return checkSimulateOutputKeys;
    }

    public void setCheckSimulateOutputKeys(CheckSimulateOutputKeys checkSimulateOutputKeys) {
        this.checkSimulateOutputKeys = checkSimulateOutputKeys == null
            ? new CheckSimulateOutputKeys()
            : checkSimulateOutputKeys;
    }

    public boolean isDifyBaseConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public boolean isCheckSimulateWorkflowSwitchOn() {
        if (workflowCheckSimulate == null || workflowCheckSimulate.isBlank()) {
            return false;
        }
        String value = workflowCheckSimulate.trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    public String resolveCheckSimulateApiKey() {
        return apiKeyCheckSimulate == null ? "" : apiKeyCheckSimulate.trim();
    }

    public boolean isFollowUpCaseSummaryEnabled() {
        return isDifyBaseConfigured()
            && isWorkflowSwitchOn(workflowFollowUpCaseSummary)
            && !resolveFollowUpCaseSummaryApiKey().isBlank();
    }

    public boolean isFollowUpMedicalChatEnabled() {
        return isDifyBaseConfigured()
            && isWorkflowSwitchOn(workflowFollowUpMedicalChat)
            && !resolveFollowUpMedicalChatApiKey().isBlank();
    }

    public boolean isCriticalValueDetectEnabled() {
        return isDifyBaseConfigured()
            && isWorkflowSwitchOn(workflowCriticalValueDetect)
            && !resolveCriticalValueDetectApiKey().isBlank();
    }

    public String resolveFollowUpCaseSummaryApiKey() {
        return apiKeyFollowUpCaseSummary == null ? "" : apiKeyFollowUpCaseSummary.trim();
    }

    public String resolveFollowUpMedicalChatApiKey() {
        return apiKeyFollowUpMedicalChat == null ? "" : apiKeyFollowUpMedicalChat.trim();
    }

    public boolean isFollowUpShiftScheduleEnabled() {
        return isDifyBaseConfigured()
            && isWorkflowSwitchOn(workflowFollowUpShiftSchedule)
            && !resolveFollowUpShiftScheduleApiKey().isBlank();
    }

    public String resolveFollowUpShiftScheduleApiKey() {
        return apiKeyFollowUpShiftSchedule == null ? "" : apiKeyFollowUpShiftSchedule.trim();
    }

    public String describeFollowUpShiftScheduleDisabledReason() {
        if (!enabled) {
            return "Dify 总开关未启用";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return "Dify base-url 未配置";
        }
        if (!isWorkflowSwitchOn(workflowFollowUpShiftSchedule)) {
            return "workflow-follow-up-shift-schedule 未开启";
        }
        if (resolveFollowUpShiftScheduleApiKey().isBlank()) {
            return "api-key-follow-up-shift-schedule 未配置";
        }
        return "";
    }

    public boolean isFollowUpEnqueueEnabled() {
        return isDifyBaseConfigured()
            && isWorkflowSwitchOn(workflowFollowUpEnqueue)
            && !resolveFollowUpEnqueueApiKey().isBlank();
    }

    public String resolveFollowUpEnqueueApiKey() {
        return apiKeyFollowUpEnqueue == null ? "" : apiKeyFollowUpEnqueue.trim();
    }

    public String resolveCriticalValueDetectApiKey() {
        return apiKeyCriticalValueDetect == null ? "" : apiKeyCriticalValueDetect.trim();
    }

    private boolean isWorkflowSwitchOn(String switchValue) {
        if (switchValue == null || switchValue.isBlank()) {
            return false;
        }
        String value = switchValue.trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value);
    }

    public static class CheckSimulateOutputKeys {

        private String valuesRoot = "simulatedValues";
        private String resultText = "resultText";
        private String structuredOutput = "structured_output";

        public String getValuesRoot() {
            return valuesRoot;
        }

        public void setValuesRoot(String valuesRoot) {
            this.valuesRoot = valuesRoot;
        }

        public String getResultText() {
            return resultText;
        }

        public void setResultText(String resultText) {
            this.resultText = resultText;
        }

        public String getStructuredOutput() {
            return structuredOutput;
        }

        public void setStructuredOutput(String structuredOutput) {
            this.structuredOutput = structuredOutput;
        }
    }
}
