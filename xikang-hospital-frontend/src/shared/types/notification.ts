/**
 * 通知消息相关类型
 */

export type NotificationRole = 'patient' | 'physician' | 'admin'

export type NotificationType =
  | 'doctor_change'
  | 'leave_approved'
  | 'leave_rejected'
  | 'adjust_pending'
  | 'adjust_confirmed'

export interface NotificationItem {
  id: number
  receiverId: number
  receiverRole: NotificationRole
  type: NotificationType
  title: string
  content: string
  bizType?: string
  bizId?: number
  isRead: number  // 0=未读 1=已读（后端 SMALLINT）
  isDeleted: number
  createdTime: string
  updateTime?: string
}

export interface NotificationListResult {
  list: NotificationItem[]
  total: number
  page: number
  size: number
}

export interface NotificationUnreadCount {
  count: number
}

/** 把后端 type 映射成中文标签（前端展示用） */
export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  doctor_change: '医生变更',
  leave_approved: '请假已批准',
  leave_rejected: '请假已拒绝',
  adjust_pending: '待确认调整',
  adjust_confirmed: '调整已确认',
}
