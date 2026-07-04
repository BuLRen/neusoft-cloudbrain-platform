package com.xikang.notification.mapper;

import com.xikang.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知表 Mapper
 */
public interface NotificationMapper {

    /** 单条插入 */
    int insert(Notification notification);

    /** 批量插入（患者群发场景） */
    int batchInsert(@Param("list") List<Notification> list);

    /** 列表查询（按 receiver 过滤，可选 type/isRead，排除软删） */
    List<Notification> selectList(@Param("receiverId") Long receiverId,
                                  @Param("receiverRole") String receiverRole,
                                  @Param("type") String type,
                                  @Param("isRead") Integer isRead,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /** 列表总数（用于分页） */
    long countList(@Param("receiverId") Long receiverId,
                   @Param("receiverRole") String receiverRole,
                   @Param("type") String type,
                   @Param("isRead") Integer isRead);

    /** 取最近 N 条（下拉用） */
    List<Notification> selectRecent(@Param("receiverId") Long receiverId,
                                    @Param("receiverRole") String receiverRole,
                                    @Param("size") int size);

    /** 未读数 */
    int countUnread(@Param("receiverId") Long receiverId,
                    @Param("receiverRole") String receiverRole);

    /** 单条标记已读 */
    int markRead(@Param("id") Long id,
                 @Param("receiverId") Long receiverId,
                 @Param("updateTime") LocalDateTime updateTime);

    /** 全部标记已读（同一接收者） */
    int markAllRead(@Param("receiverId") Long receiverId,
                    @Param("receiverRole") String receiverRole,
                    @Param("updateTime") LocalDateTime updateTime);

    /** 软删除单条 */
    int softDelete(@Param("id") Long id,
                   @Param("receiverId") Long receiverId);

    /** 单条查询（用于 send 后回查 / 详情） */
    Notification selectById(@Param("id") Long id);
}
