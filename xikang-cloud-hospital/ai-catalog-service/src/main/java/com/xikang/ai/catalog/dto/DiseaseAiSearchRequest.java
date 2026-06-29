package com.xikang.ai.catalog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for Dify W4 disease catalog search.
 */
public class DiseaseAiSearchRequest {

    private List<String> diseaseKeywords = new ArrayList<>();
    private List<String> symptomKeywords = new ArrayList<>();
    private List<String> icdPrefixes = new ArrayList<>();
    private List<String> categoryKeywords = new ArrayList<>();
    private List<String> negativeKeywords = new ArrayList<>();
    private Integer limit = 80;

    public List<String> getDiseaseKeywords() {
        return diseaseKeywords;
    }

    public void setDiseaseKeywords(List<String> diseaseKeywords) {
        this.diseaseKeywords = diseaseKeywords;
    }

    public List<String> getSymptomKeywords() {
        return symptomKeywords;
    }

    public void setSymptomKeywords(List<String> symptomKeywords) {
        this.symptomKeywords = symptomKeywords;
    }

    public List<String> getIcdPrefixes() {
        return icdPrefixes;
    }

    public void setIcdPrefixes(List<String> icdPrefixes) {
        this.icdPrefixes = icdPrefixes;
    }

    public List<String> getCategoryKeywords() {
        return categoryKeywords;
    }

    public void setCategoryKeywords(List<String> categoryKeywords) {
        this.categoryKeywords = categoryKeywords;
    }

    public List<String> getNegativeKeywords() {
        return negativeKeywords;
    }

    public void setNegativeKeywords(List<String> negativeKeywords) {
        this.negativeKeywords = negativeKeywords;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
