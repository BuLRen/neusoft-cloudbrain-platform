package com.xikang.auth.controller;

import com.xikang.auth.service.AuthService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * User login
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        String token = authService.login(username, password);
        return Result.success(Map.of("token", token));
    }

    /**
     * User logout
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return Result.success();
    }

    /**
     * User register
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> registerRequest) {
        authService.register(registerRequest);
        return Result.success();
    }

    /**
     * Validate token
     */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validateToken(@RequestHeader("Authorization") String token) {
        Map<String, Object> result = authService.validateToken(token);
        return Result.success(result);
    }
}
