export type OutcomeRangePreset = '7d' | '30d' | '365d'

export const OUTCOME_RANGE_OPTIONS: { label: string; value: OutcomeRangePreset }[] = [
  { label: '最近七天', value: '7d' },
  { label: '最近一个月', value: '30d' },
  { label: '最近一年', value: '365d' },
]

/** 北京时间当天，返回 YYYY-MM-DD */
export function beijingTodayYmd(): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
}

/** 北京时间日期加减天数 */
export function beijingYmdAddDays(days: number, baseYmd = beijingTodayYmd()): string {
  const [y, m, d] = baseYmd.split('-').map(Number)
  const utc = Date.UTC(y!, m! - 1, d!)
  const next = new Date(utc + days * 86_400_000)
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(next)
}

export function resolveOutcomeRange(preset: OutcomeRangePreset) {
  const to = beijingTodayYmd()
  const span = preset === '7d' ? 6 : preset === '30d' ? 29 : 364
  const from = beijingYmdAddDays(-span, to)
  return { from, to, preset }
}

/** 展示用：YYYY-MM-DD HH:mm（北京时间） */
export function formatBeijingDateTime(value?: string | null): string {
  if (!value) return '—'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
