export type ResultItemStatus = 'normal' | 'high' | 'low' | 'abnormal' | 'positive' | string

export interface SimulatedResultItem {
  itemCode: string
  itemName: string
  value: string | number
  unit: string
  referenceRange: string
  status: ResultItemStatus
  meaning: string
}

export interface SimulatedCheckStructuredOutput {
  checkName: string
  isNormal: boolean
  simulatedForDiseases: string[]
  resultItems: SimulatedResultItem[]
  conclusion: string
  notice: string
}

function parseIsNormalValue(raw: unknown): boolean {
  if (typeof raw === 'boolean') return raw
  if (raw == null) return false
  const text = String(raw).trim().toLowerCase()
  if (text === 'true' || text === '1' || text === 'yes') return true
  if (text === 'false' || text === '0' || text === 'no') return false
  return false
}

function pickStringField(row: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = row[key]
    if (value != null && String(value).trim() !== '') {
      return String(value).trim()
    }
  }
  return ''
}

/** 兼容 LLM / Dify 输出的多种字段名（如 reference、name、result） */
export function normalizeResultItemRow(row: Record<string, unknown>): SimulatedResultItem {
  const rawValue = row.value ?? row.result
  return {
    itemCode: pickStringField(row, ['itemCode', 'item_code', 'code']),
    itemName: pickStringField(row, ['itemName', 'name', 'item']),
    value: (rawValue === null || rawValue === undefined ? '' : rawValue) as string | number,
    unit: pickStringField(row, ['unit']),
    referenceRange: pickStringField(row, ['referenceRange', 'reference', 'refRange', 'reference_range']),
    status: pickStringField(row, ['status']) || 'normal',
    meaning: pickStringField(row, ['meaning', 'interpretation', 'clinicalMeaning', 'note']),
  }
}

function looksLikeStructuredOutput(data: Record<string, unknown>): boolean {
  if (Array.isArray(data.resultItems) && data.resultItems.length > 0) return true
  if (typeof data.checkName === 'string' && data.checkName.trim()) return true
  if (typeof data.conclusion === 'string' && data.conclusion.trim()) return true
  return false
}

function unwrapStructuredPayload(raw: unknown): Record<string, unknown> | null {
  if (typeof raw === 'string' && raw.trim().startsWith('{')) {
    try {
      return JSON.parse(raw) as Record<string, unknown>
    } catch {
      return null
    }
  }
  if (!raw || typeof raw !== 'object') return null
  const data = raw as Record<string, unknown>
  const nested = data.structured_output ?? data.structuredOutput
  if (nested && typeof nested === 'object') {
    return nested as Record<string, unknown>
  }
  if (typeof nested === 'string' && nested.trim().startsWith('{')) {
    try {
      return JSON.parse(nested) as Record<string, unknown>
    } catch {
      return null
    }
  }
  if (!looksLikeStructuredOutput(data)) {
    for (const wrapKey of ['value', 'data', 'content', 'output'] as const) {
      const unwrapped = unwrapStructuredPayload(data[wrapKey])
      if (unwrapped && looksLikeStructuredOutput(unwrapped)) return unwrapped
    }
  }
  return data
}

/** 优先解析结构化结果；若仅有结论文本也返回可展示对象 */
export function resolveSimulationDisplayOutput(
  raw: unknown,
  options?: { defaultCheckName?: string },
): SimulatedCheckStructuredOutput | null {
  const resolved = resolveStructuredOutput(raw)
  if (resolved && ((resolved.resultItems?.length ?? 0) > 0 || resolved.conclusion.trim() || resolved.checkName.trim())) {
    return resolved
  }
  if (!raw || typeof raw !== 'object') return resolved

  const record = raw as Record<string, unknown>
  const simulatedValues =
    record.simulatedValues && typeof record.simulatedValues === 'object'
      ? (record.simulatedValues as Record<string, unknown>)
      : null
  const conclusionFromText =
    typeof record.resultText === 'string' && record.resultText.trim() ? record.resultText.trim() : ''
  const conclusionFromForm =
    typeof simulatedValues?.checkResult === 'string' && simulatedValues.checkResult.trim()
      ? simulatedValues.checkResult.trim()
      : ''
  const conclusion = conclusionFromText || conclusionFromForm
  if (!conclusion) return resolved

  const noticeFromForm =
    typeof simulatedValues?.checkRemark === 'string' ? simulatedValues.checkRemark.trim() : ''

  return {
    checkName: resolved?.checkName || options?.defaultCheckName || '检查',
    isNormal: resolved?.isNormal ?? false,
    simulatedForDiseases: resolved?.simulatedForDiseases ?? [],
    resultItems: resolved?.resultItems ?? [],
    conclusion,
    notice: noticeFromForm || resolved?.notice || '',
  }
}

