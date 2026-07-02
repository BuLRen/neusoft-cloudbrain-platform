/**
 * 排班课程表页面的设计 token 与类型映射
 *
 * 视觉立意：融入系统蓝色玻璃主题。"今天"用琥珀金（warning-strong）作为
 * 唯一高饱和强调，其余色彩派生自全局 tokens.css 的蓝色系。
 */

/** 班次类型 → 色票（背景 / 文字 / 边框） */
export interface ScheduleTypeColor {
  bg: string
  text: string
  border: string
  dot: string
}

/** 按挂号级别名称匹配班次类型色 */
export function resolveScheduleTypeColor(registLevelName?: string): ScheduleTypeColor {
  if (!registLevelName) return SCHEDULE_TYPE_COLORS.normal
  const name = registLevelName.trim()
  if (name.includes('专家')) return SCHEDULE_TYPE_COLORS.expert
  if (name.includes('特需') || name.includes('VIP')) return SCHEDULE_TYPE_COLORS.special
  if (name.includes('急诊')) return SCHEDULE_TYPE_COLORS.emergency
  return SCHEDULE_TYPE_COLORS.normal
}

export const SCHEDULE_TYPE_COLORS: Record<'normal' | 'expert' | 'special' | 'emergency', ScheduleTypeColor> = {
  normal:    { bg: 'rgba(31, 140, 255, 0.10)',  text: '#1f6fd6', border: 'rgba(31, 140, 255, 0.28)', dot: '#1f8cff' },
  expert:    { bg: 'rgba(124, 92, 255, 0.12)',  text: '#5a3ec8', border: 'rgba(124, 92, 255, 0.30)', dot: '#7c5cff' },
  special:   { bg: 'rgba(245, 159, 0, 0.14)',   text: '#a06a00', border: 'rgba(245, 159, 0, 0.32)',  dot: '#f59f00' },
  emergency: { bg: 'rgba(239, 77, 90, 0.12)',   text: '#c93644', border: 'rgba(239, 77, 90, 0.32)',  dot: '#ef4d5a' },
}

export type ScheduleStatusCode = 'normal' | 'upcoming' | 'tight' | 'stopped' | 'rest'

/** 状态元数据：颜色 + 形状（色弱友好双重编码）+ 文案 */
export interface ScheduleStatusMeta {
  code: ScheduleStatusCode
  color: string
  symbol: '●' | '◎' | '▲' | '✕' | 'rest'
  label: string
}

/**
 * 根据排班数据推断状态。
 * - 停诊：status === '停诊'
 * - 号源紧张：usedQuota / totalQuota > 0.8
 * - 即将开始：日期是今天且未停诊
 * - 正常：其他已发布排班
 */
export function resolveScheduleStatus(schedule: {
  status?: string
  usedQuota?: number
  totalQuota?: number
  workDate?: string
  isToday?: boolean
}): ScheduleStatusMeta {
  if (schedule.status === '停诊') {
    return { code: 'stopped', color: '#8ba0b6', symbol: '✕', label: '停诊' }
  }
  const used = schedule.usedQuota ?? 0
  const total = schedule.totalQuota ?? 0
  if (total > 0 && used / total > 0.8) {
    return { code: 'tight', color: '#ef4d5a', symbol: '▲', label: '号源紧张' }
  }
  if (schedule.isToday) {
    return { code: 'upcoming', color: '#c2710a', symbol: '◎', label: '即将开始' }
  }
  return { code: 'normal', color: '#20b486', symbol: '●', label: '正常' }
}

/** 时段顺序（兼容上午/下午/晚上三时段） */
export const TIME_SLOT_ORDER = ['上午', '下午', '晚上'] as const
export type TimeSlot = (typeof TIME_SLOT_ORDER)[number]

/** 时段显示名 */
export const TIME_SLOT_LABEL: Record<string, string> = {
  '上午': '上午',
  '下午': '下午',
  '晚上': '晚上',
}

/**
 * 时段色映射：用于月视图班次条目左侧的 3px 色条
 * 设计选择：和班次类型色（蓝/紫/金/红）解耦——
 *   - 周视图用班次类型色（区分「专家号/普通号/特需号」）
 *   - 月视图用时段色（区分「上午/下午/晚上」）
 * 因为月视图的阅读模式是「扫一眼当天的节奏」，时段比类型更重要
 */
export const TIME_SLOT_COLORS: Record<string, { bar: string; label: string }> = {
  '上午': { bar: '#e8890a', label: '上午' }, // 琥珀（暖色，象征晨间）
  '下午': { bar: '#1f8cff', label: '下午' }, // 主蓝（系统主色）
  '晚上': { bar: '#7c5cff', label: '晚上' }, // 紫（暖冷过渡）
}

export function resolveSlotColor(slot?: string): { bar: string; label: string } {
  if (!slot) return { bar: '#8ba0b6', label: '' }
  return TIME_SLOT_COLORS[slot] ?? { bar: '#8ba0b6', label: slot }
}

/** 中文星期 */
export const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const

/** ISO 日期 → 中文星期几（0=周一 ... 6=周日） */
export function isoToWeekdayIndex(iso: string): number {
  const d = new Date(iso + 'T00:00:00')
  // getDay: 0=周日 ... 6=周六；转换成 0=周一
  return (d.getDay() + 6) % 7
}

/** 把 Date 格式化成 yyyy-MM-dd（本地时区，避免 UTC 偏移） */
export function formatDateIso(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 取本周（以周一为起点）的 7 天 ISO 日期 */
export function weekRange(startDate: Date): string[] {
  const result: string[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(startDate)
    d.setDate(startDate.getDate() + i)
    result.push(formatDateIso(d))
  }
  return result
}

/** 取某天所在周的周一作为起点 */
export function startOfWeek(d: Date): Date {
  const date = new Date(d)
  const offset = (date.getDay() + 6) % 7 // 0=周一
  date.setDate(date.getDate() - offset)
  date.setHours(0, 0, 0, 0)
  return date
}

/** 判断 ISO 日期是否是今天 */
export function isTodayIso(iso: string): boolean {
  return iso === formatDateIso(new Date())
}

/** 月视图：取某月所有要显示的格子（含前后补白，对齐到完整的周） */
export function monthGridCells(year: number, month: number): { iso: string; inMonth: boolean }[] {
  const first = new Date(year, month, 1)
  const start = startOfWeek(first)
  const cells: { iso: string; inMonth: boolean }[] = []
  // 6 行 × 7 列 = 42 格，覆盖任何月份
  for (let i = 0; i < 42; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    const iso = formatDateIso(d)
    cells.push({ iso, inMonth: d.getMonth() === month })
  }
  return cells
}
