import type { W4FallbackSuggestion, W4Output, W4Suggestion } from '@/shared/api/modules/physician'

export function formatW4Probability(value?: number): string {
  if (value == null || Number.isNaN(value)) return '-'
  const num = value <= 1 ? value * 100 : value
  return `${Math.round(num * 10) / 10}%`
}

export function w4RiskLabel(level?: string): string {
  if (!level) return ''
  const normalized = level.trim().toLowerCase()
  if (normalized === 'high' || normalized === 'danger') return '高风险'
  if (normalized === 'medium' || normalized === 'warning' || normalized === 'attention') return '中风险'
  if (normalized === 'low' || normalized === 'normal') return '低风险'
  return level
}

export function w4RiskTone(level?: string): 'success' | 'warning' | 'danger' | 'info' {
  const normalized = String(level || '').trim().toLowerCase()
  if (normalized === 'high' || normalized === 'danger') return 'danger'
  if (normalized === 'medium' || normalized === 'warning' || normalized === 'attention') return 'warning'
  if (normalized === 'low' || normalized === 'normal') return 'success'
  return 'info'
}

export function w4StatusLabel(status?: W4Output['status']): string {
  if (status === 'success') return '诊断建议已生成'
  if (status === 'empty') return '暂无匹配建议'
  if (status === 'fallback') return '疾病库未匹配，已给出兜底建议'
  return '待运行 W4'
}

export function w4StatusTone(status?: W4Output['status']): 'success' | 'warning' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'fallback') return 'warning'
  return 'info'
}

export function suggestionDisplayName(item: W4Suggestion | W4FallbackSuggestion): string {
  return item.diagnosisName || (item as W4Suggestion).diseaseName || '-'
}

export function sortSuggestions(list?: W4Suggestion[]): W4Suggestion[] {
  return [...(list ?? [])].sort((a, b) => {
    const aOrder = a.sortOrder ?? Number.MAX_SAFE_INTEGER
    const bOrder = b.sortOrder ?? Number.MAX_SAFE_INTEGER
    if (aOrder !== bOrder) return aOrder - bOrder
    const aProb = a.probability ?? 0
    const bProb = b.probability ?? 0
    return bProb - aProb
  })
}

export function firstW4Suggestion(output?: W4Output | null): W4Suggestion | undefined {
  return sortSuggestions(output?.suggestions)[0]
}

export function hasW4PanelContent(liveOutput?: W4Output | null, savedSuggestions?: W4Suggestion[]): boolean {
  if (liveOutput) {
    return Boolean(
      liveOutput.clinicalSummaryForDoctor
      || liveOutput.warningSigns?.length
      || liveOutput.suggestions?.length
      || liveOutput.fallbackSuggestions?.length
      || liveOutput.differentialDiagnosis?.length
      || liveOutput.searchAdvice,
    )
  }
  return Boolean(savedSuggestions?.length)
}
