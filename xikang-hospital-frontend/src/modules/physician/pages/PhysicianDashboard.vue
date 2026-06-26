<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type PhysicianHistoricalSummary, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useAuthStore } from '@/app/stores/auth'
import { physicianRoute, resumePathForVisitState, visitStateLabel, VISIT_STATE } from '../constants/visitState'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const patients = ref<PhysicianPatient[]>([])
const historical = ref<PhysicianHistoricalSummary | null>(null)

const stats = computed(() => {
  const waiting = patients.value.filter((p) => p.visitState === VISIT_STATE.REGISTERED).length
  const inProgress = patients.value.filter((p) => p.visitState === VISIT_STATE.IN_PROGRESS).length
  const resultsReady = patients.value.filter((p) => p.visitState === VISIT_STATE.EXAM_COMPLETED).length
  return {
    waiting,
    inProgress,
    resultsReady,
    todayCompleted: historical.value?.todayCompletedVisits ?? 0,
  }
})

const kpiCards = computed(() => [
  { title: '待接诊', value: stats.value.waiting, tone: 'warning' as const, to: '/physician/queue' },
  { title: '接诊中', value: stats.value.inProgress, tone: 'primary' as const, to: '/physician/queue' },
  { title: '待查看结果', value: stats.value.resultsReady, tone: 'ai' as const, to: '/physician/results' },
  { title: '今日完成看诊', value: stats.value.todayCompleted, tone: 'success' as const, to: '/physician/queue' },
])

const historicalCards = computed(() => {
  const h = historical.value
  return [
    { title: '累计完成看诊', value: h?.totalCompletedVisits ?? 0, hint: `今日 +${h?.todayCompletedVisits ?? 0}`, tone: 'success' as const },
    { title: '累计服务患者', value: h?.uniquePatientsServed ?? 0, hint: '按患者去重', tone: 'primary' as const },
    { title: '累计开立检查', value: h?.totalCheckOrders ?? 0, hint: `今日 +${h?.todayCheckOrders ?? 0}`, tone: 'warning' as const },
    { title: '累计开立检验', value: h?.totalInspectionOrders ?? 0, hint: `今日 +${h?.todayInspectionOrders ?? 0}`, tone: 'ai' as const },
  ]
})

const waitingPreview = computed(() =>
  patients.value
    .filter((p) => p.visitState === VISIT_STATE.REGISTERED || p.visitState === VISIT_STATE.IN_PROGRESS)
    .slice(0, 5),
)

const resultsPending = computed(() =>
  patients.value.filter((p) => p.visitState === VISIT_STATE.EXAM_COMPLETED).slice(0, 8),
)

const quickEntries = [
  { title: '进入接诊队列', description: '查看待诊患者并开始接诊流程。', path: '/physician/queue', tone: 'primary' as const },
  { title: '查看检查结果', description: '查看已完成检查/检验的患者结果。', path: '/physician/results', tone: 'ai' as const },
]

function aiSummary(patient: PhysicianPatient) {
  const summary = patient.aiConsultSummary
  if (!summary) return '暂无 AI 预问诊摘要'
  return summary.chiefComplaint || summary.aiSummary || summary.suggestedExam || '已完成 AI 预问诊'
}

function open(path: string) {
  router.push(path)
}

function openPatient(patient: PhysicianPatient) {
  const path = resumePathForVisitState(patient.visitState)
  router.push(physicianRoute(path, patient.registerId))
}

