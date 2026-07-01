<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElBadge, ElEmpty } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import GlucoseForecastPanel from '@/modules/medtech/follow-up/components/GlucoseForecastPanel.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { beijingYmdAddDays, beijingTodayYmd } from '@/shared/utils/beijingDate'
import type { FollowUpHealthMetric, RevisitRequest } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  registerId: number
}>()

const loading = ref(false)
const homeMetrics = ref<FollowUpHealthMetric[]>([])
const baselineMetrics = ref<FollowUpHealthMetric[]>([])
const pendingRevisits = ref<RevisitRequest[]>([])

const chartMetrics = computed(() => [...baselineMetrics.value, ...homeMetrics.value])

const pendingCount = computed(() => pendingRevisits.value.length)

async function loadData() {
  if (!props.registerId) return
  loading.value = true
  try {
    const from = beijingYmdAddDays(-30)
    const [patientReport, uciBaseline, revisits] = await Promise.all([
      medtechFollowUpApi.getMetrics(props.registerId, {
        from,
        metricKeys: ['blood_glucose'],
        sourceType: 'patient_report',
      }),
      medtechFollowUpApi.getMetrics(props.registerId, {
        from,
        metricKeys: ['blood_glucose'],
        sourceType: 'uci_import',
      }),
      medtechFollowUpApi.listRevisitRequests().catch(() => []),
    ])
    homeMetrics.value = patientReport
    baselineMetrics.value = uciBaseline
    pendingRevisits.value = revisits.filter(
      (item) => item.registerId === props.registerId && item.status === 'pending',
    )
  } finally {
    loading.value = false
  }
}

watch(
  () => props.registerId,
  () => {
    void loadData()
  },
)

onMounted(() => {
  void loadData()
})

defineExpose({ reload: loadData })
</script>

<template>
  <GlassCard class="home-glucose-panel" v-loading="loading">
    <div class="panel-head">
      <div>
        <h3 class="panel-title">居家自测血糖</h3>
        <p class="panel-desc">
          实测曲线仅展示患者自录数据；灰色基线为 UCI 演示导入（uci_import）
        </p>
      </div>
      <ElBadge v-if="pendingCount" :value="pendingCount" type="danger">
        <StatusTag tone="warning">待处理复诊申请</StatusTag>
      </ElBadge>
    </div>

    <div v-if="pendingRevisits.length" class="revisit-list">
      <div v-for="item in pendingRevisits" :key="item.id" class="revisit-item">
        <StatusTag :tone="item.urgency === 'urgent' ? 'danger' : 'warning'">
          {{ item.urgency === 'urgent' ? '紧急' : '普通' }}
        </StatusTag>
        <span>{{ item.reason }}</span>
        <small>{{ item.createdAt?.slice(0, 16) }}</small>
      </div>
    </div>

    <GlucoseForecastPanel
      v-if="registerId"
      :register-id="registerId"
      :metrics="chartMetrics"
      mode="doctor"
    />
    <ElEmpty v-else description="请选择患者" />
  </GlassCard>
</template>

<style scoped>
.home-glucose-panel {
  padding: var(--space-5);
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

.revisit-list {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.revisit-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(245, 158, 11, 0.08);
  font-size: 14px;
}

.revisit-item small {
  margin-left: auto;
  color: var(--color-text-muted);
}
</style>
