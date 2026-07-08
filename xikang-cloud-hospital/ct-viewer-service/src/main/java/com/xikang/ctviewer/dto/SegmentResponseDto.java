package com.xikang.ctviewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SegmentResponseDto {
    private String maskVolumeId;
    @JsonProperty("isMask")
    private boolean mask;
    private String message;
    private Map<String, Object> meta;
    private List<Map<String, Object>> lesions;
    private Map<String, Object> summary;

    /**
     * AI 分割专属字段（规则分割不填充，前端判断 source 字段区分）。
     * 这些指标直接从 summary 中也可取到，此处冗余提升前端便利性。
     */
    private String modelVersion;
    private Long processingTimeMs;
    private String overallRiskLevel;
}
