import * as echarts from 'echarts'
import { onMounted, onUnmounted, ref, shallowRef } from 'vue'
import {
  SYMPTOM_RELIEF_COLORS,
  SYMPTOM_RELIEF_LABELS,
  SYMPTOM_RELIEF_SCORE,
} from '@/shared/constants/outcomeCharts'

export function useECharts() {
  const chartRef = ref<HTMLElement | null>(null)
  const chart = shallowRef<echarts.ECharts | null>(null)

  function init() {
    if (!chartRef.value) return
    if (!chart.value) {
      chart.value = echarts.init(chartRef.value)
    }
  }

  function setOption(option: echarts.EChartsOption, replace = true) {
    init()
    chart.value?.setOption(option, replace)
  }

  function resize() {
    chart.value?.resize()
  }

  function dispose() {
    chart.value?.dispose()
    chart.value = null
  }

  onMounted(() => {
    init()
    window.addEventListener('resize', resize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    dispose()
  })

  return { chartRef, chart, setOption, resize, dispose, init }
}

export function buildTrendOption(params: {
  title: string
  dates: string[]
  values: number[]
  unit?: string
  chartType?: 'line' | 'bar'
  color?: string
}): echarts.EChartsOption {
  const color = params.color ?? '#1f8cff'
  const seriesType = params.chartType ?? 'line'
  return {
    title: {
      text: params.title,
      left: 0,
      textStyle: { fontSize: 14, fontWeight: 600, color: '#102033' },
    },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => `${value}${params.unit ? ` ${params.unit}` : ''}`,
    },
    grid: { left: 40, right: 16, top: 48, bottom: 28 },
    xAxis: {
      type: 'category',
      data: params.dates,
      axisLabel: { color: '#5f7288' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#5f7288' },
      splitLine: { lineStyle: { color: 'rgba(70, 111, 160, 0.12)' } },
    },
    series: [
      {
        name: params.title,
        type: seriesType,
        smooth: seriesType === 'line',
        data: params.values,
        itemStyle: { color },
        areaStyle: seriesType === 'line' ? { color: 'rgba(31, 140, 255, 0.12)' } : undefined,
        barMaxWidth: 28,
      },
    ],
  }
}

export interface GlucoseChartPoint {
  time: string
  value: number
}

function parseChartTime(value: string): number {
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? 0 : ms
}

export function buildGlucoseTrendOption(params: {
  title: string
  actualPoints: GlucoseChartPoint[]
  forecastPoints: GlucoseChartPoint[]
  formatTime?: (value: string) => string
  unit?: string
  color?: string
}): echarts.EChartsOption {
  const color = params.color ?? '#1f8cff'
  const formatTime = params.formatTime ?? ((v: string) => v)
  const unit = params.unit ?? 'mmol/L'

  type Slot = { time: string; actual: number | null; forecast: number | null }
  const slots = new Map<number, Slot>()

  for (const point of params.actualPoints) {
    const ms = parseChartTime(point.time)
    slots.set(ms, { time: point.time, actual: point.value, forecast: null })
  }

  for (const point of params.forecastPoints) {
    const ms = parseChartTime(point.time)
    const existing = slots.get(ms)
    if (existing) {
      existing.forecast = point.value
    } else {
      slots.set(ms, { time: point.time, actual: null, forecast: point.value })
    }
  }

  const sorted = [...slots.entries()].sort((a, b) => a[0] - b[0])
  const lastActual = params.actualPoints.at(-1)
  const lastActualMs = lastActual ? parseChartTime(lastActual.time) : null

  const axisLabels = sorted.map(([, slot]) => formatTime(slot.time))
  const actualData = sorted.map(([, slot]) => slot.actual)
  const forecastData = sorted.map(([ms, slot]) => {
    if (lastActualMs != null && ms === lastActualMs && lastActual != null) {
      return lastActual.value
    }
    return slot.forecast
  })

  return {
    title: {
      text: params.title,
      left: 0,
      textStyle: { fontSize: 14, fontWeight: 600, color: '#102033' },
    },
    legend: {
      top: 4,
      right: 0,
      data: ['实测', '预测'],
      textStyle: { color: '#5f7288' },
    },
    tooltip: {
      trigger: 'axis',
      formatter(items) {
        const rows = Array.isArray(items) ? items : [items]
        if (!rows.length) return ''
        const header = String(rows[0]?.name ?? '')
        const lines = [header]
        for (const row of rows) {
          const val = row?.value
          if (val == null || val === '') continue
          lines.push(`${row?.seriesName ?? ''}: ${val} ${unit}`)
        }
        return lines.join('<br/>')
      },
    },
    grid: { left: 48, right: 16, top: 56, bottom: 36 },
    xAxis: {
      type: 'category',
      data: axisLabels,
      axisLabel: { color: '#5f7288', rotate: axisLabels.length > 12 ? 28 : 0 },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#5f7288' },
      splitLine: { lineStyle: { color: 'rgba(70, 111, 160, 0.12)' } },
    },
    series: [
      {
        name: '实测',
        type: 'line',
        smooth: true,
        connectNulls: false,
        data: actualData,
        itemStyle: { color },
        lineStyle: { width: 2 },
        areaStyle: { color: 'rgba(31, 140, 255, 0.12)' },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#ef4d5a', opacity: 0.45 },
          data: [{ yAxis: 3.9 }, { yAxis: 10.0 }],
        },
      },
      {
        name: '预测',
        type: 'line',
        smooth: true,
        connectNulls: true,
        data: forecastData,
        itemStyle: { color: '#f59f00' },
        lineStyle: { type: 'dashed', width: 2 },
      },
    ],
  }
}

