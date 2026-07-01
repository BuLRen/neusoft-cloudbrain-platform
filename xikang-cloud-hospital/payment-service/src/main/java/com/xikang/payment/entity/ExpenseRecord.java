package com.xikang.payment.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ExpenseRecord Entity - 费用记录表（v3.2：从 registration-service 迁移至 payment-service）
 *
 * expense_record: id, register_id, patient_id, patient_name, category_id, category_name,
 *                item_id, item_name, item_code, quantity, unit_price, total_amount,
 *                status, pay_time, refund_time, operator_id, operator_name, remark, create_time
 *
 * status: 0待缴费 / 1已缴费 / 2已退款 / 3已作废
 */
@Data
public class ExpenseRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;
    private Long patientId;
    private String patientName;
    private Long categoryId;
    private String categoryName;
    private Long itemId;
    private String itemName;
    private String itemCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
