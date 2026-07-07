package com.xikang.registration.service;

import com.xikang.registration.mapper.MonitoringDismissalMapper;
import com.xikang.registration.mapper.MonitoringMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MonitoringMapper monitoringMapper;
    private final MonitoringDismissalMapper monitoringDismissalMapper;

    public List<Map<String, Object>> listAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        alerts.addAll(buildTriageTimeoutAlerts());
        alerts.addAll(buildTriageBacklogAlerts());
        alerts.addAll(buildWaitingBacklogAlerts());
        alerts.addAll(buildLowStockAlerts());
        return applyDismissalStatus(alerts);
    }

    @Transactional
    public void dismissAlert(String alertKey, String status, Long operatorId, String operatorName) {
        String normalized = normalizeDismissStatus(status);
        try {
            monitoringDismissalMapper.upsertDismissal(alertKey, normalized, operatorId, operatorName);
        } catch (Exception e) {
            // 表未迁移时降级：不阻断前端操作
        }
    }

    private List<Map<String, Object>> buildTriageTimeoutAlerts() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : monitoringMapper.selectHighRiskTriageTimeouts()) {
            Object id = row.get("id");
            String patientName = String.valueOf(row.getOrDefault("patientName", "患者"));
            Map<String, Object> alert = baseAlert(
                "triage:timeout:" + id,
                "AI 分诊",
                "高风险分诊超时未处理：" + patientName,
                "critical",
                "管理员"
            );
            alert.put("summary", "患者 " + patientName + " 的分诊记录已超过 30 分钟未确认。");
            result.add(alert);
        }
        return result;
    }

    private List<Map<String, Object>> buildTriageBacklogAlerts() {
        int pending = monitoringMapper.countPendingTriage();
        if (pending <= 5) {
            return List.of();
        }
        Map<String, Object> alert = baseAlert(
            "triage:backlog",
            "AI 分诊",
            "待确认分诊积压（" + pending + " 条）",
            "warning",
            "管理员"
        );
        alert.put("summary", "当前有 " + pending + " 条分诊记录待管理员确认，建议尽快处理。");
        return List.of(alert);
    }

    private List<Map<String, Object>> buildWaitingBacklogAlerts() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : monitoringMapper.selectDepartmentWaitingBacklog()) {
            Object deptId = row.get("departmentId");
            String deptName = String.valueOf(row.getOrDefault("departmentName", "科室"));
            Object waitingCount = row.get("waitingCount");
            Map<String, Object> alert = baseAlert(
                "calling:waiting:" + deptId,
                "叫号候诊",
                deptName + " 候诊积压（" + waitingCount + " 人）",
                "warning",
                "分诊台"
            );
            alert.put("summary", deptName + " 当前候诊人数 " + waitingCount + "，超过预警阈值 10 人。");
            result.add(alert);
        }
        return result;
    }

    private List<Map<String, Object>> buildLowStockAlerts() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : monitoringMapper.selectLowStockDrugs(10)) {
            Object drugId = row.get("id");
            String name = String.valueOf(row.getOrDefault("name", "药品"));
            Object stock = row.get("stockQuantity");
            Object threshold = row.get("lowStockThreshold");
            Map<String, Object> alert = baseAlert(
                "pharmacy:low-stock:" + drugId,
                "药房",
                name + " 库存低于安全线",
                "info",
                "药房值班"
            );
            alert.put("summary", name + " 当前库存 " + stock + "，低于安全阈值 " + threshold + "。");
            result.add(alert);
        }
        return result;
    }

    private Map<String, Object> baseAlert(String alertKey, String module, String title,
                                          String level, String owner) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("alertKey", alertKey);
        alert.put("module", module);
        alert.put("title", title);
        alert.put("level", level);
        alert.put("status", "pending");
        alert.put("owner", owner);
        alert.put("updatedAt", LocalDateTime.now().format(TS_FMT));
        return alert;
    }

    private List<Map<String, Object>> applyDismissalStatus(List<Map<String, Object>> alerts) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> alert : alerts) {
            String alertKey = String.valueOf(alert.get("alertKey"));
            String dismissed = null;
            try {
                dismissed = monitoringDismissalMapper.selectStatusByAlertKey(alertKey);
            } catch (Exception e) {
                // 表未迁移时忽略 dismiss 状态
            }
            if ("resolved".equals(dismissed)) {
                continue;
            }
            if ("processing".equals(dismissed)) {
                alert.put("status", "processing");
            }
            visible.add(alert);
        }
        return visible;
    }

    private String normalizeDismissStatus(String status) {
        if ("processing".equalsIgnoreCase(status)) {
            return "processing";
        }
        return "resolved";
    }
}
