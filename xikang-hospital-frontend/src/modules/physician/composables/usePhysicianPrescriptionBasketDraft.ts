export interface PrescriptionBasketItem {
  drugId: number
  drugName: string
  drugPrice: number
  drugUsage: string
  drugNumber: number
}

const STORAGE_PREFIX = 'physician-prescription-basket:'

export function prescriptionBasketDraftKey(registerId: number) {
  return `${STORAGE_PREFIX}${registerId}`
}

function isValidBasketItem(item: unknown): item is PrescriptionBasketItem {
  if (!item || typeof item !== 'object') return false
  const row = item as Partial<PrescriptionBasketItem>
  return (
    typeof row.drugId === 'number' &&
    row.drugId > 0 &&
    typeof row.drugName === 'string' &&
    row.drugName.trim().length > 0 &&
    typeof row.drugPrice === 'number' &&
    row.drugPrice >= 0 &&
    typeof row.drugUsage === 'string' &&
    typeof row.drugNumber === 'number' &&
    row.drugNumber >= 1
  )
}

export function loadPrescriptionBasketDraft(registerId: number): PrescriptionBasketItem[] {
  const raw = sessionStorage.getItem(prescriptionBasketDraftKey(registerId))
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isValidBasketItem)
  } catch {
    return []
  }
}

export function savePrescriptionBasketDraft(registerId: number, items: PrescriptionBasketItem[]) {
  if (!items.length) {
    clearPrescriptionBasketDraft(registerId)
    return
  }
  sessionStorage.setItem(prescriptionBasketDraftKey(registerId), JSON.stringify(items))
}

export function clearPrescriptionBasketDraft(registerId: number) {
  sessionStorage.removeItem(prescriptionBasketDraftKey(registerId))
}
