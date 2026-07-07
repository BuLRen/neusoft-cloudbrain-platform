package com.xikang.physician.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import com.xikang.physician.context.PhysicianAuthContext;
import com.xikang.physician.entity.AuthUser;
import com.xikang.physician.mapper.AuthUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class PhysicianAuthInterceptor implements HandlerInterceptor {

    private final AuthUserMapper authUserMapper;
    private final ObjectMapper objectMapper;

    public PhysicianAuthInterceptor(AuthUserMapper authUserMapper, ObjectMapper objectMapper) {
        this.authUserMapper = authUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            writeError(response, 401, "未授权，请先登录");
            return false;
        }

        Map<String, Object> claims = JwtUtils.parseToken(token);
        if (claims == null) {
            writeError(response, 401, "无效的令牌");
            return false;
        }

        String role = claims.get("role") != null ? String.valueOf(claims.get("role")) : "";
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

        if ("medtech".equals(role) || user.getUserType() != null && user.getUserType() == 4) {
            writeError(response, 403, "医技人员无权访问门诊诊疗接口");
            return false;
        }

        if (!"physician".equals(role) && !"admin".equals(role)) {
            writeError(response, 403, "无权访问门诊诊疗接口");
            return false;
        }

        boolean isAdmin = "admin".equals(role);
        boolean adminAllAccess = isAdmin;
        Long employeeId = null;
        if (!isAdmin) {
            if (user.getEmployeeId() != null) {
                employeeId = user.getEmployeeId().longValue();
            } else {
                employeeId = parseLong(claims.get("employeeId"));
            }
            if ("physician".equals(role) && employeeId == null) {
                writeError(response, 403, "医生账号未绑定员工档案");
                return false;
            }
        }

        PhysicianAuthContext.set(new PhysicianAuthContext.Context(userId, role, employeeId, adminAllAccess));
        if (employeeId != null) {
            request.setAttribute(PhysicianRequestAttributes.EMPLOYEE_ID, employeeId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        PhysicianAuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
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
        try {
            return Long.parseLong(String.valueOf(value));
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
