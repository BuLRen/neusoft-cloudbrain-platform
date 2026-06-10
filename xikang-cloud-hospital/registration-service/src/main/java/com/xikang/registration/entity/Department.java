package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * Department Entity - 科室表
 */
@Data
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;           // dept_name
    private String code;           // dept_code
    private String type;           // dept_type
    private String description;    // dept_description
    private Integer delmark;       // 0=有效/1=删除
}
