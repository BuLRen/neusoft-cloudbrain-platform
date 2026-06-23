package com.xikang.schedule.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Coze Webhook 回调 Controller
 * 预留接口，明天配置 Coze 工作流时填写具体逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/webhook")
@RequiredArgsConstructor
public class CozeWebhookController {

    /**
     * AI 生成排班完成回调
     * TODO: 明天实现具体逻辑
     */
    @PostMapping("/ai-generate")
    public Map<String, Object> onAiGenerate(@RequestBody Map<String, Object> payload) {
        log.info("【预留】收到 Coze AI 生成排班回调：{}", payload);

        // TODO: 明天实现
        // 1. 解析 payload 中的 plan_id, schedules[]
        // 2. 保存到数据库
        // 3. 返回确认

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "接收成功");
        result.put("data", Map.of(
                "plan_id", payload.get("plan_id"),
                "status", "草稿",
                "schedule_count", 0
        ));
        return result;
    }

    /**
     * 请假处理完成回调
     * TODO: 明天实现具体逻辑
     */
    @PostMapping("/leave-processed")
    public Map<String, Object> onLeaveProcessed(@RequestBody Map<String, Object> payload) {
        log.info("【预留】收到 Coze 请假处理回调：{}", payload);

        // TODO: 明天实现
        // 1. 解析 payload 中的 leave_id, adjust_id
        // 2. 更新请假申请状态
        // 3. 发送通知

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "接收成功");
        return result;
    }

    /**
     * 号源预警完成回调
     * TODO: 明天实现具体逻辑
     */
    @PostMapping("/quota-alert")
    public Map<String, Object> onQuotaAlert(@RequestBody Map<String, Object> payload) {
        log.info("【预留】收到 Coze 号源预警回调：{}", payload);

        // TODO: 明天实现
        // 1. 解析预警信息
        // 2. 发送通知给管理员

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "接收成功");
        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("service", "schedule-service");
        result.put("coze_configured", false); // 明天配置后更新
        return result;
    }
}