package com.xikang.medtech.entity;

import lombok.Data;

@Data
public class AuthUser {
    private Long id;
    private Integer userType;
    private Integer employeeId;
    private Integer departmentId;
    private String departmentName;
    private String realName;
    private Integer status;
}
