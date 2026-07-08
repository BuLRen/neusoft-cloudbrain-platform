// 检查/检验结果结构化解析工具（从 web 端 simulatedCheckResult.ts + clinicalNotebook.ts + resultForm.ts 移植）
// 后端通过 examItems[i].resultRaw (JSON 字符串) 携带结构化数据，本模块负责解析为可渲染结构。

export type ResultItemStatus = 'normal' | 'high' | 'low' | 'abnormal' | 'positive' | string

// ============== 表单结果载荷解析（resultForm.ts 移植）==============

const CT_FIELD_LABELS: Record<string, string> = {
  findings: '所见',
  impression: '印象',
  conclusion: '结论',
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

export interface ResultPayloadEntry {
  key: string
  label: string
  value: string
}

export function resolveResultFieldLabel(categoryCode: string | undefined, fieldKey: string): string {
  if (categoryCode === 'imaging_ct' || fieldKey in CT_FIELD_LABELS) {
    return CT_FIELD_LABELS[fieldKey] ?? fieldKey
  }
  return fieldKey
}

/** 解析 resultRaw：非 JSON 文本归入 legacyText，JSON 解析失败也归入 legacyText */
export function parseResultPayload(raw?: string | null): ResultPayload | null {
  if (!raw?.trim()) return null
  const trimmed = raw.trim()
  if (trimmed === 'null' || trimmed === 'undefined') return null
  if (!trimmed.startsWith('{')) {
    return { legacyText: trimmed }
  }
  try {
    return JSON.parse(trimmed) as ResultPayload
  } catch {
    return { legacyText: trimmed }
  }
}

/** 把 resultRaw 解析为表单字段值列表（患者端病历本风格）*/
export function resultPayloadEntries(raw?: string | null): ResultPayloadEntry[] {
  const payload = parseResultPayload(raw)
  if (!payload) return []
  if (payload.legacyText) {
    return [{ key: 'checkResult', label: '检查结果', value: payload.legacyText }]
  }
  const values = payload.values ?? {}
  const categoryCode = payload.categoryCode
  return Object.entries(values)
    .filter(([, value]) => value != null && String(value).trim() !== '')
    .map(([key, value]) => ({
      key,
      label: resolveResultFieldLabel(categoryCode, key),
      value: String(value),
    }))
}

/** 概要展示用：仅取主结果字段，避免把整个 JSON 字符串当摘要 */
export function formatPrimaryResultSummary(
  raw?: string | null,
  primaryKey: 'checkResult' | 'inspectionResult' = 'checkResult',
): string {
  const payload = parseResultPayload(raw)
  if (!payload) return ''
  if (payload.legacyText) return payload.legacyText
  const primary = payload.values?.[primaryKey]
  if (primary != null && String(primary).trim() !== '') {
    return String(primary).trim()
  }
  if (payload.categoryCode === 'imaging_ct' && payload.values) {
    for (const key of ['conclusion', 'impression', 'findings'] as const) {
      const value = payload.values[key]
      if (value != null && String(value).trim() !== '') {
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
        if (conclusion != null && String(conclusion).trim() !== '') {
          return String(conclusion).trim()
        }
      }
    } catch {
      // ignore
    }
  }
  return ''
}

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

interface ExamItemLike {
  resultRaw?: string | null
  resultSummary?: string
  techName?: string
  category?: 'check' | 'inspection'
}

function parseIsNormalValue(raw: unknown): boolean {
  if (typeof raw === 'boolean') return raw
  if (raw == null) return false
  const text = String(raw).trim().toLowerCase()
  if (text === 'true' || text === '1' || text === 'yes') return true
  return false
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

export function normalizeStructuredOutput(raw: unknown): SimulatedCheckStructuredOutput | null {
  const data = unwrapStructuredPayload(raw)
  if (!data) return null

  const resultItems: SimulatedResultItem[] = Array.isArray(data.resultItems)
    ? data.resultItems
        .filter((item) => item && typeof item === 'object')
        .map((item) => {
          const row = item as Record<string, unknown>
          return {
            itemCode: String(row.itemCode ?? ''),
            itemName: String(row.itemName ?? ''),
            value: (row.value === null || row.value === undefined ? '' : row.value) as string | number,
            unit: String(row.unit ?? ''),
            referenceRange: String(row.referenceRange ?? ''),
            status: String(row.status ?? 'normal'),
            meaning: String(row.meaning ?? ''),
          }
        })
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

/** 优先从 resultRaw(JSON 字符串) 解析 structuredOutput；兼容 Dify 形态 */
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
      // payload 自身可能就是结构化对象
      const direct = normalizeStructuredOutput(payload)
      if (direct) return direct
    } catch {
      // fall through
    }
  }
  return null
}

/** 综合 resultRaw + resultSummary 兜底，返回可展示对象。
 *  仅当 resultRaw 携带真正的结构化数据（structuredOutput / structured_output）时返回非 null；
 *  若 resultRaw 仅有结论文本或不可解析，则返回 null，让上层走 resultSummary 字符串回退路径。
 */
export function resolveExamStructuredOutput(item: ExamItemLike): SimulatedCheckStructuredOutput | null {
  if (!item.resultRaw) return null
  const structured = resolveStructuredOutputFromPayload(item.resultRaw)
  return structured
}

export function hasStructuredExamResult(item: ExamItemLike): boolean {
  const structured = resolveExamStructuredOutput(item)
  if (!structured) return false
  if ((structured.resultItems?.length ?? 0) > 0) return true
  return Boolean(structured.conclusion?.trim() || structured.checkName?.trim())
}

export function statusLabel(status: ResultItemStatus): string {
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
      return status ? String(status) : '正常'
  }
}

export type StatusTone = 'success' | 'warning' | 'danger' | 'info'

export function statusTone(status: ResultItemStatus): StatusTone {
  if (status === 'high' || status === 'abnormal' || status === 'positive') return 'danger'
  if (status === 'low') return 'warning'
  if (status === 'normal') return 'success'
  return 'info'
}

/** 把异常指标置顶，方便小程序优先展示 */
export function sortItemsByAbnormal(items: SimulatedResultItem[]): SimulatedResultItem[] {
  const abnormal = items.filter((item) => item.status !== 'normal' && item.status !== '')
  const normal = items.filter((item) => item.status === 'normal' || item.status === '')
  return [...abnormal, ...normal]
}
