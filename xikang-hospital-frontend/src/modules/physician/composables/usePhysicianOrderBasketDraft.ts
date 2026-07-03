import type { MedicalTechnology } from '@/shared/api/modules/physician'

export type OrderBasketTechType = MedicalTechnology['techType']

export interface OrderBasketItem {
  medicalTechnologyId?: number
  techName: string
  techType: OrderBasketTechType
  deptName?: string
  info: string
  position: string
  remark: string
}

const STORAGE_PREFIX = 'physician-order-basket:'

export function orderBasketDraftKey(registerId: number) {
  return `${STORAGE_PREFIX}${registerId}`
}

function isValidBasketItem(item: unknown): item is OrderBasketItem {
  if (!item || typeof item !== 'object') return false
  const row = item as Partial<OrderBasketItem>
  return (
    typeof row.techName === 'string' &&
    row.techName.trim().length > 0 &&
    typeof row.techType === 'string' &&
    (row.techType === 'check' || row.techType === 'inspection' || row.techType === 'disposal') &&
    typeof row.info === 'string' &&
    typeof row.position === 'string' &&
    typeof row.remark === 'string'
  )
}

export function loadOrderBasketDraft(registerId: number): OrderBasketItem[] {
  const raw = sessionStorage.getItem(orderBasketDraftKey(registerId))
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isValidBasketItem)
  } catch {
    return []
  }
}

export function saveOrderBasketDraft(registerId: number, items: OrderBasketItem[]) {
  if (!items.length) {
    clearOrderBasketDraft(registerId)
    return
  }
  sessionStorage.setItem(orderBasketDraftKey(registerId), JSON.stringify(items))
}

export function clearOrderBasketDraft(registerId: number) {
  sessionStorage.removeItem(orderBasketDraftKey(registerId))
}
