package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Settlement Category Entity - 结算类别（检查/检验/处置/药品）
 */
@Data
public class SettleCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;           // 类别名称：检查/检验/处置/药品
    private String code;           // 类别代码
    private String description;    // 描述
    private Integer status;       // 状态：0禁用/1启用
    private LocalDateTime createTime;
}
