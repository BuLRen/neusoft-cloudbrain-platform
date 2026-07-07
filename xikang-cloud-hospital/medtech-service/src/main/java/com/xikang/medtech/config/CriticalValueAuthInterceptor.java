package com.xikang.medtech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import com.xikang.medtech.context.CriticalValueAuthContext;
import com.xikang.medtech.entity.AuthUser;
import com.xikang.medtech.mapper.AuthUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

@Component
public class CriticalValueAuthInterceptor implements HandlerInterceptor {

    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final Set<String> PHYSICIAN_PATHS = Set.of("/pending", "/ack", "/handle");
    private static final Set<String> MEDTECH_PATHS = Set.of("/report", "/board");
    private static final Set<String> MEDTECH_ROLES = Set.of("medtech", "admin");

    private final AuthUserMapper authUserMapper;
    private final ObjectMapper objectMapper;

    public CriticalValueAuthInterceptor(AuthUserMapper authUserMapper, ObjectMapper objectMapper) {
        this.authUserMapper = authUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.contains("/critical-value/stream/")) {
            return true;
        }

        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            writeError(response, 401, "未授权，请先登录");
            return false;
        }

        Claims claims = JwtUtils.parseToken(token);
        if (claims == null) {
            writeError(response, 401, "无效的令牌");
            return false;
        }

        Long userId = parseLong(claims.get("userId"));
        if (userId == null) {
            writeError(response, 401, "无效的令牌");
            return false;
        }

        AuthUser user = authUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            writeError(response, 401, "用户不存在或已禁用");
            return false;
        }

        String role = resolveRole(claims.get("role"), user.getUserType());
        String suffix = resolveSuffix(uri);

        // 管理员无需 employeeId，提前放行，避免后续拆箱 NPE
        if ("admin".equals(role)) {
            CriticalValueAuthContext.set(new CriticalValueAuthContext.Context(
                userId, role, resolveEmployeeIdOrNull(user, claims), user.getRealName()));
            return true;
        }

        if (PHYSICIAN_PATHS.contains(suffix)) {
            if (!"physician".equals(role)) {
                writeError(response, 403, "仅门诊医生可执行该操作");
                return false;
            }
            Long employeeId = resolveEmployeeIdOrNull(user, claims);
            if (employeeId == null) {
                writeError(response, 403, "医生账号未绑定员工档案");
                return false;
            }
            CriticalValueAuthContext.set(new CriticalValueAuthContext.Context(userId, role, employeeId, user.getRealName()));
            return true;
        }

        if (MEDTECH_PATHS.contains(suffix)) {
            if (!MEDTECH_ROLES.contains(role)) {
                writeError(response, 403, "仅医技人员或管理员可执行该操作");
                return false;
            }
            CriticalValueAuthContext.set(new CriticalValueAuthContext.Context(
                userId, role, resolveEmployeeIdOrNull(user, claims), user.getRealName()));
            return true;
        }

        writeError(response, 403, "无权访问危急值接口");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CriticalValueAuthContext.clear();
    }

    private String resolveRole(Object jwtRole, Integer userType) {
        if (jwtRole != null) {
            String role = String.valueOf(jwtRole).trim().toLowerCase(Locale.ROOT);
            if (!role.isEmpty()) {
                return role;
            }
        }
        return userTypeToRole(userType);
    }

    private Long resolveEmployeeIdOrNull(AuthUser user, Claims claims) {
        if (user.getEmployeeId() != null) {
            return user.getEmployeeId().longValue();
        }
        return parseLong(claims.get("employeeId"));
    }

    private String userTypeToRole(Integer userType) {
        if (userType == null) {
            return "";
        }
        return switch (userType) {
            case 1 -> "admin";
            case 2 -> "physician";
            case 3 -> "registration";
            case 4 -> "medtech";
            case 5 -> "pharmacy";
            case 7 -> "followup";
            default -> "patient";
        };
    }

    private String resolveSuffix(String uri) {
        String marker = "/critical-value";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return "";
        }
        String tail = uri.substring(idx + marker.length());
        if (tail.startsWith("/report")) {
            return "/report";
        }
        if (tail.startsWith("/pending")) {
            return "/pending";
        }
        if (tail.contains("/ack")) {
            return "/ack";
        }
        if (tail.contains("/handle")) {
            return "/handle";
        }
        if (tail.startsWith("/board")) {
            return "/board";
        }
        return tail;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? 401 : 403);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(code, message));
    }
}
