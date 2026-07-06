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
  | 'PAYMENT_SUCCESS'
  | 'REFUND_SUCCESS'
  | 'EXAM_FEE_CREATED'

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

/**
 * 把后端 type 映射成中文标签（前端展示用）。
 * <p>注意：payment-service 写库的是大写（PAYMENT_SUCCESS/REFUND_SUCCESS），
 *        schedule/leave 写库的是小写蛇形 —— 这里统一映射，避免回退到原始 type。
 * <p>用 Record<string, string> 而非 Record<NotificationType, string>，是为了
 *        兜底未来新增类型时不报 TS 错（运行时仍走 `|| item.type` 回退）。
 */
export const NOTIFICATION_TYPE_LABEL: Record<string, string> = {
  doctor_change: '医生变更',
  leave_approved: '请假已批准',
  leave_rejected: '请假已拒绝',
  adjust_pending: '待确认调整',
  adjust_confirmed: '调整已确认',
  PAYMENT_SUCCESS: '支付成功',
  REFUND_SUCCESS: '退款成功',
  EXAM_FEE_CREATED: '待缴费提醒',
}
