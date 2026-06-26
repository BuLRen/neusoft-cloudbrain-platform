<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  ElButton,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import MedtechStepLayout from '@/modules/medtech/layouts/MedtechStepLayout.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import {
  collectAllMetricDefs,
  metricLabel,
  resolveOutcomeChartProfile,
  SYMPTOM_RELIEF_COLORS,
  SYMPTOM_RELIEF_LABELS,
} from '@/shared/constants/outcomeCharts'
import { buildReliefTrendOption, buildTrendOption } from '@/shared/composables/useECharts'
import {
  formatBeijingDateTime,
  OUTCOME_RANGE_OPTIONS,
  resolveOutcomeRange,
  type OutcomeRangePreset,
} from '@/shared/utils/beijingDate'
import { addToWeeklySchedule, getWeeklyScheduleStatus } from '@/modules/medtech/follow-up/services/interviewSchedule'
import type {
  FollowUpHealthMetric,
  FollowUpOutcomeRecord,
  FollowUpPatientDetail,
  FollowUpPatientOption,
  FollowUpPatientProfile,
} from '@/shared/types/medtechFollowUp'

const patients = ref<FollowUpPatientOption[]>([])
const selectedRegisterId = ref<number | undefined>()
const profile = ref<FollowUpPatientProfile | null>(null)
const metrics = ref<FollowUpHealthMetric[]>([])
const records = ref<FollowUpOutcomeRecord[]>([])
const scheduleScheduled = ref(false)
const loading = ref(false)
const scheduling = ref(false)

const rangePreset = ref<OutcomeRangePreset>('30d')
const dateRangeLabel = computed(() => OUTCOME_RANGE_OPTIONS.find((o) => o.value === rangePreset.value)?.label ?? '')

const chartProfile = computed(() => resolveOutcomeChartProfile(profile.value?.primaryDiseaseCategory))
const primaryChartDefs = computed(() => chartProfile.value.primary)
const secondaryChartDefs = computed(() => chartProfile.value.secondary)

const metricDialogVisible = ref(false)
const selectedMetricKey = ref<string | null>(null)
const metricDialogChartEl = ref<HTMLElement | null>(null)
let metricDialogChart: echarts.ECharts | null = null

const patientDetailVisible = ref(false)
const patientDetail = ref<FollowUpPatientDetail | null>(null)
const patientDetailLoading = ref(false)

const primaryChartEls = ref<HTMLElement[]>([])
const secondaryChartEl = ref<HTMLElement | null>(null)
const reliefChartEl = ref<HTMLElement | null>(null)
const chartInstances: echarts.ECharts[] = []

const latestMetrics = computed(() => {
  const map = new Map<string, FollowUpHealthMetric>()
  for (const item of metrics.value) {
    const prev = map.get(item.metricKey)
    if (!prev || item.recordDate > prev.recordDate) {
      map.set(item.metricKey, item)
    }
  }
  return map
})

const historyRows = computed(() => {
  const grouped = new Map<string, Record<string, string | number>>()
  for (const item of metrics.value) {
    const row = grouped.get(item.recordDate) ?? {
      recordDate: item.recordDate,
      recordedAt: item.recordedAt ?? '',
    }
    if (!row.recordedAt && item.recordedAt) {
      row.recordedAt = item.recordedAt
    }
    row[item.metricKey] = item.metricValue
    grouped.set(item.recordDate, row)
  }
  return Array.from(grouped.values()).sort((a, b) => String(b.recordDate).localeCompare(String(a.recordDate)))
})

const historyMetricKeys = computed(() => {
  const keys = new Set<string>()
  metrics.value.forEach((item) => keys.add(item.metricKey))
  return Array.from(keys)
})

const allMetricDefs = computed(() =>
  collectAllMetricDefs(
    chartProfile.value,
    historyMetricKeys.value,
    (key) => latestMetrics.value.get(key)?.unit,
  ),
)

const selectedMetricDef = computed(() =>
  allMetricDefs.value.find((def) => def.key === selectedMetricKey.value),
)

