<script setup lang="ts">

import { computed, onMounted, ref, watch } from 'vue'

import { ElEmpty } from 'element-plus'

import GlassCard from '@/shared/components/GlassCard.vue'

import StatusTag from '@/shared/components/StatusTag.vue'

import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'

import type {

  LastVisitLabItem,

  LastVisitSnapshot,

  PrescriptionSummaryItem,

  ProfessionalMetricItem,

} from '@/shared/types/medtechFollowUp'



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



const PRIORITY_METRIC_KEYS = ['hba1c', 'fasting_glucose', 'postprandial_glucose']



function flagTone(flag?: string) {

  if (flag === 'high' || flag === 'critical') return 'warning'

  if (flag === 'low') return 'info'

  return 'success'

}



function flagLabel(flag?: string) {

  if (flag === 'high') return '偏高'

  if (flag === 'low') return '偏低'

  if (flag === 'critical') return '危急'

  return '正常'

}



function normalizeLabItems(raw: LastVisitSnapshot | null): LastVisitLabItem[] {

  if (!raw) return []

  if (raw.labItems?.length) {

    return raw.labItems

  }

  if (raw.labPanel?.length) {

    return raw.labPanel.map((item) => ({

      metricCode: item.code ?? item.metricCode,

      label: item.label,

      metricValue: item.value ?? item.metricValue,

      unit: item.unit,

      refRange: item.refRange,

      abnormalFlag: item.flag ?? item.abnormalFlag,

    }))

  }

  const metrics = raw.professionalMetrics

  if (!metrics) return []

  return Object.entries(metrics).map(([key, item]) => ({

    metricCode: key,

    label: (item as ProfessionalMetricItem).label ?? key,

    metricValue: (item as ProfessionalMetricItem).value,

    unit: (item as ProfessionalMetricItem).unit,

    abnormalFlag: (item as ProfessionalMetricItem).abnormalFlag,

  }))

}



const labItems = computed(() => {

  const items = normalizeLabItems(snapshot.value)

  const prioritized = PRIORITY_METRIC_KEYS.map((key) =>

    items.find((item) => (item.metricCode ?? item.code) === key),

  ).filter((item): item is LastVisitLabItem => Boolean(item))

  const list = prioritized.length ? prioritized : items

  const maxCount = props.compact ? 3 : 6

  return list.slice(0, maxCount)

})



const prescriptionItems = computed(() => {

  const raw = snapshot.value?.prescriptionSummary

  if (!raw) return [] as PrescriptionSummaryItem[]

  if (typeof raw === 'string') {

    try {

      return JSON.parse(raw) as PrescriptionSummaryItem[]

    } catch {

      return []

    }

  }

  return raw

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

        <p class="panel-desc">{{ mode === 'patient' ? '查看您上次在院看诊时的检验与诊断摘要' : '院内检验数据（演示）' }}</p>

      </div>

      <StatusTag tone="neutral">只读</StatusTag>

    </div>



    <template v-if="snapshot">

      <div class="visit-meta">

        <span v-if="snapshot.visitDate">看诊日期：{{ snapshot.visitDate }}</span>

        <span v-if="snapshot.departmentName">{{ snapshot.departmentName }}</span>

        <span v-if="snapshot.doctorName">{{ snapshot.doctorName }}</span>

      </div>



      <div v-if="snapshot.chiefComplaint" class="clinical-block">

        <span class="clinical-block__label">主诉</span>

        <p>{{ snapshot.chiefComplaint }}</p>

      </div>



      <p v-if="snapshot.diagnosisSummary" class="diagnosis">

        <span class="clinical-block__label">诊断</span>

        {{ snapshot.diagnosisSummary }}

      </p>



      <div v-if="labItems.length" class="metric-grid">

        <div

          v-for="item in labItems"

          :key="item.metricCode ?? item.code"

          class="metric-card"

          :class="{ 'metric-card--abnormal': item.abnormalFlag && item.abnormalFlag !== 'normal' }"

        >

          <div class="metric-card__head">

            <span class="metric-label">{{ item.label ?? item.metricCode }}</span>

            <StatusTag :tone="flagTone(item.abnormalFlag ?? item.flag)">

              {{ flagLabel(item.abnormalFlag ?? item.flag) }}

            </StatusTag>

          </div>

          <strong class="metric-value">

            {{ item.metricValue ?? item.value }}

            <small>{{ item.unit }}</small>

          </strong>

          <span v-if="item.refRange" class="metric-ref">参考：{{ item.refRange }}</span>

        </div>

      </div>

      <p v-if="labItems.length" class="metric-hint">以上为上次在院检验快照，居家血糖监测请查看「血糖监测」页签。</p>



      <div v-if="snapshot.treatmentAdvice" class="clinical-block clinical-block--advice">

        <span class="clinical-block__label">医嘱建议</span>

        <p>{{ snapshot.treatmentAdvice }}</p>

      </div>



      <details v-if="prescriptionItems.length" class="prescription-block">

        <summary>上次处方（{{ prescriptionItems.length }}）</summary>

        <ul class="prescription-list">

          <li v-for="(item, index) in prescriptionItems" :key="`${item.drugId}-${index}`">

            <strong>{{ item.drugName }}</strong>

            <span v-if="item.drugUsage">{{ item.drugUsage }}</span>

          </li>

        </ul>

      </details>

    </template>

    <ElEmpty v-else description="暂无上次看诊快照，请运行种子脚本补齐演示数据" />

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



.clinical-block {

  margin-bottom: var(--space-3);

  line-height: 1.6;

}



.clinical-block p {

  margin: var(--space-1) 0 0;

}



.clinical-block--advice {

  margin-top: var(--space-4);

  padding: var(--space-3);

  border-radius: var(--radius-lg);

  background: color-mix(in srgb, var(--color-primary) 6%, transparent);

}



.clinical-block__label {

  display: block;

  font-size: 12px;

  font-weight: 600;

  color: var(--color-text-muted);

  margin-bottom: var(--space-1);

}



.diagnosis {

  margin: 0 0 var(--space-4);

  line-height: 1.6;

}



.metric-grid {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));

  gap: var(--space-3);

}



.metric-card {

  padding: var(--space-3);

  border: 1px solid var(--color-border);

  border-radius: var(--radius-lg);

  background: var(--color-surface);

}



.metric-card--abnormal {

  border-color: color-mix(in srgb, var(--color-warning) 40%, var(--color-border));

  background: color-mix(in srgb, var(--color-warning) 5%, var(--color-surface));

}



.metric-card__head {

  display: flex;

  align-items: center;

  justify-content: space-between;

  gap: var(--space-2);

  margin-bottom: var(--space-2);

}



.metric-label {

  font-size: 12px;

  color: var(--color-text-muted);

}



.metric-value {

  display: block;

  font-size: 20px;

}



.metric-value small {

  font-size: 12px;

  font-weight: 400;

  color: var(--color-text-muted);

}



.metric-ref {

  display: block;

  margin-top: var(--space-1);

  font-size: 11px;

  color: var(--color-text-muted);

}



.metric-hint {

  margin: var(--space-3) 0 0;

  font-size: 12px;

  color: var(--color-text-muted);

}



.prescription-block {

  margin-top: var(--space-4);

}



.prescription-block summary {

  cursor: pointer;

  font-weight: 600;

  margin-bottom: var(--space-2);

}



.prescription-list {

  margin: 0;

  padding-left: 1.2rem;

  display: grid;

  gap: var(--space-2);

}



.prescription-list li {

  line-height: 1.5;

}



.prescription-list span {

  display: block;

  font-size: 12px;

  color: var(--color-text-muted);

}

</style>

