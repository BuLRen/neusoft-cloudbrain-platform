package com.xikang.physician.entity;

import lombok.Data;

@Data
public class AuthUser {
    private Long id;
    private Integer userType;
    private Integer employeeId;
    private Integer status;
}
