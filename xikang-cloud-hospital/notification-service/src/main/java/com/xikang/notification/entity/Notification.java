package com.xikang.notification.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一消息通知实体
 * <p>表 notification：承载患者/医生/管理员三方消息。
 */
@Data
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long receiverId;          // 接收者 ID
    private String receiverRole;      // patient / physician / admin
    private String type;              // doctor_change / leave_approved / ...
    private String title;
    private String content;
    private String bizType;           // 关联业务类型
    private Long bizId;               // 关联业务 ID
    private Integer isRead;           // 0=未读, 1=已读
    private Integer isDeleted;        // 0=正常, 1=已软删
    private LocalDateTime createdTime;
    private LocalDateTime updateTime;
}
