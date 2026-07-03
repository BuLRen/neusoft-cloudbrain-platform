package com.xikang.ctviewer.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LoadResponseDto {
    private String volumeId;
    private Map<String, Object> meta;
}
