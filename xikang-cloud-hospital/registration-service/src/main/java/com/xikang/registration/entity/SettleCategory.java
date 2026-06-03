package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * SettleCategory Entity - 结算类别表
 * settle_category: id, settle_code, settle_name, sequence_no, delmark
 */
@Data
public class SettleCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;          // settle_code
    private String name;         // settle_name
    private Integer sequenceNo;   // sequence_no
    private Integer delmark;     // delmark: 0=有效/1=删除
}