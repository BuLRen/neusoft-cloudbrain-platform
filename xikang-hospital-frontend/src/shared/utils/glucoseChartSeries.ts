export interface GlucoseChartPoint {
  time: string
  value: number
  interpolated?: boolean
}

const DEMO_BASELINE_MAX = 5
const MAX_GAP_FILL_STEPS = 4
const MAX_GAP_HOURS = 12
const MIN_FORECAST_POINTS = 8
const FORECAST_HOURS = 24

export function parseGlucoseChartTime(value: string): number {
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? 0 : ms
}

function sortByTime(points: GlucoseChartPoint[]): GlucoseChartPoint[] {
  return [...points].sort((a, b) => parseGlucoseChartTime(a.time) - parseGlucoseChartTime(b.time))
}

/** 在相邻实测点之间线性插值，便于折线连续展示 */
export function fillGlucoseGaps(points: GlucoseChartPoint[]): GlucoseChartPoint[] {
  if (points.length < 2) return points

  const result: GlucoseChartPoint[] = []
  for (let i = 0; i < points.length - 1; i += 1) {
    const current = points[i]!
    const next = points[i + 1]!
    result.push(current)

    const t0 = parseGlucoseChartTime(current.time)
    const t1 = parseGlucoseChartTime(next.time)
    const gapHours = (t1 - t0) / 3_600_000
    if (gapHours <= 1 || gapHours > MAX_GAP_HOURS) continue

    const steps = Math.min(MAX_GAP_FILL_STEPS, Math.floor(gapHours))
    for (let step = 1; step < steps; step += 1) {
      const ratio = step / steps
      const interpolatedMs = t0 + (t1 - t0) * ratio
      const interpolatedValue = current.value + (next.value - current.value) * ratio
      result.push({
        time: new Date(interpolatedMs).toISOString().slice(0, 19),
        value: Math.round(interpolatedValue * 10) / 10,
        interpolated: true,
      })
    }
  }
  result.push(points[points.length - 1]!)
  return result
}

/** 预测点不足时，按小时补齐未来 24h 曲线（与后端演示预测一致） */
export function expandForecastPoints(
  forecastPoints: GlucoseChartPoint[],
  bridgePoint: GlucoseChartPoint | null,
): GlucoseChartPoint[] {
  if (forecastPoints.length >= MIN_FORECAST_POINTS || !bridgePoint) {
    return forecastPoints
  }

  const anchorMs = parseGlucoseChartTime(bridgePoint.time)
  const byHour = new Map<number, GlucoseChartPoint>()
  for (const point of forecastPoints) {
    byHour.set(Math.round(parseGlucoseChartTime(point.time) / 3_600_000), point)
  }

  const expanded: GlucoseChartPoint[] = []
  for (let hour = 1; hour <= FORECAST_HOURS; hour += 1) {
    const slotMs = anchorMs + hour * 3_600_000
    const hourKey = Math.round(slotMs / 3_600_000)
    const existing = byHour.get(hourKey)
    if (existing) {
      expanded.push(existing)
      continue
    }
    const drift = 0
    expanded.push({
      time: new Date(slotMs).toISOString().slice(0, 19),
      value: Math.round((bridgePoint.value + drift) * 10) / 10,
    })
  }
  return expanded
}

/**
 * 合并演示基线、实测与预测：
 * - 开头最多 5 个演示点（且早于首条实测）
 * - 预测仅保留末次实测之后
 * - 实测段自动插值补齐
 */
export function mergeGlucoseChartSeries(params: {
  baselinePoints: GlucoseChartPoint[]
  actualPoints: GlucoseChartPoint[]
  forecastPoints: GlucoseChartPoint[]
}) {
  const sortedActual = sortByTime(params.actualPoints)
  const firstActualMs = sortedActual[0] ? parseGlucoseChartTime(sortedActual[0].time) : null

  let baseline = sortByTime(params.baselinePoints)
  if (firstActualMs != null) {
    baseline = baseline.filter((point) => parseGlucoseChartTime(point.time) < firstActualMs)
  }
  baseline = baseline.slice(-DEMO_BASELINE_MAX)

  const filledActual = fillGlucoseGaps(sortedActual)
  const lastActual = filledActual.at(-1) ?? sortedActual.at(-1)
  const lastActualMs = lastActual ? parseGlucoseChartTime(lastActual.time) : null

  let forecast = sortByTime(params.forecastPoints)
  if (lastActualMs != null) {
    forecast = forecast.filter((point) => parseGlucoseChartTime(point.time) > lastActualMs)
  }
  forecast = expandForecastPoints(forecast, lastActual ?? null)

  return {
    baseline,
    actual: filledActual,
    forecast,
    bridgePoint: lastActual ?? null,
  }
}
