import type { W5FallbackSuggestion, W5Output, W5Suggestion } from '@/shared/api/modules/physician'

export function formatW5Confidence(value?: number): string {
  if (value == null || Number.isNaN(value)) return '-'
  const num = value <= 1 ? value * 100 : value
  return `${Math.round(num * 10) / 10}%`
}

export function w5StatusLabel(status?: W5Output['status']): string {
  if (status === 'success') return '荐药建议已生成'
  if (status === 'fallback') return '药品库未匹配，已给出兜底建议'
  return '待运行 W5'
}

export function sortW5Suggestions(list?: W5Suggestion[]): W5Suggestion[] {
  return [...(list ?? [])].sort((a, b) => {
    const aOrder = a.sortOrder ?? Number.MAX_SAFE_INTEGER
    const bOrder = b.sortOrder ?? Number.MAX_SAFE_INTEGER
    if (aOrder !== bOrder) return aOrder - bOrder
    const aConf = a.confidence ?? 0
    const bConf = b.confidence ?? 0
    return bConf - aConf
  })
}

export function hasW5PanelContent(
  liveOutput?: W5Output | null,
  savedSuggestions?: W5Suggestion[],
): boolean {
  if (liveOutput?.status === 'fallback') {
    return Boolean(liveOutput.fallbackSuggestions?.length || liveOutput.clinicalSummaryForDoctor)
  }
  if (liveOutput?.suggestions?.length) return true
  return Boolean(savedSuggestions?.length)
}

export function displayDrugName(item: W5Suggestion | W5FallbackSuggestion): string {
  return item.drugName || '-'
}

export function formatW5StockLabel(stock?: number, unit?: string): string {
  if (stock == null) return '加载中…'
  return `${stock} ${unit || '盒'}`
}

export function isW5LowStock(stock?: number, threshold?: number): boolean {
  if (stock == null) return false
  const limit = threshold != null && threshold > 0 ? threshold : 20
  return stock > 0 && stock <= limit
}

export function isW5OutOfStock(stock?: number): boolean {
  return stock != null && stock <= 0
}
