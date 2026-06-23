package com.xikang.registration.dto;

import lombok.Data;

@Data
public class PhysicianAdminView {
    private Long id;
    private Long deptmentId;
    private String deptName;
    private Long registLevelId;
    private String registName;
    private String realname;
    private Integer delmark;
    private Long userId;
    private String username;
    private Integer accountStatus;
}
