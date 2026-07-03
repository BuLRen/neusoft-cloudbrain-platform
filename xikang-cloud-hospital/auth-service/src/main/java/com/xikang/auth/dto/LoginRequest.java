package com.xikang.auth.dto;

import lombok.Data;

/**
 * Login request DTO
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
    private String captchaId;
    private String captchaCode;
}