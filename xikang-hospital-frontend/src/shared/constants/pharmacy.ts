/**
 * 药房模块枚举常量
 */

/** 剂型 */
export const DOSAGE_FORMS = [
  '片剂',
  '胶囊',
  '注射液',
  '颗粒',
  '口服液',
  '软膏',
  '滴眼液',
  '吸入剂',
  '贴剂',
  '其他',
] as const

/** 交易流水类型 */
export const TRANSACTION_TYPES = ['发放', '退回', '入库', '盘点', '报损'] as const

/** 发药状态 */
export const DISPENSATION_STATUS = {
  PENDING: 0,
  DISPENSED: 1,
  RETURNED: 2,
} as const

export function dispensationStatusName(status?: number): string {
  switch (status) {
    case DISPENSATION_STATUS.DISPENSED:
      return '已发药'
    case DISPENSATION_STATUS.RETURNED:
      return '已退药'
    case DISPENSATION_STATUS.PENDING:
      return '待发药'
    default:
      return '未知'
  }
}

/** 审方结果状态 */
export const REVIEW_STATUS_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  pass: 'success',
  warn: 'warning',
  block: 'danger',
}

/** 近效期阈值（天数） */
export const NEAR_EXPIRY_DAYS = 30
export const CRITICAL_EXPIRY_DAYS = 7

/** 退药原因预设 */
export const RETURN_REASONS = [
  '患者申请退药',
  '药物不良反应',
  '医生改方',
  '药品质量原因',
  '其他',
] as const

/** 随访类型 */
export const FOLLOW_UP_TYPES = [
  'MEDICATION_REMINDER',
  'EFFECT_TRACKING',
  'RECOVERY_ASSESSMENT',
] as const

export const FOLLOW_UP_TYPE_LABELS: Record<string, string> = {
  MEDICATION_REMINDER: '用药提醒',
  EFFECT_TRACKING: '效果追踪',
  RECOVERY_ASSESSMENT: '康复评估',
}

/** 用药依从性 */
export const MEDICATION_ADHERENCE = ['COMPLIANT', 'PARTIAL', 'NON_COMPLIANT'] as const
export const MEDICATION_ADHERENCE_LABELS: Record<string, string> = {
  COMPLIANT: '依从',
  PARTIAL: '部分依从',
  NON_COMPLIANT: '不依从',
}

/** 分页默认 */
export const PAGE_SIZE_DEFAULT = 20
