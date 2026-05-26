package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registration Level Entity - 挂号级别（普通/专家/特需）
 */
@Data
public class RegistLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;           // 级别名称：普通/专家/特需
    private BigDecimal price;     // 挂号费
    private String description;    // 描述
    private Integer status;       // 状态：0禁用/1启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
