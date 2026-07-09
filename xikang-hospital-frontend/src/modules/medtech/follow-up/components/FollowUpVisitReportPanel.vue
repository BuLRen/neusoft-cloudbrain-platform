<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElInput, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { metricLabel } from '@/shared/constants/outcomeCharts'
import type {
  FollowUpHealthMetric,
  FollowUpVisitReport,
  FollowUpVisitReportRecoveryStatus,
  LastVisitLabItem,
  LastVisitSnapshot,
} from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  registerId?: number
  lastVisitSnapshot?: LastVisitSnapshot | null
  latestMetrics?: Map<string, FollowUpHealthMetric>
}>()

const loading = ref(false)
const saving = ref(false)
const finalizing = ref(false)
const report = ref<FollowUpVisitReport | null>(null)

const observationText = ref('')
const conclusionText = ref('')
const recoveryStatus = ref<FollowUpVisitReportRecoveryStatus>('unknown')

const isFinalized = computed(() => report.value?.status === 'finalized')

const snapshotForDisplay = computed(() => props.lastVisitSnapshot ?? report.value?.lastVisitSnapshot ?? null)

const metricComparisons = computed(() => {
  const snapshot = snapshotForDisplay.value
  const latest = props.latestMetrics
  if (!snapshot?.labItems?.length || !latest?.size) {
    return []
  }
  return snapshot.labItems
    .map((item) => buildComparison(item, latest))
    .filter((item): item is NonNullable<typeof item> => item != null)
    .slice(0, 6)
})

function buildComparison(item: LastVisitLabItem, latest: Map<string, FollowUpHealthMetric>) {
  const metricKey = item.metricCode ?? item.code
  if (!metricKey) return null
  const current = latest.get(metricKey)
  if (!current) return null
  const visitValue = Number(item.metricValue ?? item.value)
  const currentValue = Number(current.metricValue)
  if (Number.isNaN(visitValue) || Number.isNaN(currentValue)) return null
  let trend: 'up' | 'down' | 'flat' = 'flat'
  if (currentValue > visitValue) trend = 'up'
  else if (currentValue < visitValue) trend = 'down'
  return {
    key: metricKey,
    label: metricLabel(metricKey),
    visitValue: item.metricValue,
    currentValue: current.metricValue,
    unit: item.unit ?? current.unit,
    trend,
  }
}

function trendLabel(trend: 'up' | 'down' | 'flat') {
  if (trend === 'up') return '升高'
  if (trend === 'down') return '降低'
  return '持平'
}

function trendTone(trend: 'up' | 'down' | 'flat'): 'success' | 'warning' | 'info' {
  if (trend === 'down') return 'success'
  if (trend === 'up') return 'warning'
  return 'info'
}

async function loadReport() {
  if (!props.registerId) {
    report.value = null
    return
  }
  loading.value = true
  try {
    const res = await medtechFollowUpApi.getLatestVisitReport(props.registerId)
    if (res.exists === false) {
      report.value = { registerId: props.registerId, status: 'draft' }
      observationText.value = ''
      conclusionText.value = ''
      recoveryStatus.value = 'unknown'
      return
    }
    report.value = res
    observationText.value = res.observationText ?? ''
    conclusionText.value = res.conclusionText ?? ''
    recoveryStatus.value = res.recoveryStatus ?? 'unknown'
  } catch {
    ElMessage.error('加载随访报告失败')
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!props.registerId) return
  saving.value = true
  try {
    const saved = await medtechFollowUpApi.saveVisitReport({
      id: report.value?.id,
      registerId: props.registerId,
      observationText: observationText.value,
      conclusionText: conclusionText.value,
      recoveryStatus: recoveryStatus.value,
    })
    report.value = saved
    ElMessage.success('草稿已保存')
  } catch {
    // unified error toast
  } finally {
    saving.value = false
  }
}

