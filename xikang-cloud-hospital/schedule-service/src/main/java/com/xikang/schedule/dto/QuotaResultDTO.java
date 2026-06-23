package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 号源扣减结果 DTO
 */
@Data
public class QuotaResultDTO implements Serializable {

    private Boolean success;
    private Integer remainingQuota;
    private String message;
}