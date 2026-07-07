package com.xikang.notification.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 列表查询参数（前端用户读消息用）
 */
@Data
public class NotificationQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long receiverId;
    private String receiverRole;
    private String type;       // 可选过滤
    private Integer isRead;    // 可选过滤：0=未读，1=已读
    private Integer page = 1;
    private Integer size = 20;
}
