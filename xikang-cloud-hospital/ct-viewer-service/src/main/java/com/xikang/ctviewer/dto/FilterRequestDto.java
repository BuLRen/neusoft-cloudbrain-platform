package com.xikang.ctviewer.dto;

import lombok.Data;

import java.util.Map;

@Data
public class FilterRequestDto {
    private String sourceVolumeId;
    private String filterName;
    private Map<String, Object> params;
}
