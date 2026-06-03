package com.xikang.auth.controller;

import com.xikang.auth.service.AuthService;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;

    /**
     * User login
     */
    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        Map<String, String> tokens = authService.login(username, password);

        String accessToken = tokens.get("accessToken");
        String refreshToken = tokens.get("refreshToken");

        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_COOKIE_NAME, accessToken)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(60 * 60)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        Map<String, Object> body = Map.of(
                "userId", tokens.get("userId"),
                "role", tokens.get("role"),
                "realName", tokens.get("realName"),
                "token", accessToken,
                "refreshToken", refreshToken
        );
        return ResponseEntity.ok().headers(headers).body(Result.success("登录成功", body));
    }

    /**
     * User logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);

        ResponseCookie clearAccess = ResponseCookie.from(ACCESS_COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie clearRefresh = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, clearAccess.toString());
        headers.add(HttpHeaders.SET_COOKIE, clearRefresh.toString());

        return ResponseEntity.ok().headers(headers).body(Result.success("登出成功", null));
    }

    /**
     * Refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<Result<Void>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        Map<String, String> refreshed = authService.refresh(refreshToken);

        String accessToken = refreshed.get("accessToken");

        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_COOKIE_NAME, accessToken)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(60 * 60)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
        return ResponseEntity.ok().headers(headers).body(Result.success("刷新成功", null));
    }

    /**
     * Get current user info (for session check)
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        // Try to get token from Authorization header first
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null) {
            return Result.error(401, "未授权");
        }
        Map<String, Object> result = authService.me(token);
        return Result.success(result);
    }

    /**
     * User register
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> registerRequest) {
        authService.register(registerRequest);
        return Result.success("注册成功", null);
    }

    /**
     * Validate token
     */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Map<String, Object> result = authService.validateToken(token);
        return Result.success(result);
    }
}