const selectedMetricHistory = computed(() => {
  if (!selectedMetricKey.value) return []
  return metrics.value
    .filter((item) => item.metricKey === selectedMetricKey.value)
    .sort((a, b) => {
      const dateCmp = b.recordDate.localeCompare(a.recordDate)
      if (dateCmp !== 0) return dateCmp
      return String(b.recordedAt ?? '').localeCompare(String(a.recordedAt ?? ''))
    })
})

function setPrimaryChartEl(el: unknown, index: number) {
  if (el instanceof HTMLElement) {
    primaryChartEls.value[index] = el
  }
}


function currentQueryRange() {
  return resolveOutcomeRange(rangePreset.value)
}

function groupMetricSeries(key: string) {
  const rows = metrics.value
    .filter((item) => item.metricKey === key)
    .sort((a, b) => a.recordDate.localeCompare(b.recordDate))
  return {
    dates: rows.map((item) => item.recordDate),
    values: rows.map((item) => Number(item.metricValue)),
    unit: rows[0]?.unit,
  }
}

function disposeMetricDialogChart() {
  metricDialogChart?.dispose()
  metricDialogChart = null
}

function renderMetricDialogChart() {
  if (!metricDialogChartEl.value || !selectedMetricKey.value) return
  const def = selectedMetricDef.value
  const series = groupMetricSeries(selectedMetricKey.value)
  disposeMetricDialogChart()
  if (!series.dates.length) return
  metricDialogChart = echarts.init(metricDialogChartEl.value)
  metricDialogChart.setOption(
    buildTrendOption({
      title: def?.label ?? metricLabel(selectedMetricKey.value),
      dates: series.dates,
      values: series.values,
      unit: def?.unit ?? series.unit,
      chartType: def?.chart ?? 'line',
      color: '#1f8cff',
    }),
  )
}

function openMetricDialog(key: string) {
  selectedMetricKey.value = key
  metricDialogVisible.value = true
}

function closeMetricDialog() {
  selectedMetricKey.value = null
  disposeMetricDialogChart()
}

function displayValue(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '—'
  return String(value)
}

function maskPhone(phone?: string) {
  if (!phone || phone.length < 11) return displayValue(phone)
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function maskIdCard(idCard?: string) {
  if (!idCard || idCard.length < 10) return displayValue(idCard)
  return `${idCard.slice(0, 3)}********${idCard.slice(-4)}`
}

function visitStateLabel(state?: number) {
  if (state === 1) return '已挂号'
  if (state === 2) return '医生接诊'
  if (state === 3) return '看诊结束'
  if (state === 4) return '已退号'
  return '—'
}

async function openPatientDetail() {
  if (!selectedRegisterId.value) return
  patientDetailVisible.value = true
  patientDetailLoading.value = true
  patientDetail.value = null
  try {
    patientDetail.value = await medtechFollowUpApi.getPatientDetail(selectedRegisterId.value)
  } catch {
    patientDetailVisible.value = false
    ElMessage.error('加载患者信息失败')
  } finally {
    patientDetailLoading.value = false
  }
}

function closePatientDetail() {
  patientDetail.value = null
}

function disposeCharts() {
  chartInstances.forEach((chart) => chart.dispose())
  chartInstances.length = 0
}

function mountChart(el: HTMLElement | null | undefined, option: echarts.EChartsOption) {
  if (!el) return
  const chart = echarts.init(el)
  chart.setOption(option)
  chartInstances.push(chart)
}

async function renderCharts() {
  disposeCharts()
  primaryChartEls.value = []
  await nextTick()

  primaryChartDefs.value.forEach((def, index) => {
    const series = groupMetricSeries(def.key)
    if (!series.dates.length) return
    mountChart(
      primaryChartEls.value[index],
      buildTrendOption({
        title: def.label,
        dates: series.dates,
        values: series.values,
        unit: def.unit ?? series.unit,
        chartType: def.chart,
        color: index === 0 ? '#1f8cff' : '#7c5cff',
      }),
    )
  })

  const reliefOption = buildReliefTrendOption(records.value, formatBeijingDateTime)
  if (reliefOption) {
    mountChart(reliefChartEl.value, reliefOption)
  }

  const secondaryKeys = secondaryChartDefs.value.map((item) => item.key)
  const secondaryDates = Array.from(new Set(
    metrics.value.filter((item) => secondaryKeys.includes(item.metricKey)).map((item) => item.recordDate),
  )).sort()

  if (secondaryDates.length && secondaryChartEl.value) {
    const chart = echarts.init(secondaryChartEl.value)
    chart.setOption({
      title: {
        text: '常规健康指标趋势',
        left: 0,
        textStyle: { fontSize: 14, fontWeight: 600, color: '#102033' },
      },
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, textStyle: { color: '#5f7288' } },
      grid: { left: 40, right: 16, top: 48, bottom: 48 },
      xAxis: { type: 'category', data: secondaryDates, axisLabel: { color: '#5f7288' } },
      yAxis: { type: 'value', axisLabel: { color: '#5f7288' }, splitLine: { lineStyle: { color: 'rgba(70, 111, 160, 0.12)' } } },
      series: secondaryChartDefs.value.map((def, index) => {
        const palette = ['#1f8cff', '#20b486', '#f59f00', '#7c5cff', '#ef4d5a']
        const values = secondaryDates.map((date) => {
          const found = metrics.value.find((item) => item.recordDate === date && item.metricKey === def.key)
          return found ? Number(found.metricValue) : null
        })
        return {
          name: def.label,
          type: 'line',
          smooth: true,
          connectNulls: true,
          data: values,
          itemStyle: { color: palette[index % palette.length] },
        }
      }),
    })
    chartInstances.push(chart)
  }
}

