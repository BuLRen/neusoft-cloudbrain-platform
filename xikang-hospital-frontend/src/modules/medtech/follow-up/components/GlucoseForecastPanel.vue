<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElButton, ElEmpty, ElMessage } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { buildGlucoseTrendOption } from '@/shared/composables/useECharts'
import type { FollowUpHealthMetric } from '@/shared/types/medtechFollowUp'
import {
  GLUCOSE_RISK_LABELS,
  GLUCOSE_RISK_TONES,
  type GlucoseForecastResult,
} from '@/shared/types/glucoseForecast'

const props = withDefaults(
  defineProps<{
    registerId?: number
    metrics?: FollowUpHealthMetric[]
    patientId?: number
    mode?: 'doctor' | 'patient'
    compact?: boolean
  }>(),
  {
    metrics: () => [],
    mode: 'doctor',
    compact: false,
  },
)

const loading = ref(false)
const refreshing = ref(false)
const forecast = ref<GlucoseForecastResult | null>(null)
const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

const riskLevel = computed(() => forecast.value?.riskLevel ?? 'unknown')
const riskLabel = computed(() => GLUCOSE_RISK_LABELS[riskLevel.value] ?? riskLevel.value)
const riskTone = computed(() => GLUCOSE_RISK_TONES[riskLevel.value] ?? 'neutral')

const actualSeries = computed(() => {
  const rows = props.metrics
    .filter((item) => item.metricKey === 'blood_glucose')
    .sort((a, b) => a.recordDate.localeCompare(b.recordDate))
  return {
    dates: rows.map((item) => item.recordDate),
    values: rows.map((item) => Number(item.metricValue)),
    unit: rows[0]?.unit ?? 'mmol/L',
  }
})

const forecastSeries = computed(() => {
  const points = forecast.value?.forecasts ?? []
  return {
    dates: points.map((item) => String(item.forecastAt).slice(0, 16).replace('T', ' ')),
    values: points.map((item) => Number(item.forecastValue)),
  }
})

function disposeChart() {
  chart?.dispose()
  chart = null
}

async function renderChart() {
  await nextTick()
  if (!chartEl.value) return
  disposeChart()
  const actual = actualSeries.value
  const predicted = forecastSeries.value
  if (!actual.dates.length && !predicted.dates.length) return
  chart = echarts.init(chartEl.value)
  chart.setOption(
    buildGlucoseTrendOption({
      title: props.compact ? '血糖趋势与预测' : '血糖监测与 24h 预测',
      actualDates: actual.dates,
      actualValues: actual.values,
      forecastDates: predicted.dates,
      forecastValues: predicted.values,
      unit: actual.unit,
    }),
  )
}

async function loadForecast() {
  if (!props.registerId && !props.patientId) return
  loading.value = true
  try {
    if (props.mode === 'patient') {
      forecast.value = await medtechFollowUpApi.getPatientGlucoseForecast({
        patientId: props.patientId,
        registerId: props.registerId,
      })
    } else if (props.registerId) {
      forecast.value = await medtechFollowUpApi.getGlucoseForecast(props.registerId)
    }
  } catch {
    forecast.value = null
  } finally {
    loading.value = false
    await renderChart()
  }
}

async function refreshForecast() {
  if (!props.registerId) return
  refreshing.value = true
  try {
    forecast.value = await medtechFollowUpApi.refreshGlucoseForecast(props.registerId)
    ElMessage.success('血糖预测已更新')
    await renderChart()
  } catch {
    ElMessage.error('刷新预测失败，请确认推理服务已启动')
  } finally {
    refreshing.value = false
  }
}

watch(
  () => [props.registerId, props.patientId, props.metrics],
  () => {
    void loadForecast()
  },
  { deep: true, immediate: true },
)

onUnmounted(disposeChart)
</script>

<template>
  <div class="glucose-forecast" v-loading="loading">
    <div class="glucose-forecast__head">
      <div>
        <h3>{{ compact ? '血糖预测' : 'AI 血糖预测' }}</h3>
        <p v-if="!compact">基于 LSTM 模型对未来 24 小时血糖进行趋势预测</p>
      </div>
      <div class="glucose-forecast__actions">
        <StatusTag :tone="riskTone">{{ riskLabel }}</StatusTag>
        <ElButton
          v-if="mode === 'doctor' && registerId"
          type="primary"
          plain
          size="small"
          :loading="refreshing"
          @click="refreshForecast"
        >
          刷新预测
        </ElButton>
      </div>
    </div>

    <div v-if="forecast?.message && !(forecast.forecasts?.length)" class="glucose-forecast__hint">
      {{ forecast.message }}
    </div>

    <div v-if="actualSeries.dates.length || forecastSeries.dates.length" ref="chartEl" class="glucose-forecast__chart" />
    <ElEmpty v-else description="暂无血糖观测数据" />

    <div v-if="forecast?.modelId" class="glucose-forecast__meta">
      模型 {{ forecast.modelId }}
      <span v-if="forecast.confidence != null"> · 置信度 {{ (forecast.confidence * 100).toFixed(0) }}%</span>
      <span v-if="forecast.observationCount != null"> · 观测 {{ forecast.observationCount }} 条</span>
    </div>
  </div>
</template>

<style scoped>
.glucose-forecast {
  display: grid;
  gap: var(--space-4);
}

.glucose-forecast__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.glucose-forecast__head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.glucose-forecast__head p {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.glucose-forecast__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.glucose-forecast__chart {
  width: 100%;
  min-height: 280px;
}

.glucose-forecast__hint {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(245, 159, 0, 0.08);
  color: #9a6700;
  font-size: 13px;
}

.glucose-forecast__meta {
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
