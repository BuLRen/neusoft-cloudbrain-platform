package com.xikang.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User info DTO for current user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private Long deptId;
    private String deptName;
    private Long registLevelId;
    private String registLevelName;
}