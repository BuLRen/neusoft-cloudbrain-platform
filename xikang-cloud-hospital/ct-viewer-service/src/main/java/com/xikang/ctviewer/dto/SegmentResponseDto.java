package com.xikang.ctviewer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SegmentResponseDto {
    private String maskVolumeId;
    @JsonProperty("isMask")
    private boolean mask;
    private String message;
    private Map<String, Object> meta;
    private List<Map<String, Object>> lesions;
    private Map<String, Object> summary;
}
