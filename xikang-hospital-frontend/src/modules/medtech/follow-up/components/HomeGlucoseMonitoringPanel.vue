<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElEmpty } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import GlucoseForecastPanel from '@/modules/medtech/follow-up/components/GlucoseForecastPanel.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { beijingYmdAddDays } from '@/shared/utils/beijingDate'
import type { FollowUpHealthMetric } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  registerId: number
}>()

const loading = ref(false)
const homeMetrics = ref<FollowUpHealthMetric[]>([])
const baselineMetrics = ref<FollowUpHealthMetric[]>([])

const chartMetrics = computed(() => [...baselineMetrics.value, ...homeMetrics.value])

async function loadData() {
  if (!props.registerId) return
  loading.value = true
  try {
    const from = beijingYmdAddDays(-30)
    const [patientReport, uciBaseline] = await Promise.all([
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
    ])
    homeMetrics.value = patientReport
    baselineMetrics.value = uciBaseline
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
          实测曲线仅展示患者自录数据；灰色基线为 UCI 演示导入（uci_import）。模型建议复诊时，请通过沟通提醒患者自行挂号。
        </p>
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
</style>
