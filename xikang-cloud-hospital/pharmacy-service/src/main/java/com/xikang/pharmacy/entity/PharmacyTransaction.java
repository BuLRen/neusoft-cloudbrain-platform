package com.xikang.pharmacy.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pharmacy Transaction Entity - 药房交易记录表
 */
@Data
public class PharmacyTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;             // 类型：发放/退回/报损/入库/盘点
    private Long drugId;             // 药品ID
    private String drugName;         // 药品名称
    private Long prescriptionId;     // 处方ID
    private Long registerId;         // 挂号ID
    private Integer quantity;        // 数量（正数入库/负数出库）
    private BigDecimal unitPrice;    // 单价
    private BigDecimal totalAmount; // 总金额
    private Long operatorId;         // 操作人ID
    private String operatorName;     // 操作人姓名
    private String reason;           // 原因
    private LocalDateTime transactionTime;
    private LocalDateTime createTime;
}
