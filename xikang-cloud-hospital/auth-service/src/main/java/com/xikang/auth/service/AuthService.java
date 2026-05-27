package com.xikang.auth.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${jwt.accessExpirationMs:900000}")
    private long accessExpirationMs;

    @Value("${jwt.refreshExpirationMs:604800000}")
    private long refreshExpirationMs;

    /**
     * User login
     */
    public Map<String, String> login(String username, String password) {
        // TODO: Implement actual authentication logic with database
        log.info("User login: {}", username);
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");

        String accessToken = JwtUtils.generateToken(username, claims, accessExpirationMs);
        String refreshToken = JwtUtils.generateToken(username, claims, refreshExpirationMs);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", username,
                "role", "admin"
        );
    }

    /**
     * User logout
     */
    public void logout(String token) {
        // TODO: Implement token invalidation logic
        log.info("User logout, token: {}", token);
    }

    public Map<String, String> refresh(String refreshToken) {
        if (!JwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh token 无效或已过期");
        }

        String userId = JwtUtils.getSubject(refreshToken);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "Refresh token 无效");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");

        String accessToken = JwtUtils.generateToken(userId, claims, accessExpirationMs);
        return Map.of(
                "accessToken", accessToken,
                "userId", userId,
                "role", "admin"
        );
    }

    public Map<String, Object> me(String accessToken) {
        if (!JwtUtils.validateToken(accessToken)) {
            throw new BusinessException(401, "Access token 无效或已过期");
        }
        String userId = JwtUtils.getSubject(accessToken);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "Access token 无效");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("role", "admin");
        return result;
    }

    /**
     * User register
     */
    public void register(Map<String, String> registerRequest) {
        // TODO: Implement actual registration logic
        log.info("User register: {}", registerRequest);
    }

    /**
     * Validate token
     */
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        boolean valid = JwtUtils.validateToken(token);
        result.put("valid", valid);
        if (valid) {
            result.put("userId", JwtUtils.getSubject(token));
        }
        return result;
    }
}
