package com.xikang.schedule.client.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RegistLevelDTO implements Serializable {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer quota;
}
