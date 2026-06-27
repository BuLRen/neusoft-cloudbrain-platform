import type { FollowUpDayScheduleItem, FollowUpPriorityLevel } from '@/shared/types/medtechFollowUp'

export type FollowUpScheduleVisualStatus = 'planned' | 'completed' | 'overdue' | 'custom'

/** 日历/日程列表中的访谈状态着色：过去未完成=红，已完成=绿 */
export function resolveInterviewScheduleVisualStatus(
  item: FollowUpDayScheduleItem,
  dateYmd: string,
  todayYmd: string,
): FollowUpScheduleVisualStatus {
  if (item.itemType === 'custom') return 'custom'
  if (item.itemType !== 'interview') return 'planned'
  if (item.status === 'completed') return 'completed'
  if (dateYmd < todayYmd && item.status === 'planned') return 'overdue'
  return 'planned'
}

export const FOLLOW_UP_PRIORITY_LABELS: Record<FollowUpPriorityLevel, string> = {
  normal: '常规',
  high: '重点关注',
  critical: '重点患者',
}

export function monthRangeYmd(year: number, month: number) {
  const from = `${year}-${String(month).padStart(2, '0')}-01`
  const lastDay = new Date(year, month, 0).getDate()
  const to = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  return { from, to }
}

export function buildMonthCells(year: number, month: number, todayYmd: string) {
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const leading = firstWeekday === 0 ? 6 : firstWeekday - 1
  const daysInMonth = new Date(year, month, 0).getDate()
  const cells: Array<{ date: string; inMonth: boolean; isToday: boolean }> = []

  for (let i = leading; i > 0; i -= 1) {
    const d = new Date(year, month - 1, 1 - i)
    const ymd = formatYmd(d)
    cells.push({ date: ymd, inMonth: false, isToday: ymd === todayYmd })
  }
  for (let day = 1; day <= daysInMonth; day += 1) {
    const ymd = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    cells.push({ date: ymd, inMonth: true, isToday: ymd === todayYmd })
  }
  while (cells.length % 7 !== 0) {
    const offset = cells.length - daysInMonth - leading + 1
    const d = new Date(year, month, offset)
    const ymd = formatYmd(d)
    cells.push({ date: ymd, inMonth: false, isToday: ymd === todayYmd })
  }
  return cells
}

function formatYmd(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
