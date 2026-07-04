import { http } from '../request'
import type {
  NotificationItem,
  NotificationListResult,
  NotificationRole,
  NotificationType,
} from '@/shared/types/notification'

export interface NotificationListParams {
  receiverId: number
  receiverRole: NotificationRole
  type?: NotificationType
  isRead?: number
  page?: number
  size?: number
}

export const notificationApi = {
  /**
   * 分页查询消息列表
   */
  list(params: NotificationListParams) {
    return http<NotificationListResult>({
      url: '/notification/list',
      method: 'GET',
      params,
    })
  },

  /**
   * 最近 N 条（铃铛下拉用）
   */
  recent(receiverId: number, receiverRole: NotificationRole, size = 5) {
    return http<NotificationItem[]>({
      url: '/notification/recent',
      method: 'GET',
      params: { receiverId, receiverRole, size },
    })
  },

  /**
   * 未读数（前端轮询用）
   */
  unreadCount(receiverId: number, receiverRole: NotificationRole) {
    return http<{ count: number }>({
      url: '/notification/unread-count',
      method: 'GET',
      params: { receiverId, receiverRole },
    })
  },

  /**
   * 单条标记已读
   */
  markRead(id: number, receiverId: number) {
    return http<void>({
      url: `/notification/${id}/read`,
      method: 'POST',
      params: { receiverId },
    })
  },

  /**
   * 全部标记已读
   */
  markAllRead(receiverId: number, receiverRole: NotificationRole) {
    return http<{ affected: number }>({
      url: '/notification/read-all',
      method: 'POST',
      params: { receiverId, receiverRole },
    })
  },

  /**
   * 软删除单条
   */
  delete(id: number, receiverId: number) {
    return http<void>({
      url: `/notification/${id}/delete`,
      method: 'POST',
      params: { receiverId },
    })
  },
}
