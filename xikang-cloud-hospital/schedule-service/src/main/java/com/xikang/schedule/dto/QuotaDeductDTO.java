package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 号源扣减请求 DTO
 */
@Data
public class QuotaDeductDTO implements Serializable {

    private Long scheduleId;
    private Integer count;
    private Long registerId; // 关联挂号ID（用于追溯）
}