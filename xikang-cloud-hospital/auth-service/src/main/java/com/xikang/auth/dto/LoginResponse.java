package com.xikang.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private Long userId;
    private String realName;
    private String role;
    private Long deptId;
    private String deptName;
}