async function loadPatients() {
  try {
    patients.value = await medtechFollowUpApi.listPatients(3)
    if (!patients.value.length) {
      ElMessage.warning('未找到随访患者，请确认后端已重启且演示数据已导入')
      return
    }
    if (!selectedRegisterId.value) {
      selectedRegisterId.value = patients.value[0]?.registerId
    }
  } catch {
    ElMessage.error('加载随访患者列表失败')
  }
}

async function loadPatientData() {
  if (!selectedRegisterId.value) return
  loading.value = true
  try {
    const { from, to } = currentQueryRange()
    const [profileRes, metricsRes, recordsRes, scheduleRes] = await Promise.all([
      medtechFollowUpApi.getProfile(selectedRegisterId.value),
      medtechFollowUpApi.getMetrics(selectedRegisterId.value, { from, to }),
      medtechFollowUpApi.getRecords(selectedRegisterId.value),
      getWeeklyScheduleStatus(selectedRegisterId.value),
    ])
    profile.value = profileRes
    metrics.value = metricsRes
    records.value = recordsRes
    scheduleScheduled.value = Boolean(scheduleRes.scheduled)
    await renderCharts()
  } catch {
    ElMessage.error('加载患者疗效数据失败')
  } finally {
    loading.value = false
  }
}

async function handleScheduleInterview() {
  if (!selectedRegisterId.value) return
  scheduling.value = true
  try {
    await addToWeeklySchedule({
      registerId: selectedRegisterId.value,
      triggerReason: '疗效评估发现指标变化，建议安排每周访谈跟进',
      triggerMetricKey: primaryChartDefs.value[0]?.key,
    })
    ElMessage.success('已加入本周访谈日程')
    scheduleScheduled.value = true
  } catch {
    // 统一错误提示
  } finally {
    scheduling.value = false
  }
}

function patientLabel(item: FollowUpPatientOption) {
  return `${item.realName ?? '未知'}（${item.caseNumber ?? item.registerId}）`
}

function reliefLabel(value?: string) {
  if (!value) return '—'
  return SYMPTOM_RELIEF_LABELS[value] ?? value
}