export function buildReliefTrendOption(
  records: { followUpTime?: string; symptomRelief?: string; patientFeedback?: string }[],
  formatTime: (value?: string | null) => string,
): echarts.EChartsOption | null {
  const sorted = [...records]
    .filter((item) => item.followUpTime || item.symptomRelief)
    .sort((a, b) => String(a.followUpTime ?? '').localeCompare(String(b.followUpTime ?? '')))

  if (!sorted.length) return null

  const categories = sorted.map((item) => formatTime(item.followUpTime))
  const data = sorted.map((item) => {
    const key = item.symptomRelief ?? 'unknown'
    const score = SYMPTOM_RELIEF_SCORE[key] ?? 2
    return {
      value: score,
      itemStyle: { color: SYMPTOM_RELIEF_COLORS[key] ?? SYMPTOM_RELIEF_COLORS.unknown },
      reliefKey: key,
      feedback: item.patientFeedback ?? '',
    }
  })

  return {
    title: {
      text: '康复随访进展',
      left: 0,
      textStyle: { fontSize: 14, fontWeight: 600, color: '#102033' },
    },
    tooltip: {
      trigger: 'axis',
      formatter(params) {
        const row = Array.isArray(params) ? params[0] : params
        const payload = row?.data as { reliefKey?: string; feedback?: string; value?: number }
        const label = SYMPTOM_RELIEF_LABELS[payload?.reliefKey ?? ''] ?? payload?.reliefKey ?? '—'
        const lines = [`${row?.name}`, `缓解程度：${label}`, `评分：${payload?.value ?? '—'}/4`]
        if (payload?.feedback) lines.push(`反馈：${payload.feedback}`)
        return lines.join('<br/>')
      },
    },
    grid: { left: 44, right: 16, top: 48, bottom: 36 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: '#5f7288', rotate: categories.length > 4 ? 24 : 0 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 4,
      interval: 1,
      axisLabel: {
        color: '#5f7288',
        formatter: (value: number) => {
          const map: Record<number, string> = { 1: '加重', 2: '无变化', 3: '部分', 4: '明显' }
          return map[value] ?? ''
        },
      },
      splitLine: { lineStyle: { color: 'rgba(70, 111, 160, 0.12)' } },
    },
    series: [
      {
        name: '缓解评分',
        type: 'bar',
        data,
        barMaxWidth: 36,
        label: {
          show: true,
          position: 'top',
          color: '#5f7288',
          formatter: (params: { data: { reliefKey?: string } }) =>
            SYMPTOM_RELIEF_LABELS[params.data?.reliefKey ?? ''] ?? '',
        },
      },
    ],
  }
}
