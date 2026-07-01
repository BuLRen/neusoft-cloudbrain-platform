<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElEmpty } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { LastVisitSnapshot, ProfessionalMetricItem } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    registerId?: number
    patientId?: number
    compact?: boolean
    mode?: 'doctor' | 'patient'
  }>(),
  {
    compact: false,
    mode: 'doctor',
  },
)

const loading = ref(false)
const snapshot = ref<LastVisitSnapshot | null>(null)

/** 上次看诊仅展示核心糖代谢指标，避免信息过载 */
const PRIORITY_METRIC_KEYS = ['hba1c', 'fasting_glucose', 'postprandial_glucose']

const metricEntries = computed(() => {
  const metrics = snapshot.value?.professionalMetrics ?? {}
  const all = Object.entries(metrics).map(([key, item]) => ({
    key,
    ...(item as ProfessionalMetricItem),
  }))
  const prioritized = PRIORITY_METRIC_KEYS.map((key) => all.find((m) => m.key === key)).filter(
    (m): m is (typeof all)[number] => Boolean(m),
  )
  const list = prioritized.length ? prioritized : all
  const maxCount = props.compact ? 3 : 4
  return list.slice(0, maxCount)
})

async function loadSnapshot() {
  if (!props.registerId && !props.patientId) return
  loading.value = true
  try {
    if (props.mode === 'patient') {
      snapshot.value = await medtechFollowUpApi.getPatientLastVisit({
        patientId: props.patientId,
        registerId: props.registerId,
      })
    } else if (props.registerId) {
      snapshot.value = await medtechFollowUpApi.getLastVisit(props.registerId)
    }
  } catch {
    snapshot.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.registerId, props.patientId, props.mode],
  () => {
    void loadSnapshot()
  },
)

onMounted(() => {
  void loadSnapshot()
})
</script>

<template>
  <GlassCard class="last-visit-panel" :class="{ compact }" v-loading="loading">
    <div class="panel-head">
      <div>
        <h3 class="panel-title">上次看诊</h3>
        <p class="panel-desc">院内检验数据（演示）</p>
      </div>
      <StatusTag tone="neutral">只读</StatusTag>
    </div>

    <template v-if="snapshot">
      <div class="visit-meta">
        <span v-if="snapshot.visitDate">看诊日期：{{ snapshot.visitDate }}</span>
        <span v-if="snapshot.departmentName">{{ snapshot.departmentName }}</span>
        <span v-if="snapshot.doctorName">{{ snapshot.doctorName }}</span>
      </div>
      <p v-if="snapshot.diagnosisSummary" class="diagnosis">{{ snapshot.diagnosisSummary }}</p>

      <div class="metric-grid">
        <div v-for="item in metricEntries" :key="item.key" class="metric-card">
          <span class="metric-label">{{ item.label ?? item.key }}</span>
          <strong class="metric-value">
            {{ item.value }}
            <small>{{ item.unit }}</small>
          </strong>
        </div>
      </div>
      <p v-if="metricEntries.length" class="metric-hint">仅展示核心糖代谢指标，完整检验报告以院内系统为准。</p>
    </template>
    <ElEmpty v-else description="暂无上次看诊快照" />
  </GlassCard>
</template>

<style scoped>
.last-visit-panel {
  padding: var(--space-5);
}

.last-visit-panel.compact {
  padding: var(--space-4);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.panel-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.panel-desc {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.visit-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-3);
}

.diagnosis {
  margin: 0 0 var(--space-4);
  line-height: 1.6;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--space-3);
}

.metric-card {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.metric-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-1);
}

.metric-value {
  font-size: 18px;
}

.metric-value small {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-muted);
}

.metric-hint {
  margin: var(--space-3) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
