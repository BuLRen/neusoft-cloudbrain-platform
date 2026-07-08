package com.xikang.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.notification.dto.NotificationSendRequest;
import com.xikang.notification.entity.Notification;
import com.xikang.notification.mapper.NotificationMapper;
import com.xikang.notification.websocket.UserSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 写入（内部调用） ====================

    /**
     * 写入单条消息（其他业务服务通过 /api/notification/send 调用）。
     * <p>字段校验失败抛 RuntimeException，由 controller 包成 error Result。
     * <p>写入成功后通过 WebSocket 异步推送给在线连接；推送在事务提交后执行（afterCommit），
     * 避免业务回滚后误推；推送失败仅记日志，不影响主流程。
     */
    @Transactional
    public Notification send(NotificationSendRequest req) {
        validate(req);
        Notification entity = toEntity(req);
        notificationMapper.insert(entity);
        log.info("通知已写入：receiver={}/{}, type={}, bizType={}/{}",
                req.getReceiverRole(), req.getReceiverId(),
                req.getType(), req.getBizType(), req.getBizId());
        registerPushAfterCommit(entity);
        return entity;
    }

    /**
     * 批量写入（同内容群发，例如给同一挂号 schedule 下的所有患者发"医生变更"通知）。
     */
    @Transactional
    public int batchSend(List<NotificationSendRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return 0;
        }
        for (NotificationSendRequest req : reqs) {
            validate(req);
        }
        List<Notification> entities = reqs.stream().map(this::toEntity).toList();
        int rows = notificationMapper.batchInsert(entities);
        log.info("批量通知已写入：{} 条（type={}, bizType={}/{}）",
                rows, reqs.get(0).getType(),
                reqs.get(0).getBizType(), reqs.get(0).getBizId());
        for (Notification entity : entities) {
            registerPushAfterCommit(entity);
        }
        return rows;
    }

    /**
     * 注册事务提交后推送钩子。若当前无事务上下文（例如被 controller 直接同步调用），
     * 则立即推送。
     */
    private void registerPushAfterCommit(Notification entity) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushSafely(entity);
                }
            });
        } else {
            pushSafely(entity);
        }
    }

    private void pushSafely(Notification entity) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "notification");
            payload.put("id", entity.getId());
            payload.put("receiverId", entity.getReceiverId());
            payload.put("receiverRole", entity.getReceiverRole());
            payload.put("type", entity.getType());
            payload.put("title", entity.getTitle());
            payload.put("content", entity.getContent());
            payload.put("bizType", entity.getBizType());
            payload.put("bizId", entity.getBizId());
            payload.put("isRead", entity.getIsRead());
            payload.put("createdTime", entity.getCreatedTime() != null
                    ? entity.getCreatedTime().toString() : null);
            String json = objectMapper.writeValueAsString(payload);
            int pushed = sessionRegistry.sendToUser(entity.getReceiverId(), entity.getReceiverRole(), json);
            if (pushed > 0) {
                log.info("WS 推送成功：receiver={}/{}, type={}, sessions={}",
                        entity.getReceiverRole(), entity.getReceiverId(), entity.getType(), pushed);
            }
        } catch (Exception e) {
            log.warn("WS 推送失败（不影响主流程）：receiver={}/{}, err={}",
                    entity.getReceiverRole(), entity.getReceiverId(), e.getMessage());
        }
    }

    // ==================== 查询 ====================

    public List<Notification> list(Long receiverId, String receiverRole,
                                   String type, Integer isRead,
                                   int page, int size) {
        int offset = Math.max(page - 1, 0) * size;
        return notificationMapper.selectList(receiverId, receiverRole, type, isRead, offset, size);
    }

    public long count(Long receiverId, String receiverRole,
                      String type, Integer isRead) {
        return notificationMapper.countList(receiverId, receiverRole, type, isRead);
    }

    public List<Notification> recent(Long receiverId, String receiverRole, int size) {
        return notificationMapper.selectRecent(receiverId, receiverRole, Math.min(size, 20));
    }

    public int unreadCount(Long receiverId, String receiverRole) {
        return notificationMapper.countUnread(receiverId, receiverRole);
    }

    // ==================== 已读 / 删除 ====================

    @Transactional
    public int markRead(Long id, Long receiverId) {
        return notificationMapper.markRead(id, receiverId, LocalDateTime.now());
    }

    @Transactional
    public int markAllRead(Long receiverId, String receiverRole) {
        return notificationMapper.markAllRead(receiverId, receiverRole, LocalDateTime.now());
    }

    @Transactional
    public int delete(Long id, Long receiverId) {
        return notificationMapper.softDelete(id, receiverId);
    }

    // ==================== 内部辅助 ====================

    private void validate(NotificationSendRequest req) {
        if (req.getReceiverId() == null) {
            throw new RuntimeException("receiverId 不能为空");
        }
        if (!"patient".equals(req.getReceiverRole())
                && !"physician".equals(req.getReceiverRole())
                && !"admin".equals(req.getReceiverRole())) {
            throw new RuntimeException("receiverRole 非法：" + req.getReceiverRole());
        }
        if (req.getType() == null || req.getType().isBlank()) {
            throw new RuntimeException("type 不能为空");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new RuntimeException("title 不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new RuntimeException("content 不能为空");
        }
    }

    private Notification toEntity(NotificationSendRequest req) {
        Notification n = new Notification();
        n.setReceiverId(req.getReceiverId());
        n.setReceiverRole(req.getReceiverRole());
        n.setType(req.getType());
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setBizType(req.getBizType());
        n.setBizId(req.getBizId());
        return n;
    }
}
