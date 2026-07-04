package com.xikang.notification.controller;

import com.xikang.common.result.Result;
import com.xikang.notification.dto.NotificationSendRequest;
import com.xikang.notification.entity.Notification;
import com.xikang.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知 Controller
 * <p>对外 REST API（用户读消息）+ 内部 send API（其他服务调用）。
 */
@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 内部调用鉴权 token（X-Internal-Token） */
    @Value("${notification.internal-token:notif-internal-2026}")
    private String internalToken;

    // ==================== 内部接口（其他业务服务调） ====================

    /**
     * 写入单条消息（其他业务服务用）。
     * <p>需带 X-Internal-Token header 鉴权。
     */
    @PostMapping("/send")
    public Result<Notification> send(@RequestBody NotificationSendRequest req,
                                     @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalToken.equals(token)) {
            log.warn("内部 send 调用鉴权失败：tokenPresent={}, type={}", token != null, req.getType());
            return Result.error(401, "鉴权失败");
        }
        try {
            Notification entity = notificationService.send(req);
            return Result.success(entity);
        } catch (RuntimeException e) {
            log.warn("send 写入失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量写入（同内容群发，例如医生变更通知所有受影响患者）。
     */
    @PostMapping("/batch-send")
    public Result<Integer> batchSend(@RequestBody List<NotificationSendRequest> reqs,
                                     @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalToken.equals(token)) {
            return Result.error(401, "鉴权失败");
        }
        try {
            int rows = notificationService.batchSend(reqs);
            return Result.success(rows);
        } catch (RuntimeException e) {
            log.warn("batchSend 失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // ==================== 用户接口 ====================

    /**
     * 列表查询（分页 + 可选过滤）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam Long receiverId,
                                            @RequestParam String receiverRole,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Integer isRead,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size) {
        if (size == null || size <= 0 || size > 200) {
            size = 20;
        }
        List<Notification> items = notificationService.list(receiverId, receiverRole, type, isRead, page, size);
        long total = notificationService.count(receiverId, receiverRole, type, isRead);

        Map<String, Object> data = new HashMap<>();
        data.put("list", items);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /**
     * 最近 N 条（铃铛下拉用）
     */
    @GetMapping("/recent")
    public Result<List<Notification>> recent(@RequestParam Long receiverId,
                                             @RequestParam String receiverRole,
                                             @RequestParam(defaultValue = "5") Integer size) {
        if (size == null || size <= 0 || size > 20) {
            size = 5;
        }
        return Result.success(notificationService.recent(receiverId, receiverRole, size));
    }

    /**
     * 未读数（前端轮询用）
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(@RequestParam Long receiverId,
                                                    @RequestParam String receiverRole) {
        int n = notificationService.unreadCount(receiverId, receiverRole);
        Map<String, Object> data = new HashMap<>();
        data.put("count", n);
        return Result.success(data);
    }

    /**
     * 单条标记已读
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id,
                                 @RequestParam Long receiverId) {
        int affected = notificationService.markRead(id, receiverId);
        if (affected == 0) {
            return Result.error("消息不存在或不属于该用户");
        }
        return Result.success();
    }

    /**
     * 全部标记已读
     */
    @PostMapping("/read-all")
    public Result<Map<String, Object>> markAllRead(@RequestParam Long receiverId,
                                                    @RequestParam String receiverRole) {
        int affected = notificationService.markAllRead(receiverId, receiverRole);
        Map<String, Object> data = new HashMap<>();
        data.put("affected", affected);
        return Result.success(data);
    }

    /**
     * 软删除单条
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam Long receiverId) {
        int affected = notificationService.delete(id, receiverId);
        if (affected == 0) {
            return Result.error("消息不存在或不属于该用户");
        }
        return Result.success();
    }
}
