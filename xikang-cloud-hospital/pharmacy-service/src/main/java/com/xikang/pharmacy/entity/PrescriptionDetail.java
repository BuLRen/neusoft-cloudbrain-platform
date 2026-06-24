package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prescription Detail Entity - 处方明细表
 */
@Data
public class PrescriptionDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long prescriptionId;      // 处方ID
    private Long drugId;             // 药品ID
    private String drugName;         // 药品名称
    private String specification;    // 规格
    private String dosage;           // 剂量
    private Integer quantity;        // 数量
    private BigDecimal unitPrice;    // 单价
    private BigDecimal totalAmount;  // 总金额
    private String usage;           // 用法：口服/静注/外用等
    private String frequency;        // 频次：一日几次
    private String duration;         // 疗程
    private String remark;           // 备注
    private String drugState;        // 处方行状态：未发/已发/已退（用于退药过滤）
    private LocalDateTime createTime;
}
