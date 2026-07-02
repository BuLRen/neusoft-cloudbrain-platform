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
  inspectionRequestId?: number
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

const CT_FIELD_LABELS: Record<string, string> = {
  findings: '所见',
  impression: '印象',
  conclusion: '结论',
}

export function resolveResultFieldLabel(categoryCode: string | undefined, fieldKey: string): string {
  if (categoryCode === 'imaging_ct' || fieldKey in CT_FIELD_LABELS) {
    return CT_FIELD_LABELS[fieldKey] ?? fieldKey
  }
  return fieldKey
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

/** 表格「结果摘要」列：仅展示主结果字段（如 inspectionResult / checkResult），不含备注等附属字段 */
export function formatPrimaryResultSummary(
  raw?: string | null,
  primaryKey: 'checkResult' | 'inspectionResult' = 'checkResult',
): string {
  const payload = parseResultPayload(raw)
  if (!payload) return '-'
  if (payload.legacyText) return payload.legacyText

  const primary = payload.values?.[primaryKey]
  if (primary != null && String(primary).trim()) {
    return String(primary).trim()
  }

  if (payload.categoryCode === 'imaging_ct' && payload.values) {
    for (const key of ['conclusion', 'impression', 'findings'] as const) {
      const value = payload.values[key]
      if (value != null && String(value).trim()) {
        const text = String(value).trim()
        return text.length > 80 ? `${text.slice(0, 80)}…` : text
      }
    }
  }

  if (raw?.trim().startsWith('{')) {
    try {
      const data = JSON.parse(raw.trim()) as Record<string, unknown>
      const structured = data.structuredOutput ?? data.structured_output
      if (structured && typeof structured === 'object') {
        const conclusion = (structured as Record<string, unknown>).conclusion
        if (conclusion != null && String(conclusion).trim()) {
          return String(conclusion).trim()
        }
      }
    } catch {
      // ignore malformed JSON
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
  const categoryCode = payload.categoryCode
  return Object.entries(values)
    .filter(([, value]) => value != null && String(value).trim())
    .map(([key, value]) => ({
      key,
      label: resolveResultFieldLabel(categoryCode, key),
      value: String(value),
    }))
}
