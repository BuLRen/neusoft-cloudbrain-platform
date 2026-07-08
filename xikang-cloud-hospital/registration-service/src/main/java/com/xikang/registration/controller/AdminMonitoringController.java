package com.xikang.registration.controller;

import com.xikang.common.result.Result;
import com.xikang.common.utils.JwtUtils;
import com.xikang.registration.service.MonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration/admin/monitoring")
@RequiredArgsConstructor
public class AdminMonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> listAlerts() {
        return Result.success(monitoringService.listAlerts());
    }

    @PutMapping("/alerts/{alertKey}/dismiss")
    public Result<Void> dismissAlert(@PathVariable String alertKey,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : "resolved";
        Long operatorId = null;
        String operatorName = null;
        Map<String, Object> claims = parseClaims(request);
        if (claims != null) {
            Object uid = claims.get("userId");
            if (uid instanceof Number n) {
                operatorId = n.longValue();
            } else if (uid != null) {
                try {
                    operatorId = Long.parseLong(uid.toString());
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            if (claims.get("realName") != null) {
                operatorName = String.valueOf(claims.get("realName"));
            } else if (claims.get("username") != null) {
                operatorName = String.valueOf(claims.get("username"));
            }
        }
        monitoringService.dismissAlert(alertKey, status, operatorId, operatorName);
        return Result.success();
    }

    private Map<String, Object> parseClaims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return JwtUtils.parseToken(authHeader.substring(7));
    }
}
