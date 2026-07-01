<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElButton, ElEmpty, ElMessage } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import GlucoseRevisitAdviceBlock from '@/modules/medtech/follow-up/components/GlucoseRevisitAdviceBlock.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { buildGlucoseTrendOption } from '@/shared/composables/useECharts'
import { beijingTodayYmd, beijingYmdAddDays, formatBeijingDateTime } from '@/shared/utils/beijingDate'
import type { FollowUpHealthMetric, GlucoseAdvice } from '@/shared/types/medtechFollowUp'
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

defineEmits<{
  revisit: []
}>()

const loading = ref(false)
const refreshing = ref(false)
const adviceLoading = ref(false)
const forecast = ref<GlucoseForecastResult | null>(null)
const advice = ref<GlucoseAdvice | null>(null)
const glucoseMetrics = ref<FollowUpHealthMetric[]>([])
const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

const riskLevel = computed(() => forecast.value?.riskLevel ?? 'unknown')
const riskLabel = computed(() => GLUCOSE_RISK_LABELS[riskLevel.value] ?? riskLevel.value)
const riskTone = computed(() => GLUCOSE_RISK_TONES[riskLevel.value] ?? 'neutral')

const actualSeries = computed(() => {
  const source = glucoseMetrics.value.length
    ? glucoseMetrics.value
    : props.metrics.filter((item) => item.metricKey === 'blood_glucose')
  const rows = source
    .filter((item) => item.metricKey === 'blood_glucose')
    .sort((a, b) => String(a.recordedAt ?? a.recordDate).localeCompare(String(b.recordedAt ?? b.recordDate)))
  const patientRows = rows.filter((item) => item.source !== 'uci_import')
  const recent = (patientRows.length ? patientRows : rows).slice(-72)
  return {
    points: recent.map((item) => ({
      time: String(item.recordedAt ?? item.recordDate),
      value: Number(item.metricValue),
    })),
    unit: recent[0]?.unit ?? 'mmol/L',
  }
})

const baselineSeries = computed(() => {
  const source = glucoseMetrics.value.length ? glucoseMetrics.value : props.metrics
  const rows = source
    .filter((item) => item.metricKey === 'blood_glucose' && item.source === 'uci_import')
    .sort((a, b) => String(a.recordedAt ?? a.recordDate).localeCompare(String(b.recordedAt ?? b.recordDate)))
    .slice(-72)
  return {
    points: rows.map((item) => ({
      time: String(item.recordedAt ?? item.recordDate),
      value: Number(item.metricValue),
    })),
  }
})

const forecastSeries = computed(() => ({
  points: (forecast.value?.forecasts ?? []).map((item) => ({
    time: String(item.forecastAt),
    value: Number(item.forecastValue),
  })),
}))

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
  if (!actual.points.length && !predicted.points.length) return
  chart = echarts.init(chartEl.value)
  chart.setOption(
    buildGlucoseTrendOption({
      title: props.compact ? '血糖趋势与预测' : '血糖监测与 24h 预测',
      actualPoints: actual.points,
      baselinePoints: baselineSeries.value.points,
      forecastPoints: predicted.points,
      formatTime: (value) => formatBeijingDateTime(value),
      unit: actual.unit,
    }),
  )
}

async function loadGlucoseMetrics() {
  if (!props.registerId) return
  const to = beijingTodayYmd()
  const from = beijingYmdAddDays(-7, to)
  try {
    if (props.mode === 'patient') {
      glucoseMetrics.value = await medtechFollowUpApi.listPatientObservations({
        patientId: props.patientId,
        registerId: props.registerId,
        from,
      })
    } else {
      glucoseMetrics.value = await medtechFollowUpApi.getMetrics(props.registerId, {
        from,
        to,
        metricKeys: ['blood_glucose'],
        sourceType: 'patient_report',
      })
    }
  } catch {
    glucoseMetrics.value = []
  }
}

