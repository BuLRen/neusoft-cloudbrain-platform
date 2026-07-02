package com.xikang.ctviewer.dto;

import lombok.Data;

import java.util.Map;

@Data
public class FilterResponseDto {
    private String volumeId;
    private boolean isMask;
    private String message;
    private Map<String, Object> meta;
}
