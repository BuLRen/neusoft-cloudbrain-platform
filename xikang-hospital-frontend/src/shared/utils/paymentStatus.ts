import type { PaymentOrder } from '@/shared/types/payment'

export type StatusTone = 'primary' | 'success' | 'warning' | 'danger' | 'neutral' | 'ai'

export const ITEM_CODE_LABELS: Record<string, string> = {
  REGISTRATION_FEE: '挂号费',
  MEDICATION_FEE: '药品费',
  EXAMINATION_FEE: '检查检验费',
  DISPOSAL_FEE: '处置费',
  CHECK_FEE: '检查费',
}

export function itemCodeLabel(code?: string): string {
  if (!code) return '—'
  return ITEM_CODE_LABELS[code] ?? code
}

export function orderStatusTone(o: Pick<PaymentOrder, 'status'>): StatusTone {
  if (o.status === 1) return 'success'
  if (o.status === 2) return 'neutral'
  return 'warning'
}

export function orderStatusText(o: Pick<PaymentOrder, 'status' | 'statusName' | 'paidItemCount' | 'pendingItemCount'>): string {
  if (o.status === 0 && (o.paidItemCount ?? 0) > 0 && (o.pendingItemCount ?? 0) > 0) {
    return '部分已缴'
  }
  return o.statusName || '—'
}

export function itemStatusTone(status?: number): StatusTone {
  if (status === 1) return 'success'
  if (status === 2) return 'neutral'
  if (status === 3) return 'neutral'
  return 'warning'
}

export function itemStatusText(status?: number): string {
  switch (status) {
    case 0: return '待缴费'
    case 1: return '已缴费'
    case 2: return '已退款'
    case 3: return '已作废'
    default: return '—'
  }
}

export function formatPaymentTime(value?: string | null): string {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

export function formatMoney(value?: number | null): string {
  return `¥ ${(value ?? 0).toFixed(2)}`
}
