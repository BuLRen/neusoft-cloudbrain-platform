package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ExpenseRecord Entity - 费用记录表
 * expense_record: id, register_id, patient_id, patient_name, category_id, category_name,
 *                item_id, item_name, item_code, quantity, unit_price, total_amount,
 *                status, pay_time, refund_time, operator_id, operator_name, remark, create_time
 */
@Data
public class ExpenseRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long registerId;           // register_id
    private Long patientId;           // patient_id
    private String patientName;       // patient_name
    private Long categoryId;          // category_id
    private String categoryName;      // category_name
    private Long itemId;             // item_id
    private String itemName;         // item_name
    private String itemCode;         // item_code
    private Integer quantity;         // quantity
    private BigDecimal unitPrice;    // unit_price
    private BigDecimal totalAmount;   // total_amount
    private Integer status;           // status: 0待缴费/1已缴费/2已退款/3已作废
    private LocalDateTime payTime;   // pay_time
    private LocalDateTime refundTime; // refund_time
    private Long operatorId;         // operator_id
    private String operatorName;      // operator_name
    private String remark;           // remark
    private LocalDateTime createTime; // create_time
}