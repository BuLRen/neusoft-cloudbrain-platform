package com.xikang.notification.service;

import com.xikang.notification.dto.NotificationSendRequest;
import com.xikang.notification.entity.Notification;
import com.xikang.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ==================== 写入（内部调用） ====================

    /**
     * 写入单条消息（其他业务服务通过 /api/notification/send 调用）。
     * <p>字段校验失败抛 RuntimeException，由 controller 包成 error Result。
     */
    @Transactional
    public Notification send(NotificationSendRequest req) {
        validate(req);
        Notification entity = toEntity(req);
        notificationMapper.insert(entity);
        log.info("通知已写入：receiver={}/{}, type={}, bizType={}/{}",
                req.getReceiverRole(), req.getReceiverId(),
                req.getType(), req.getBizType(), req.getBizId());
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
        return rows;
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
