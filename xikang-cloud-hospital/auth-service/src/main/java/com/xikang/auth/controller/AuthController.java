package com.xikang.auth.controller;

import com.xikang.auth.service.AuthService;
import com.xikang.auth.service.PatientService;
import com.xikang.auth.service.CaptchaService;
import com.xikang.auth.entity.Patient;
import com.xikang.auth.dto.LoginRequest;
import com.xikang.auth.dto.CaptchaResponse;
import com.xikang.auth.dto.UserInfoResponse.PatientInfo;
import com.xikang.common.utils.JwtUtils;
import com.xikang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final PatientService patientService;
    private final CaptchaService captchaService;

    /**
     * Get login captcha image
     */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * User login
     */
    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(@RequestBody LoginRequest loginRequest) {
        Map<String, String> tokens = authService.login(loginRequest);

        String username = loginRequest.getUsername();

        String accessToken = tokens.get("accessToken");
        String refreshToken = tokens.get("refreshToken");
        Long userId = Long.parseLong(tokens.get("userId"));

        // 获取患者列表
        List<Patient> patientList = patientService.getPatientsByUserId(userId);
        List<PatientInfo> patients = patientList.stream()
                .map(p -> PatientInfo.builder()
                        .patientId(p.getId())
                        .realName(p.getRealName())
                        .gender(p.getGender())
                        .relation(p.getRelation())
                        .isPrimary(p.getIsPrimary())
                        .accountBalance(p.getAccountBalance())
                        .allergyHistory(p.getAllergyHistory())
                        .build())
                .collect(Collectors.toList());

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

        Map<String, Object> body = new HashMap<>();
        body.put("userId", tokens.get("userId"));
        body.put("username", username);
        body.put("role", tokens.get("role"));
        body.put("realName", tokens.get("realName"));
        body.put("token", accessToken);
        body.put("refreshToken", refreshToken);
        body.put("patients", patients);
        if (tokens.containsKey("employeeId")) {
            body.put("employeeId", Long.parseLong(tokens.get("employeeId")));
        }
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
    public ResponseEntity<Result<Map<String, Object>>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader,
            @RequestBody(required = false) Map<String, String> requestBody
    ) {
        String refreshToken = refreshHeader;
        if ((refreshToken == null || refreshToken.isBlank()) && requestBody != null) {
            refreshToken = requestBody.get("refreshToken");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = refreshCookie;
        }
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

        Map<String, Object> body = new HashMap<>();
        body.put("token", accessToken);
        body.put("accessToken", accessToken);
        return ResponseEntity.ok().headers(headers).body(Result.success("刷新成功", body));
    }

    /**
     * Get current user info (for session check)
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = ACCESS_COOKIE_NAME, required = false) String accessCookie
    ) {
        String headerToken = extractBearerToken(authHeader);
        String token = null;
        if (headerToken != null && !headerToken.isBlank() && JwtUtils.validateToken(headerToken)) {
            token = headerToken;
        } else if (accessCookie != null && !accessCookie.isBlank() && JwtUtils.validateToken(accessCookie)) {
            token = accessCookie;
        }
        if (token == null || token.isBlank()) {
            return Result.error(401, "未授权");
        }
        Map<String, Object> result = authService.me(token);
        return Result.success(result);
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
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

    /**
     * Change password
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> passwordRequest
    ) {
        String token = authHeader;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // Validate token and get username
        if (!JwtUtils.validateToken(token)) {
            return Result.error(401, "未授权，请重新登录");
        }

        String username = JwtUtils.getSubject(token);
        if (username == null || username.isBlank()) {
            return Result.error(401, "无效的令牌");
        }

        String oldPassword = passwordRequest.get("oldPassword");
        String newPassword = passwordRequest.get("newPassword");

        authService.changePassword(username, oldPassword, newPassword);
        return Result.success("密码修改成功", null);
    }
}
