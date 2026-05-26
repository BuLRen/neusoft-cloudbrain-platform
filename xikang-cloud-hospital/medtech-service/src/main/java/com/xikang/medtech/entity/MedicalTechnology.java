package com.xikang.medtech.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Medical Technology Entity - 医技项目表（检查/检验/处置）
 */
@Data
public class MedicalTechnology implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;              // 项目名称
    private String code;              // 项目编码
    private String type;              // 类型：检查/检验/处置
    private Long departmentId;        // 执行科室ID
    private String departmentName;    // 执行科室名称
    private BigDecimal price;         // 价格
    private String specimenType;      // 样本类型（检验用）
    private String container;         // 容器类型
    private String instructions;      // 注意事项
    private String preparation;       // 患者准备要求
    private Integer turnaroundTime;   // 出结果时间（小时）
    private Integer status;           // 状态：0禁用/1启用
    private String description;       // 描述
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
