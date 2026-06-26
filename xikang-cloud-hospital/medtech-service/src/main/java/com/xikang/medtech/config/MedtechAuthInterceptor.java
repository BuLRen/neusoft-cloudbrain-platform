package com.xikang.medtech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import com.xikang.medtech.context.MedtechAuthContext;
import com.xikang.medtech.entity.AuthUser;
import com.xikang.medtech.mapper.AuthUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class MedtechAuthInterceptor implements HandlerInterceptor {

    private final AuthUserMapper authUserMapper;
    private final ObjectMapper objectMapper;

    public MedtechAuthInterceptor(AuthUserMapper authUserMapper, ObjectMapper objectMapper) {
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

        if ("physician".equals(role) || user.getUserType() != null && user.getUserType() == 2) {
            writeError(response, 403, "门诊医生无权访问医技执行接口");
            return false;
        }

        if (!"medtech".equals(role) && !"admin".equals(role)) {
            writeError(response, 403, "无权访问医技执行接口");
            return false;
        }

        boolean isAdmin = "admin".equals(role);
        Long employeeId = null;
        Long departmentId = null;
        String departmentName = null;

        if (!isAdmin) {
            if (user.getEmployeeId() != null) {
                employeeId = user.getEmployeeId().longValue();
            } else {
                employeeId = parseLong(claims.get("employeeId"));
            }
            if ("medtech".equals(role) && employeeId == null) {
                writeError(response, 403, "医技账号未绑定员工档案");
                return false;
            }
            if (user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId().longValue();
            }
            departmentName = user.getDepartmentName();
            if ("medtech".equals(role) && departmentId == null) {
                writeError(response, 403, "医技账号未绑定执行科室");
                return false;
            }
        }

        MedtechAuthContext.set(new MedtechAuthContext.Context(
            userId,
            role,
            employeeId,
            departmentId,
            departmentName,
            isAdmin
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MedtechAuthContext.clear();
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
