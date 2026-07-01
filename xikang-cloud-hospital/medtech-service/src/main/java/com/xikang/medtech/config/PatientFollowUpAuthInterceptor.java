package com.xikang.medtech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import com.xikang.medtech.context.PatientFollowUpAuthContext;
import com.xikang.medtech.entity.AuthUser;
import com.xikang.medtech.mapper.AuthUserMapper;
import com.xikang.medtech.mapper.PatientFollowUpAuthMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PatientFollowUpAuthInterceptor implements HandlerInterceptor {

    private final AuthUserMapper authUserMapper;
    private final PatientFollowUpAuthMapper patientFollowUpAuthMapper;
    private final ObjectMapper objectMapper;

    public PatientFollowUpAuthInterceptor(
        AuthUserMapper authUserMapper,
        PatientFollowUpAuthMapper patientFollowUpAuthMapper,
        ObjectMapper objectMapper
    ) {
        this.authUserMapper = authUserMapper;
        this.patientFollowUpAuthMapper = patientFollowUpAuthMapper;
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

        boolean isPatient = "patient".equals(role) || (user.getUserType() != null && user.getUserType() == 6);
        if (!isPatient) {
            writeError(response, 403, "仅患者账号可访问该接口");
            return false;
        }

        List<Long> patientIds = patientFollowUpAuthMapper.selectPatientIdsByUserId(userId);
        if (patientIds.isEmpty()) {
            writeError(response, 403, "患者账号未绑定档案");
            return false;
        }

        PatientFollowUpAuthContext.set(new PatientFollowUpAuthContext.Context(userId, role, patientIds));

        Long registerId = extractRegisterId(request);
        if (registerId != null && !patientFollowUpAuthMapper.isRegisterAccessible(registerId, patientIds, userId)) {
            writeError(response, 403, "无权访问该就诊记录");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        PatientFollowUpAuthContext.clear();
    }

    private Long extractRegisterId(HttpServletRequest request) {
        Set<Long> candidates = new LinkedHashSet<>();
        addLongParam(candidates, request.getParameter("registerId"));
        String path = request.getRequestURI();
        if (path != null) {
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("session".equals(segments[i]) && i + 1 < segments.length) {
                    continue;
                }
                if ("patient".equals(segments[i]) && i + 1 < segments.length && "session".equals(segments[i + 1])) {
                    addLongParam(candidates, segments[i + 2]);
                }
            }
            for (int i = 0; i < segments.length; i++) {
                if ("patient-brief".equals(segments[i]) && i + 1 < segments.length) {
                    addLongParam(candidates, segments[i + 1]);
                }
            }
        }
        List<Long> list = new ArrayList<>(candidates);
        return list.isEmpty() ? null : list.get(0);
    }

    private void addLongParam(Set<Long> target, String raw) {
        Long value = parseLong(raw);
        if (value != null) {
            target.add(value);
        }
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