async function load() {
  loading.value = true
  try {
    const [patientPage, summary] = await Promise.all([
      physicianApi.patients({ page: 1, size: 100 }),
      physicianApi.statsSummary(),
    ])
    patients.value = patientPage.records
    historical.value = summary
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="physician-dashboard u-page-grid">
    <PageHeader
      :title="`${authStore.realName || '医生'} · 你好`"
      eyebrow="Role Physician / Overview"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
        <ElButton type="primary" @click="open('/physician/queue')">进入接诊队列</ElButton>
      </template>
    </PageHeader>

    <section class="kpi-grid" v-loading="loading">
      <GlassCard v-for="card in kpiCards" :key="card.title" class="kpi-card" @click="open(card.to)">
        <StatusTag :tone="card.tone">{{ card.title }}</StatusTag>
        <strong>{{ card.value }}</strong>
        <span class="kpi-hint">点击进入相关页面</span>
      </GlassCard>
    </section>

    <section class="historical-section" v-loading="loading">
      <div class="panel__header historical-section__header">
        <div>
          <h3>历史累计统计</h3>
          <p>统计该医生名下所有历史接诊与开单数据，含今日增量。</p>
        </div>
        <StatusTag tone="neutral" class="historical-section__tag">全时段</StatusTag>
      </div>
      <div class="kpi-grid">
        <GlassCard v-for="card in historicalCards" :key="card.title" class="kpi-card kpi-card--static">
          <StatusTag :tone="card.tone">{{ card.title }}</StatusTag>
          <strong>{{ card.value }}</strong>
          <span class="kpi-hint">{{ card.hint }}</span>
        </GlassCard>
      </div>
    </section>

    <section class="content-grid">
      <GlassCard class="panel panel--list">
        <div class="panel__header">
          <div>
            <h3>待诊患者预览</h3>
            <p>最近待接诊与接诊中的患者，最多显示 5 条。</p>
          </div>
          <ElButton link type="primary" @click="open('/physician/queue')">查看全部</ElButton>
        </div>
        <div v-if="waitingPreview.length > 0" class="list-stack">
          <button
            v-for="patient in waitingPreview"
            :key="patient.registerId"
            type="button"
            class="list-item list-item--column"
            @click="openPatient(patient)"
          >
            <div class="panel__header">
              <strong>{{ patient.realName }} · {{ patient.caseNumber }}</strong>
              <StatusTag :tone="visitStateLabel(patient.visitState).tone">
                {{ visitStateLabel(patient.visitState).text }}
              </StatusTag>
            </div>
            <p>{{ aiSummary(patient) }}</p>
          </button>
        </div>
        <div v-else class="empty-tip">
          <StatusTag tone="success">暂无待诊</StatusTag>
          <p>当前没有待接诊或接诊中的患者。</p>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--quick">
        <div class="panel__header">
          <div>
            <h3>快捷入口</h3>
            <p>从首页直接进入最常用的诊疗页面。</p>
          </div>
        </div>
        <div class="quick-grid">
          <button v-for="item in quickEntries" :key="item.title" type="button" class="quick-card" @click="open(item.path)">
            <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
            <p>{{ item.description }}</p>
          </button>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--results">
        <div class="panel__header">
          <div>
            <h3>待查看结果</h3>
            <p>检查/检验已完成、等待医生查看结果的患者。</p>
          </div>
          <ElButton link type="primary" @click="open('/physician/results')">前往查看结果</ElButton>
        </div>
        <div v-if="resultsPending.length > 0" class="list-stack">
          <button
            v-for="patient in resultsPending"
            :key="patient.registerId"
            type="button"
            class="list-item"
            @click="openPatient(patient)"
          >
            <div>
              <strong>{{ patient.realName }}</strong>
              <p>{{ patient.caseNumber }} · 检查检验已完成</p>
            </div>
            <StatusTag tone="ai">查看结果</StatusTag>
          </button>
        </div>
        <div v-else class="empty-tip">
          <StatusTag tone="neutral">暂无待查看</StatusTag>
          <p>当前没有等待查看结果的患者。</p>
        </div>
      </GlassCard>
    </section>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.kpi-card,
.panel {
  padding: var(--space-5);
}

.kpi-card {
  display: grid;
  gap: var(--space-3);
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast) var(--ease-standard);
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.kpi-card--static {
  cursor: default;
}

.kpi-card--static:hover {
  transform: none;
  box-shadow: none;
}

.historical-section {
  display: grid;
  gap: var(--space-4);
  width: 100%;
}

.historical-section__header {
  width: 100%;
}

.historical-section__header h3 {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.historical-section__tag {
  flex-shrink: 0;
}

.kpi-card strong {
  font-size: 28px;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  color: var(--color-text-soft);
  font-size: 0.78rem;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: var(--space-4);
}

.panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.panel__header p,
.list-item p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 0.85rem;
}

.panel--list,
.panel--results {
  grid-column: 1;
}

.panel--quick {
  grid-column: 2;
  grid-row: span 2;
}

.list-stack,
.quick-grid {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.quick-card,
.list-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
}

.quick-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.quick-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.quick-card p {
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 0.85rem;
}

.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4);
  width: 100%;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.list-item:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.list-item--column {
  display: grid;
}

.empty-tip {
  display: grid;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  padding: var(--space-4);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  text-align: center;
}

.empty-tip p {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

@media (max-width: 1200px) {
  .kpi-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .panel--list,
  .panel--results,
  .panel--quick {
    grid-column: auto;
    grid-row: auto;
  }
}
</style>