/** 从 simulate API 响应或 Dify 原始 outputs 中解析 structured_output */
export function resolveStructuredOutput(raw: unknown): SimulatedCheckStructuredOutput | null {
  if (!raw || typeof raw !== 'object') return null

  const record = raw as Record<string, unknown>
  const candidates: unknown[] = [
    record.structuredOutput,
    record.structured_output,
    record.resultText,
    raw,
  ]

  for (const candidate of candidates) {
    if (!candidate) continue
    const normalized = normalizeStructuredOutput(candidate)
    if (normalized) return normalized
    const wrapped = normalizeStructuredOutput({ structured_output: candidate })
    if (wrapped) return wrapped
  }

  return null
}

export function normalizeStructuredOutput(raw: unknown): SimulatedCheckStructuredOutput | null {
  const data = unwrapStructuredPayload(raw)
  if (!data) return null

  const resultItems = Array.isArray(data.resultItems)
    ? data.resultItems
        .filter((item) => item && typeof item === 'object')
        .map((item) => normalizeResultItemRow(item as Record<string, unknown>))
    : []

  const checkName = String(data.checkName ?? '')
  if (!checkName && resultItems.length === 0) return null

  return {
    checkName,
    isNormal: parseIsNormalValue(data.isNormal),
    simulatedForDiseases: Array.isArray(data.simulatedForDiseases)
      ? data.simulatedForDiseases.map((item) => String(item))
      : [],
    resultItems,
    conclusion: String(data.conclusion ?? ''),
    notice: String(data.notice ?? ''),
  }
}

export function statusLabel(status: string): string {
  switch (status) {
    case 'high':
      return '偏高'
    case 'low':
      return '偏低'
    case 'abnormal':
      return '异常'
    case 'positive':
      return '阳性'
    case 'normal':
      return '正常'
    default:
      return status || '正常'
  }
}

/** 从已提交的 inspectionResult / checkResult JSON 中解析 structuredOutput */
export function resolveStructuredOutputFromPayload(raw?: string | null): SimulatedCheckStructuredOutput | null {
  if (!raw?.trim()) return null
  const trimmed = raw.trim()
  if (trimmed.startsWith('{')) {
    try {
      const payload = JSON.parse(trimmed) as Record<string, unknown>
      if (payload.structuredOutput != null) {
        const normalized = normalizeStructuredOutput(payload.structuredOutput)
        if (normalized) return normalized
      }
    } catch {
      // fall through to generic resolver
    }
  }
  return resolveStructuredOutput(raw)
}

export function hasExportableLabReportPayload(raw?: string | null): boolean {
  const structured = resolveStructuredOutputFromPayload(raw)
  return Boolean(structured && (structured.resultItems?.length ?? 0) > 0)
}

/** 判断是否为医技模拟工作流草稿（未完成提交） */
export function isDraftResultPayload(raw?: string | null): boolean {
  if (!raw?.trim().startsWith('{')) return false
  try {
    const payload = JSON.parse(raw) as Record<string, unknown>
    return payload.draft === true
  } catch {
    return false
  }
}

export function statusTone(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'high' || status === 'abnormal' || status === 'positive') return 'danger'
  if (status === 'low') return 'warning'
  if (status === 'normal') return 'success'
  return 'info'
}
