package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Expense Record Entity - 费用记录表
 */
@Data
public class ExpenseRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;           // 挂号ID
    private Long patientId;           // 患者ID
    private String patientName;       // 患者姓名
    private Long categoryId;          // 结算类别ID
    private String categoryName;      // 结算类别名称：检查/检验/处置/药品
    private Long itemId;             // 项目ID（检查/检验/处置/药品ID）
    private String itemName;         // 项目名称
    private String itemCode;         // 项目编码
    private Integer quantity;         // 数量
    private BigDecimal unitPrice;    // 单价
    private BigDecimal totalAmount;   // 总金额
    private Integer status;           // 状态：0待缴费/1已缴费/2已退款/3已作废
    private LocalDateTime payTime;   // 缴费时间
    private LocalDateTime refundTime; // 退款时间
    private Long operatorId;         // 操作人ID
    private String operatorName;      // 操作人姓名
    private String remark;           // 备注
    private LocalDateTime createTime;
}