function reliefTone(value?: string): 'success' | 'primary' | 'warning' | 'danger' | 'neutral' {
  if (value === 'relieved') return 'success'
  if (value === 'partial') return 'primary'
  if (value === 'unchanged') return 'warning'
  if (value === 'worsened') return 'danger'
  return 'neutral'
}

watch(selectedRegisterId, () => {
  void loadPatientData()
})

watch(rangePreset, () => {
  void loadPatientData()
})

onUnmounted(() => {
  disposeCharts()
  disposeMetricDialogChart()
})

void loadPatients().then(() => loadPatientData())
</script>

<template>
  <MedtechStepLayout
    :show-steps="false"
    :step="1"
    :total-steps="1"
    module-label="随访系统"
    title="疗效评估"
    description="基于模拟术后健康指标与随访反馈，按患者所患疾病匹配主视角图表，支持趋势筛查与加入每周访谈日程。"
  >
    <GlassCard class="outcome-card" v-loading="loading">
      <div class="toolbar">
        <div class="toolbar__left">
          <span class="toolbar__label">选择患者</span>
          <ElSelect
            v-model="selectedRegisterId"
            class="toolbar__select"
            filterable
            placeholder="请选择随访患者"
          >
            <ElOption
              v-for="item in patients"
              :key="item.registerId"
              :label="patientLabel(item)"
              :value="item.registerId"
            />
          </ElSelect>
          <ElSelect v-model="rangePreset" class="toolbar__range-preset" placeholder="时间范围">
            <ElOption
              v-for="item in OUTCOME_RANGE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <span class="toolbar__range-hint">{{ dateRangeLabel }}（截至北京时间今日）</span>
          <ElButton @click="loadPatientData">刷新</ElButton>
        </div>
        <div class="toolbar__right">
          <ElButton :disabled="!selectedRegisterId" @click="openPatientDetail">查看患者信息</ElButton>
          <ElButton
            type="primary"
            :disabled="!selectedRegisterId || scheduleScheduled"
            :loading="scheduling"
            @click="handleScheduleInterview"
          >
            {{ scheduleScheduled ? '本周已排访谈' : '加入每周访谈' }}
          </ElButton>
        </div>
      </div>

      <div v-if="profile" class="profile-bar">
        <div>
          <strong>{{ profile.realName }}</strong>
          <span class="profile-bar__meta">病历号 {{ profile.caseNumber }} · {{ profile.gender }} · {{ profile.age }}岁</span>
        </div>
        <div class="profile-bar__tags">
          <ElTag v-for="disease in profile.diseases" :key="disease.diseaseId" type="info" effect="plain">
            {{ disease.diseaseName }}
          </ElTag>
          <StatusTag tone="primary">{{ profile.primaryDiseaseCategory ?? '默认评估' }}</StatusTag>
        </div>
      </div>

      <div class="kpi-section">
        <div class="kpi-section__head">
          <h3 class="kpi-section__title">健康指标概览</h3>
          <span class="kpi-section__hint">点击卡片查看该指标历史趋势与明细</span>
        </div>
        <ElRow :gutter="16" class="kpi-row">
          <ElCol
            v-for="def in allMetricDefs"
            :key="def.key"
            :xs="12"
            :sm="8"
            :md="6"
            :lg="4"
          >
            <button
              type="button"
              class="kpi-card kpi-card--clickable"
              @click="openMetricDialog(def.key)"
            >
              <span class="kpi-card__label">{{ def.label }}</span>
              <strong class="kpi-card__value">
                {{ latestMetrics.get(def.key)?.metricValue ?? '—' }}
                <small>{{ latestMetrics.get(def.key)?.unit ?? def.unit }}</small>
              </strong>
              <span class="kpi-card__date">{{ latestMetrics.get(def.key)?.recordDate ?? '暂无数据' }}</span>
              <span class="kpi-card__action">查看历史</span>
            </button>
          </ElCol>
          <ElCol :xs="12" :sm="8" :md="6" :lg="4">
            <div class="kpi-card kpi-card--static">
              <span class="kpi-card__label">本周访谈</span>
              <strong class="kpi-card__value">{{ scheduleScheduled ? '已安排' : '未安排' }}</strong>
              <span class="kpi-card__date">用于医患沟通页联调</span>
            </div>
          </ElCol>
        </ElRow>
      </div>
    </GlassCard>

    <GlassCard class="outcome-card">
      <h3 class="section-title">疾病适配主视角</h3>
      <p class="section-desc">根据诊断分类「{{ profile?.primaryDiseaseCategory ?? '默认' }}」展示核心疗效指标趋势。</p>
      <ElRow v-if="metrics.length" :gutter="16">
        <ElCol
          v-for="(def, index) in primaryChartDefs"
          :key="def.key"
          :xs="24"
          :lg="primaryChartDefs.length > 1 ? 12 : 24"
        >
          <div class="chart-box" :ref="(el) => setPrimaryChartEl(el, index)" />
        </ElCol>
        <ElCol v-if="records.length" :xs="24" :lg="12">
          <div class="chart-box" ref="reliefChartEl" />
          <div class="relief-legend">
            <span v-for="(label, key) in SYMPTOM_RELIEF_LABELS" :key="key" class="relief-legend__item">
              <i class="relief-legend__dot" :style="{ background: SYMPTOM_RELIEF_COLORS[key] }" />
              {{ label }}
            </span>
          </div>
        </ElCol>
      </ElRow>
      <ElEmpty v-else description="当前日期范围内暂无模拟指标数据" />
    </GlassCard>

    <GlassCard class="outcome-card outcome-card--secondary">
      <h3 class="section-title">常规健康指标</h3>
      <p class="section-desc">血压、血糖、心率、体重等辅助参考趋势；最新数值见上方指标卡片。</p>
      <div v-if="metrics.length" class="chart-box chart-box--wide" ref="secondaryChartEl" />
      <ElEmpty v-else description="暂无常规指标数据" />
    </GlassCard>

    <GlassCard class="outcome-card">
      <h3 class="section-title">历史记录</h3>
      <ElTable v-if="historyRows.length" :data="historyRows" stripe>
        <ElTableColumn prop="recordDate" label="记录日期" min-width="120" />
        <ElTableColumn label="记录时间" min-width="168">
          <template #default="{ row }">{{ formatBeijingDateTime(String(row.recordedAt || row.recordDate)) }}</template>
        </ElTableColumn>
        <ElTableColumn
          v-for="key in historyMetricKeys"
          :key="key"
          :prop="key"
          :label="metricLabel(key)"
          min-width="110"
        />
      </ElTable>

      <h4 class="subsection-title">随访反馈摘要</h4>
      <ElTable v-if="records.length" :data="records" stripe>
        <ElTableColumn label="随访时间" min-width="168">
          <template #default="{ row }">{{ formatBeijingDateTime(row.followUpTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="症状缓解" min-width="100">
          <template #default="{ row }">
            <StatusTag :tone="reliefTone(row.symptomRelief)">{{ reliefLabel(row.symptomRelief) }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="patientFeedback" label="患者反馈" min-width="180" show-overflow-tooltip />
        <ElTableColumn prop="aiAssessment" label="AI 评估" min-width="220" show-overflow-tooltip />
      </ElTable>
      <ElEmpty v-if="!historyRows.length && !records.length" description="暂无历史记录" />
    </GlassCard>

    <ElDialog
      v-model="metricDialogVisible"
      :title="`${selectedMetricDef?.label ?? '指标'} · 历史数据`"
      width="720px"
      :lock-scroll="false"
      modal-class="outcome-dialog-overlay"
      destroy-on-close
      @opened="renderMetricDialogChart"
      @closed="closeMetricDialog"
    >
      <div v-if="selectedMetricHistory.length" class="metric-dialog">
        <div class="metric-dialog__chart" ref="metricDialogChartEl" />
        <ElTable :data="selectedMetricHistory" stripe max-height="320">
          <ElTableColumn prop="recordDate" label="记录日期" min-width="120" />
          <ElTableColumn label="记录时间" min-width="168">
            <template #default="{ row }">{{ formatBeijingDateTime(row.recordedAt || row.recordDate) }}</template>
          </ElTableColumn>
          <ElTableColumn label="数值" min-width="100">
            <template #default="{ row }">
              {{ row.metricValue }}{{ row.unit ? ` ${row.unit}` : '' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="source" label="来源" min-width="100" />
          <ElTableColumn prop="note" label="备注" min-width="140" show-overflow-tooltip />
        </ElTable>
      </div>
      <ElEmpty v-else description="该指标暂无历史记录" />
    </ElDialog>

    <ElDialog
      v-model="patientDetailVisible"
      :title="`${patientDetail?.realName ?? profile?.realName ?? '患者'} · 详细信息`"
      width="760px"
      :lock-scroll="false"
      modal-class="outcome-dialog-overlay"
      destroy-on-close
      @closed="closePatientDetail"
    >
      <div v-loading="patientDetailLoading" class="patient-detail-dialog">
        <template v-if="patientDetail">
          <h4 class="patient-detail-dialog__section">基本信息</h4>
          <ElDescriptions :column="2" border size="small">
            <ElDescriptionsItem label="姓名">{{ displayValue(patientDetail.realName) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="病历号">{{ displayValue(patientDetail.caseNumber) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="性别">{{ displayValue(patientDetail.gender) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="出生日期">{{ displayValue(patientDetail.birthdate) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="年龄">
              {{ patientDetail.age != null ? `${patientDetail.age}${patientDetail.ageType ?? ''}` : '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="看诊状态">{{ visitStateLabel(patientDetail.visitState) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="诊断" :span="2">{{ displayValue(patientDetail.diagnosis) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="初步诊断" :span="2">{{ displayValue(patientDetail.preliminaryDiagnosis) }}</ElDescriptionsItem>
            <ElDescriptionsItem v-if="patientDetail.diseases?.length" label="关联疾病" :span="2">
              <ElTag
                v-for="disease in patientDetail.diseases"
                :key="disease.diseaseId"
                type="info"
                effect="plain"
                class="patient-detail-dialog__tag"
              >
                {{ disease.diseaseName }}
              </ElTag>
            </ElDescriptionsItem>
          </ElDescriptions>

          <h4 class="patient-detail-dialog__section">联系方式</h4>
          <ElDescriptions :column="2" border size="small">
            <ElDescriptionsItem label="手机号">{{ maskPhone(patientDetail.phone) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="邮箱">{{ displayValue(patientDetail.email) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="身份证号">{{ maskIdCard(patientDetail.idCard ?? patientDetail.cardNumber) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊卡号">{{ displayValue(patientDetail.cardNumber) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="家庭住址" :span="2">{{ displayValue(patientDetail.contactAddress ?? patientDetail.homeAddress) }}</ElDescriptionsItem>
          </ElDescriptions>

          <h4 class="patient-detail-dialog__section">就诊信息</h4>
          <ElDescriptions :column="2" border size="small">
            <ElDescriptionsItem label="就诊科室">{{ displayValue(patientDetail.departmentName) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="接诊医生">{{ displayValue(patientDetail.physicianName) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="号别">{{ displayValue(patientDetail.registLevelName) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="结算类别">{{ displayValue(patientDetail.settleCategoryName) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊时间">
              {{ formatBeijingDateTime(patientDetail.visitDate) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="午别">{{ displayValue(patientDetail.noon) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="挂号方式">{{ displayValue(patientDetail.registMethod) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="挂号费">
              {{ patientDetail.registMoney != null ? `¥ ${patientDetail.registMoney}` : '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="是否预约">{{ displayValue(patientDetail.isBook) }}</ElDescriptionsItem>
          </ElDescriptions>

          <h4 class="patient-detail-dialog__section">病历摘要</h4>
          <ElDescriptions :column="1" border size="small">
            <ElDescriptionsItem label="主诉">{{ displayValue(patientDetail.chiefComplaint) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="现病史">{{ displayValue(patientDetail.presentIllness) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="既往史">{{ displayValue(patientDetail.pastHistory) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="过敏史">{{ displayValue(patientDetail.allergy) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="体格检查">{{ displayValue(patientDetail.physique) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="治疗建议">{{ displayValue(patientDetail.treatmentProposal) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="注意事项">{{ displayValue(patientDetail.precautions) }}</ElDescriptionsItem>
          </ElDescriptions>
        </template>
        <ElEmpty v-else-if="!patientDetailLoading" description="暂无患者信息" />
      </div>
    </ElDialog>
  </MedtechStepLayout>
</template>

<style scoped>
.outcome-card {
  padding: var(--space-5);
  margin-block-end: var(--space-4);
}

.outcome-card--secondary {
  background: color-mix(in srgb, var(--color-surface) 92%, var(--color-bg-soft));
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-block-end: var(--space-4);
}

.toolbar__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.toolbar__label {
  color: var(--color-text-muted);
  font-size: 14px;
}

.toolbar__select {
  width: 240px;
}

.toolbar__range-preset {
  width: 140px;
}

.toolbar__range-hint {
  color: var(--color-text-soft);
  font-size: 12px;
}

.profile-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  margin-block-end: var(--space-4);
}

.profile-bar__meta {
  margin-inline-start: var(--space-2);
  color: var(--color-text-muted);
  font-size: 13px;
}

.profile-bar__tags {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.kpi-section {
  margin-block-start: var(--space-2);
}

.kpi-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-end: var(--space-3);
}

.kpi-section__title {
  margin: 0;
  font-size: 16px;
}

.kpi-section__hint {
  color: var(--color-text-soft);
  font-size: 12px;
}

.kpi-row {
  margin-block-start: 0;
}

.kpi-card {
  display: block;
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  min-height: 124px;
  text-align: start;
}

.kpi-card--clickable {
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.15s ease;
}

.kpi-card--clickable:hover {
  border-color: color-mix(in srgb, var(--color-primary) 55%, var(--color-border));
  box-shadow: 0 6px 20px rgba(31, 140, 255, 0.1);
  transform: translateY(-1px);
}

.kpi-card--clickable:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.kpi-card--static {
  min-height: 124px;
}

.kpi-card__label {
  display: block;
  color: var(--color-text-muted);
  font-size: 13px;
}

.kpi-card__value {
  display: block;
  margin-block: var(--space-2);
  font-size: 28px;
  line-height: 1.1;
}

.kpi-card__value small {
  margin-inline-start: 4px;
  font-size: 14px;
  color: var(--color-text-soft);
}

.kpi-card__date {
  color: var(--color-text-soft);
  font-size: 12px;
}

.kpi-card__action {
  display: block;
  margin-block-start: var(--space-2);
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: 600;
}

.metric-dialog__chart {
  min-height: 280px;
  margin-block-end: var(--space-4);
}

.patient-detail-dialog {
  min-height: 200px;
}

.patient-detail-dialog__section {
  margin: var(--space-4) 0 var(--space-3);
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text);
}

.patient-detail-dialog__section:first-child {
  margin-block-start: 0;
}

.patient-detail-dialog__tag {
  margin-inline-end: var(--space-2);
  margin-block-end: var(--space-1);
}

.section-title {
  margin: 0;
  font-size: 18px;
}

.section-desc {
  margin-block: var(--space-2) var(--space-4);
  color: var(--color-text-muted);
  font-size: 14px;
}

.subsection-title {
  margin-block: var(--space-5) var(--space-3);
  font-size: 15px;
}

.chart-box {
  min-height: 300px;
  margin-block-end: var(--space-3);
}

.chart-box--wide {
  min-height: 320px;
}

.relief-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-2);
  padding-inline: var(--space-1);
}

.relief-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.relief-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}
</style>

<style>
/* 弹窗打开时允许滚动背后页面：遮罩不拦截滚轮，弹窗本体仍可交互 */
.outcome-dialog-overlay {
  pointer-events: none;
}

.outcome-dialog-overlay .el-dialog {
  pointer-events: auto;
}
</style>
