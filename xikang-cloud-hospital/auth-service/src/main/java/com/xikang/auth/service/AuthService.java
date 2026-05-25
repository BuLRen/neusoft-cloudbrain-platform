package com.xikang.auth.service;

import com.xikang.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * User login
     */
    public String login(String username, String password) {
        // TODO: Implement actual authentication logic with database
        log.info("User login: {}", username);
        return JwtUtils.generateToken(username);
    }

    /**
     * User logout
     */
    public void logout(String token) {
        // TODO: Implement token invalidation logic
        log.info("User logout, token: {}", token);
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