async function loadAdvice() {
  if (!props.registerId) return
  adviceLoading.value = true
  const adviceUrl =
    props.mode === 'patient'
      ? `/api/medtech/follow-up/patient/glucose-advice?registerId=${props.registerId}`
      : `/api/medtech/follow-up/outcome/glucose-advice/${props.registerId}`
  // #region agent log
  fetch('http://127.0.0.1:7723/ingest/3c270b7b-7b14-401b-89fb-a81f2dfb5895', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '02d871' },
    body: JSON.stringify({
      sessionId: '02d871',
      hypothesisId: 'C',
      location: 'GlucoseForecastPanel.vue:loadAdvice:start',
      message: 'loading glucose advice',
      data: { mode: props.mode, registerId: props.registerId, adviceUrl },
      timestamp: Date.now(),
    }),
  }).catch(() => {})
  // #endregion
  try {
    if (props.mode === 'patient') {
      advice.value = await medtechFollowUpApi.getPatientGlucoseAdvice({
        patientId: props.patientId,
        registerId: props.registerId,
      })
    } else {
      advice.value = await medtechFollowUpApi.getGlucoseAdvice(props.registerId)
    }
    // #region agent log
    fetch('http://127.0.0.1:7723/ingest/3c270b7b-7b14-401b-89fb-a81f2dfb5895', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '02d871' },
      body: JSON.stringify({
        sessionId: '02d871',
        hypothesisId: 'A',
        location: 'GlucoseForecastPanel.vue:loadAdvice:success',
        message: 'glucose advice loaded',
        data: {
          mode: props.mode,
          registerId: props.registerId,
          revisitRecommended: advice.value?.revisitRecommended,
          riskLevel: advice.value?.riskLevel,
        },
        timestamp: Date.now(),
      }),
    }).catch(() => {})
    // #endregion
  } catch (error: unknown) {
    advice.value = null
    const err = error as { response?: { status?: number; data?: unknown }; message?: string }
    // #region agent log
    fetch('http://127.0.0.1:7723/ingest/3c270b7b-7b14-401b-89fb-a81f2dfb5895', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '02d871' },
      body: JSON.stringify({
        sessionId: '02d871',
        hypothesisId: 'B',
        location: 'GlucoseForecastPanel.vue:loadAdvice:error',
        message: 'glucose advice request failed',
        data: {
          mode: props.mode,
          registerId: props.registerId,
          adviceUrl,
          status: err.response?.status,
          responseData: err.response?.data,
          errorMessage: err.message,
        },
        timestamp: Date.now(),
      }),
    }).catch(() => {})
    // #endregion
  } finally {
    adviceLoading.value = false
  }
}

async function loadForecast() {
  if (!props.registerId && !props.patientId) return
  loading.value = true
  try {
    if (props.registerId) {
      await loadGlucoseMetrics()
    }
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
    void loadAdvice()
  }
}

async function refreshForecast() {
  if (!props.registerId) return
  refreshing.value = true
  try {
    forecast.value = await medtechFollowUpApi.refreshGlucoseForecast(props.registerId)
    await loadGlucoseMetrics()
    ElMessage.success('血糖预测已更新')
    await renderChart()
    await loadAdvice()
  } catch {
    ElMessage.error('刷新预测失败，请确认推理服务已启动')
  } finally {
    refreshing.value = false
  }
}

watch(
  () => [props.registerId, props.patientId],
  () => {
    void loadForecast()
  },
  { immediate: true },
)

watch(
  () => props.metrics,
  () => {
    if (!glucoseMetrics.value.length) {
      void renderChart()
    }
  },
  { deep: true },
)

onUnmounted(disposeChart)
</script>

<template>
  <div class="glucose-forecast" v-loading="loading">
    <div class="glucose-forecast__head">
      <div>
        <h3>{{ compact ? '血糖预测' : 'AI 血糖预测' }}</h3>
        <p v-if="!compact">展示最近 72 小时实测与 LSTM+GRU 未来 24 小时预测；下方根据模型输出判断是否需要复诊</p>
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

    <GlucoseRevisitAdviceBlock
      v-if="registerId"
      :advice="advice"
      :loading="adviceLoading"
      :compact="compact"
      :show-apply-button="mode === 'patient'"
      @revisit="$emit('revisit')"
    />

    <div v-if="actualSeries.points.length || forecastSeries.points.length" ref="chartEl" class="glucose-forecast__chart" />
    <ElEmpty v-else description="暂无血糖观测数据，请先导入或刷新预测" />

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
