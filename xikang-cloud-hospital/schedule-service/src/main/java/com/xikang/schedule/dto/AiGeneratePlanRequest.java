package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiGeneratePlanRequest implements Serializable {
    private Long departmentId;
    private String month;
    private Long operatorId;
    private String generateType;
}
