package com.xikang.auth.dto;

import lombok.Data;

/**
 * Refresh token request DTO
 */
@Data
public class RefreshRequest {
    private String refreshToken;
}