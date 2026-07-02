package com.xikang.registration.dto;

import lombok.Data;

@Data
public class FollowUpAdminView {
    private Long id;
    private Long deptmentId;
    private String deptName;
    private String realname;
    private Integer delmark;
    private Long userId;
    private String username;
    private Integer accountStatus;
}
