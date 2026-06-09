package com.xikang.schedule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 调整确认 DTO
 */
@Data
public class AdjustConfirmDTO implements Serializable {

    private Long requestId;
    private Long confirmedBy;
    private String remark;
}