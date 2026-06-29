package com.xikang.ai.catalog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for Dify W5 drug catalog search.
 */
public class DrugAiSearchRequest {

    private List<String> drugKeywords = new ArrayList<>();
    private List<String> genericKeywords = new ArrayList<>();
    private List<String> categoryKeywords = new ArrayList<>();
    private List<String> indicationKeywords = new ArrayList<>();
    private List<String> negativeKeywords = new ArrayList<>();
    private Integer limit = 40;

    public List<String> getDrugKeywords() {
        return drugKeywords;
    }

    public void setDrugKeywords(List<String> drugKeywords) {
        this.drugKeywords = drugKeywords;
    }

    public List<String> getGenericKeywords() {
        return genericKeywords;
    }

    public void setGenericKeywords(List<String> genericKeywords) {
        this.genericKeywords = genericKeywords;
    }

    public List<String> getCategoryKeywords() {
        return categoryKeywords;
    }

    public void setCategoryKeywords(List<String> categoryKeywords) {
        this.categoryKeywords = categoryKeywords;
    }

    public List<String> getIndicationKeywords() {
        return indicationKeywords;
    }

    public void setIndicationKeywords(List<String> indicationKeywords) {
        this.indicationKeywords = indicationKeywords;
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
