package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * RegistLevel Entity - 挂号级别表
 * regist_level: id, regist_code, regist_name, regist_fee, regist_quota, sequence_no, delmark
 */
@Data
public class RegistLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;           // regist_code
    private String name;          // regist_name
    private BigDecimal price;     // regist_fee (挂号费)
    private Integer quota;        // regist_quota
    private Integer sequenceNo;   // sequence_no
    private Integer delmark;      // delmark: 0=有效/1=删除
}