async function finalizeReport() {
  if (!report.value?.id) {
    await saveDraft()
  }
  if (!report.value?.id) return
  finalizing.value = true
  try {
    const finalized = await medtechFollowUpApi.finalizeVisitReport(report.value.id)
    report.value = finalized
    ElMessage.success('随访报告已定稿')
  } catch {
    // unified error toast
  } finally {
    finalizing.value = false
  }
}

watch(
  () => props.registerId,
  () => {
    void loadReport()
  },
  { immediate: true },
)
</script>

<template>
  <GlassCard class="visit-report-panel" v-loading="loading">
    <header class="visit-report-panel__head">
      <div>
        <h3>随访报告</h3>
        <p class="visit-report-panel__hint">关联上次看诊，记录本期观察与结论</p>
      </div>
      <ElTag v-if="isFinalized" type="success" effect="plain">已定稿</ElTag>
      <ElTag v-else type="info" effect="plain">草稿</ElTag>
    </header>

    <section class="visit-report-panel__section">
      <h4>上次看诊摘要</h4>
      <template v-if="snapshotForDisplay">
        <p v-if="snapshotForDisplay.diagnosis"><strong>诊断</strong> {{ snapshotForDisplay.diagnosis }}</p>
        <p v-if="snapshotForDisplay.chiefComplaint"><strong>主诉</strong> {{ snapshotForDisplay.chiefComplaint }}</p>
        <p v-if="snapshotForDisplay.visitDate" class="visit-report-panel__muted">
          看诊日期 {{ snapshotForDisplay.visitDate }}
        </p>
      </template>
      <p v-else class="visit-report-panel__muted">暂无上次看诊快照</p>
    </section>

    <section class="visit-report-panel__section">
      <h4>本期观察</h4>
      <ElInput
        v-model="observationText"
        type="textarea"
        :rows="4"
        :disabled="isFinalized"
        placeholder="症状变化、用药依从性、不良反应、生活方式等"
      />
    </section>

    <section v-if="metricComparisons.length" class="visit-report-panel__section">
      <h4>指标对照</h4>
      <ul class="visit-report-panel__metrics">
        <li v-for="item in metricComparisons" :key="item.key">
          <span>{{ item.label }}</span>
          <span>{{ item.visitValue }} → {{ item.currentValue }} {{ item.unit ?? '' }}</span>
          <ElTag size="small" :type="trendTone(item.trend)" effect="plain">{{ trendLabel(item.trend) }}</ElTag>
        </li>
      </ul>
    </section>

    <section class="visit-report-panel__section">
      <h4>随访结论</h4>
      <ElSelect
        v-model="recoveryStatus"
        class="visit-report-panel__recovery"
        :disabled="isFinalized"
        placeholder="恢复评价"
      >
        <ElOption label="改善" value="improved" />
        <ElOption label="稳定" value="stable" />
        <ElOption label="加重" value="worsened" />
        <ElOption label="待评估" value="unknown" />
      </ElSelect>
      <ElInput
        v-model="conclusionText"
        type="textarea"
        :rows="3"
        :disabled="isFinalized"
        placeholder="下步建议、是否建议复诊等"
      />
    </section>

    <div class="visit-report-panel__actions">
      <ElButton :disabled="!registerId || isFinalized" :loading="saving" @click="saveDraft">保存草稿</ElButton>
      <ElButton
        type="primary"
        :disabled="!registerId || isFinalized"
        :loading="finalizing"
        @click="finalizeReport"
      >
        定稿
      </ElButton>
    </div>
  </GlassCard>
</template>

<style scoped>
.visit-report-panel {
  position: sticky;
  top: var(--space-4);
  padding: var(--space-4);
  align-self: start;
}

.visit-report-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.visit-report-panel__head h3 {
  margin: 0;
}

.visit-report-panel__hint,
.visit-report-panel__muted {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.visit-report-panel__section {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.visit-report-panel__section h4 {
  margin: 0;
  font-size: 14px;
}

.visit-report-panel__section p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.visit-report-panel__metrics {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.visit-report-panel__metrics li {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: var(--space-2);
  align-items: center;
  font-size: 13px;
}

.visit-report-panel__recovery {
  width: 100%;
}

.visit-report-panel__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}
</style>
