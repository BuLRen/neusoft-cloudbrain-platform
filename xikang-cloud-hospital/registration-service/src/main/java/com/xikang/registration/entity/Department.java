package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Department Entity - 科室表
 */
@Data
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;           // 科室名称
    private String code;           // 科室编码
    private String type;           // 科室类型：内科/外科/医技/药房/其他
    private Long parentId;         // 上级科室ID
    private Integer orderNum;      // 排序号
    private Integer status;        // 状态：0禁用/1启用
    private String description;    // 描述
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
