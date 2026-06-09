export type ResultFormFieldType = 'text' | 'textarea' | 'number'

export interface ResultFormField {
  id?: number
  fieldKey: string
  fieldLabel: string
  fieldType: ResultFormFieldType
  required?: boolean
  sortOrder?: number
  placeholder?: string
  maxLength?: number
  optionsJson?: string
}

export interface ResultFormSchema {
  checkRequestId?: number
  categoryCode: string
  categoryName?: string
  medicalTechnologyId?: number
  techCode?: string
  techName?: string
  fields: ResultFormField[]
  baseFieldCount: number
  extensionFieldCount: number
  existingValues?: Record<string, unknown>
}

export interface ResultPayload {
  schemaVersion?: number
  categoryCode?: string
  medicalTechnologyId?: number
  techCode?: string
  techName?: string
  submittedAt?: string
  values?: Record<string, unknown>
  legacyText?: string
}

export function parseResultPayload(raw?: string | null): ResultPayload | null {
  if (!raw?.trim()) return null
  const trimmed = raw.trim()
  if (!trimmed.startsWith('{')) {
    return { legacyText: trimmed }
  }
  try {
    return JSON.parse(trimmed) as ResultPayload
  } catch {
    return { legacyText: trimmed }
  }
}

export function formatResultPayloadSummary(raw?: string | null): string {
  const payload = parseResultPayload(raw)
  if (!payload) return '-'
  if (payload.legacyText) return payload.legacyText
  const values = payload.values
  if (!values) return '-'
  for (const value of Object.values(values)) {
    if (value != null && String(value).trim()) {
      const text = String(value).trim()
      return text.length > 80 ? `${text.slice(0, 80)}…` : text
    }
  }
  return '-'
}

export function resultPayloadEntries(raw?: string | null): Array<{ key: string; label: string; value: string }> {
  const payload = parseResultPayload(raw)
  if (!payload) return []
  if (payload.legacyText) {
    return [{ key: 'checkResult', label: '检查结果', value: payload.legacyText }]
  }
  const values = payload.values ?? {}
  return Object.entries(values)
    .filter(([, value]) => value != null && String(value).trim())
    .map(([key, value]) => ({ key, label: key, value: String(value) }))
}
