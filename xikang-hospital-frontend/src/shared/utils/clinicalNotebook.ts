import type { ClinicalExamItem } from '@/shared/api/modules/clinicalRecord'
import { formatPrimaryResultSummary, parseResultPayload } from '@/shared/types/resultForm'
import {
  hasExportableLabReportPayload,
  resolveSimulationDisplayOutput,
  resolveStructuredOutputFromPayload,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'

export type ExamStateTone = 'primary' | 'success' | 'warning' | 'neutral'

export function resolveExamStateTone(state: string): ExamStateTone {
  if (state === '已完成' || state === '已归档') return 'success'
  if (state === '检查中' || state === '检验中') return 'warning'
  if (state === '待检查' || state === '待检验') return 'primary'
  return 'neutral'
}

export function examCategoryLabel(category: ClinicalExamItem['category']): string {
  return category === 'inspection' ? '检验' : '检查'
}

export function hasExamResultPayload(raw?: string | null): boolean {
  if (!raw?.trim()) return false
  const text = raw.trim()
  return text !== 'null' && text !== 'undefined'
}

export function isExamCompleted(state: string): boolean {
  return state === '已完成' || state === '已归档'
}

export function canViewFullExamResult(item: ClinicalExamItem): boolean {
  if (!hasExamResultPayload(item.resultRaw)) return false
  if (item.state === '已归档') return true
  return isExamCompleted(item.state)
}

export function resolveExamStructuredOutput(item: ClinicalExamItem): SimulatedCheckStructuredOutput | null {
  if (!item.resultRaw) return null
  const structured = resolveStructuredOutputFromPayload(item.resultRaw)
  if (structured) return structured
  return resolveSimulationDisplayOutput(item.resultRaw, { defaultCheckName: item.techName || '检查' })
}

export function hasStructuredExamResult(item: ClinicalExamItem): boolean {
  const structured = resolveExamStructuredOutput(item)
  if (!structured) return false
  if ((structured.resultItems?.length ?? 0) > 0) return true
  return Boolean(structured.conclusion?.trim() || structured.checkName?.trim())
}

export function hasExamAiAnalysis(item: ClinicalExamItem): boolean {
  return Boolean(item.aiAnalysis?.trim())
}

export function isCtExamItem(item: ClinicalExamItem): boolean {
  if (item.category !== 'check') return false
  if (/CT/i.test(item.techName || '')) return true
  const payload = parseResultPayload(item.resultRaw)
  return Boolean(payload?.categoryCode?.startsWith('imaging_ct'))
}

export function canExportExamItemPdf(item: ClinicalExamItem): boolean {
  if (item.category !== 'inspection') return false
  if (!canViewFullExamResult(item)) return false
  return hasExportableLabReportPayload(item.resultRaw)
}

export function getCompletedExamItemsForExportSelection(items: ClinicalExamItem[]): ClinicalExamItem[] {
  return items.filter(canViewFullExamResult)
}

export function describeExamItemExportCapability(
  item: ClinicalExamItem,
  mode: 'physician' | 'patient',
): { exportable: boolean; reason?: string } {
  if (!canViewFullExamResult(item)) {
    return { exportable: false, reason: '暂无结果' }
  }
  if (item.category === 'inspection') {
    if (!hasExportableLabReportPayload(item.resultRaw)) {
      return { exportable: false, reason: '无结构化检验明细' }
    }
    return { exportable: true }
  }
  if (isCtExamItem(item)) {
    const payload = parseResultPayload(item.resultRaw)
    if (mode !== 'physician' && (!payload?.values || !Object.keys(payload.values).length)) {
      return { exportable: false, reason: '无诊断报告内容' }
    }
    return { exportable: true }
  }
  const structured = resolveExamStructuredOutput(item)
  if (structured && ((structured.resultItems?.length ?? 0) > 0 || structured.conclusion?.trim())) {
    return { exportable: true }
  }
  return { exportable: false, reason: '暂无可导出格式' }
}

export function formatExamResultSummary(item: ClinicalExamItem): string {
  if (!item.resultRaw) return item.resultSummary || '—'
  const primaryKey = item.category === 'inspection' ? 'inspectionResult' : 'checkResult'
  const summary = formatPrimaryResultSummary(item.resultRaw, primaryKey)
  return summary === '-' ? (item.resultSummary || '—') : summary
}

export function displayText(value?: string | null, fallback = '—'): string {
  const text = value?.trim()
  return text ? text : fallback
}

export function formatVisitDate(value?: string): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
