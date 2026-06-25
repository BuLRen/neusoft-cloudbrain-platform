import type { W3ExamSummary, W3IndicatorRow, W3Output } from '@/shared/api/modules/physician'

const ABNORMAL_STATUSES = new Set(['high', 'low', 'abnormal', 'positive', 'h', 'l'])

export function isAbnormalStatus(status?: string): boolean {
  if (!status) return false
  return ABNORMAL_STATUSES.has(status.trim().toLowerCase())
}

export function partitionIndicatorRows(rows?: W3IndicatorRow[]) {
  const list = rows ?? []
  return {
    abnormal: list.filter(row => isAbnormalStatus(row.status)),
    normal: list.filter(row => !isAbnormalStatus(row.status)),
  }
}

const RISK_ORDER: Record<string, number> = {
  high: 0,
  attention: 1,
  warning: 1,
  danger: 0,
  normal: 2,
}

export function sortExamSummaries(summaries?: W3ExamSummary[]): W3ExamSummary[] {
  return [...(summaries ?? [])].sort((a, b) => {
    const aOrder = RISK_ORDER[String(a.riskLevel || 'normal').toLowerCase()] ?? 2
    const bOrder = RISK_ORDER[String(b.riskLevel || 'normal').toLowerCase()] ?? 2
    return aOrder - bOrder
  })
}

export function countRiskLevels(summaries?: W3ExamSummary[]) {
  let attention = 0
  let normal = 0
  for (const item of summaries ?? []) {
    const level = String(item.riskLevel || 'normal').toLowerCase()
    if (level === 'high' || level === 'attention' || level === 'warning' || level === 'danger') {
      attention += 1
    } else {
      normal += 1
    }
  }
  return { attention, normal }
}

export function hasW3Content(data?: W3Output | null): boolean {
  if (!data) return false
  return Boolean(
    data.clinicalImpression?.trim() ||
      data.overallAnalysis?.trim() ||
      (data.examSummaries?.length ?? 0) > 0,
  )
}

export function hasIndicatorTable(summary?: W3ExamSummary): boolean {
  return (summary?.indicatorRows?.length ?? 0) > 0
}
