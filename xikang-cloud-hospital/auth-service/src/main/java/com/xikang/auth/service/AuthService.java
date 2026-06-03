package com.xikang.auth.service;

import com.xikang.auth.entity.User;
import com.xikang.auth.mapper.UserMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    @Value("${jwt.accessExpirationMs:900000}")
    private long accessExpirationMs;

    @Value("${jwt.refreshExpirationMs:604800000}")
    private long refreshExpirationMs;

    /**
     * User login
     */
    public Map<String, String> login(String username, String password) {
        log.info("User login attempt: {}", username);

        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "密码不能为空");
        }

        // Query user from database
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Verify password (plain text for dev, use BCrypt in production)
        if (!user.getPassword().equals(password)) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Check user status (1 = active)
        if (user.getStatus() != 1) {
            throw new BusinessException(401, "账号已被禁用");
        }

        // Convert userType to role string
        // 1: admin, 2: physician, 3: registration, 4: medtech, 5: pharmacy, 6: patient
        String role = convertUserTypeToRole(user.getUserType());

        // Generate JWT tokens
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", role);

        String accessToken = JwtUtils.generateToken(username, claims, accessExpirationMs);
        String refreshToken = JwtUtils.generateToken(username, claims, refreshExpirationMs);

        log.info("User login success: {} with role: {}", username, role);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", String.valueOf(user.getId()),
                "role", role,
                "realName", user.getRealName() != null ? user.getRealName() : username
        );
    }

    /**
     * User logout
     */
    public void logout(String token) {
        log.info("User logout, token prefix: {}",
                token != null && token.length() > 20 ? token.substring(0, 20) + "..." : token);
    }

    /**
     * Refresh access token
     */
    public Map<String, String> refresh(String refreshToken) {
        if (!JwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh token 无效或已过期");
        }

        String username = JwtUtils.getSubject(refreshToken);
        if (username == null || username.isBlank()) {
            throw new BusinessException(401, "Refresh token 无效");
        }

        // Re-validate user exists and is active
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }

        String role = convertUserTypeToRole(user.getUserType());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", role);

        String accessToken = JwtUtils.generateToken(username, claims, accessExpirationMs);

        return Map.of(
                "accessToken", accessToken,
                "userId", String.valueOf(user.getId()),
                "role", role
        );
    }

    /**
     * Get current user info from token
     */
    public Map<String, Object> me(String accessToken) {
        if (!JwtUtils.validateToken(accessToken)) {
            throw new BusinessException(401, "Access token 无效或已过期");
        }

        String username = JwtUtils.getSubject(accessToken);
        if (username == null || username.isBlank()) {
            throw new BusinessException(401, "Access token 无效");
        }

        // Get userId from token claims
        var claims = JwtUtils.parseToken(accessToken);
        String userIdStr = claims != null ? String.valueOf(claims.get("userId")) : null;
        String role = claims != null ? (String) claims.get("role") : "admin";

        // Query real name from database
        String realName = null;
        User user = userMapper.selectByUsername(username);
        if (user != null) {
            realName = user.getRealName();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userIdStr != null ? userIdStr : username);
        result.put("role", role);
        result.put("realName", realName != null ? realName : username);
        return result;
    }

    /**
     * User register (for patients and staff)
     */
    public void register(Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");
        String realName = registerRequest.get("realName");
        String phone = registerRequest.get("phone");
        String userTypeStr = registerRequest.get("userType");

        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "密码不能为空");
        }

        // Check if username already exists
        User existing = userMapper.selectByUsername(username);
        if (existing != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // Plain text for dev, BCrypt for production
        user.setRealName(realName != null ? realName : username);
        user.setPhone(phone);
        user.setUserType(userTypeStr != null ? Integer.parseInt(userTypeStr) : 6); // Default to patient (6)
        user.setStatus(1); // Active
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("User registered successfully: {}", username);
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
            var claims = JwtUtils.parseToken(token);
            result.put("userId", claims != null ? claims.get("userId") : JwtUtils.getSubject(token));
            result.put("role", claims != null ? claims.get("role") : "admin");
        }
        return result;
    }

    /**
     * Convert userType to role string
     * 1: admin, 2: physician, 3: registration, 4: medtech, 5: pharmacy, 6: patient
     */
    private String convertUserTypeToRole(Integer userType) {
        if (userType == null) {
            return "patient";
        }
        return switch (userType) {
            case 1 -> "admin";
            case 2 -> "physician";
            case 3 -> "registration";
            case 4 -> "medtech";
            case 5 -> "pharmacy";
            default -> "patient";
        };
    }
}