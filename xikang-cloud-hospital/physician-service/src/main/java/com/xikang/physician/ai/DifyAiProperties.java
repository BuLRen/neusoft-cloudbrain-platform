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
    private String ctInferenceUrl = "";
    private PreliminaryOutputKeys preliminaryOutputKeys = new PreliminaryOutputKeys();

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

    public String getCtInferenceUrl() {
        return ctInferenceUrl;
    }

    public void setCtInferenceUrl(String ctInferenceUrl) {
        this.ctInferenceUrl = ctInferenceUrl;
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
}
