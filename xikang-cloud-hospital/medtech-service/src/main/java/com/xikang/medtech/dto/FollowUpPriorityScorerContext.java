package com.xikang.medtech.dto;

import java.util.ArrayList;
import java.util.List;

public class FollowUpPriorityScorerContext {

    private Integer departmentId;
    private String departmentName;
    private String diagnosisText;
    private List<String> diseaseNames = List.of();
    private int distinctDrugCount;
    private int highAbnormalCount;
    private int anyAbnormalCount;

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDiagnosisText() {
        return diagnosisText;
    }

    public void setDiagnosisText(String diagnosisText) {
        this.diagnosisText = diagnosisText;
    }

    public List<String> getDiseaseNames() {
        return diseaseNames;
    }

    public void setDiseaseNames(List<String> diseaseNames) {
        this.diseaseNames = diseaseNames == null ? List.of() : List.copyOf(diseaseNames);
    }

    public int getDistinctDrugCount() {
        return distinctDrugCount;
    }

    public void setDistinctDrugCount(int distinctDrugCount) {
        this.distinctDrugCount = distinctDrugCount;
    }

    public int getHighAbnormalCount() {
        return highAbnormalCount;
    }

    public void setHighAbnormalCount(int highAbnormalCount) {
        this.highAbnormalCount = highAbnormalCount;
    }

    public int getAnyAbnormalCount() {
        return anyAbnormalCount;
    }

    public void setAnyAbnormalCount(int anyAbnormalCount) {
        this.anyAbnormalCount = anyAbnormalCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final FollowUpPriorityScorerContext ctx = new FollowUpPriorityScorerContext();

        public Builder departmentId(Integer departmentId) {
            ctx.departmentId = departmentId;
            return this;
        }

        public Builder departmentName(String departmentName) {
            ctx.departmentName = departmentName;
            return this;
        }

        public Builder diagnosisText(String diagnosisText) {
            ctx.diagnosisText = diagnosisText;
            return this;
        }

        public Builder diseaseNames(List<String> diseaseNames) {
            ctx.diseaseNames = diseaseNames == null ? List.of() : new ArrayList<>(diseaseNames);
            return this;
        }

        public Builder distinctDrugCount(int distinctDrugCount) {
            ctx.distinctDrugCount = distinctDrugCount;
            return this;
        }

        public Builder highAbnormalCount(int highAbnormalCount) {
            ctx.highAbnormalCount = highAbnormalCount;
            return this;
        }

        public Builder anyAbnormalCount(int anyAbnormalCount) {
            ctx.anyAbnormalCount = anyAbnormalCount;
            return this;
        }

        public FollowUpPriorityScorerContext build() {
            return ctx;
        }
    }
}
