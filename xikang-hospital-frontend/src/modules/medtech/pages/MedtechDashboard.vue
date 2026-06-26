<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import {
  medtechApi,
  type CheckApplication,
  type DisposalApplication,
  type InspectionApplication,
  type MedtechHistoricalSummary,
  type MedtechProfile,
  type MedtechTechType,
} from '@/shared/api/modules/medtech'
import { useAuthStore } from '@/app/stores/auth'

const TECH_TYPE_LABEL: Record<MedtechTechType, string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const TECH_TYPE_TONE: Record<MedtechTechType, 'primary' | 'success' | 'warning'> = {
  check: 'primary',
  inspection: 'success',
  disposal: 'warning',
}

const PENDING_STATE: Record<MedtechTechType, string> = {
  check: '待检查',
  inspection: '待检验',
  disposal: '待处置',
}

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const profile = ref<MedtechProfile | null>(null)
const pendingChecks = ref<CheckApplication[]>([])
const pendingInspections = ref<InspectionApplication[]>([])
const pendingDisposals = ref<DisposalApplication[]>([])
const historical = ref<MedtechHistoricalSummary | null>(null)

const pendingApplications = computed(() =>
  [...pendingChecks.value, ...pendingInspections.value, ...pendingDisposals.value].sort((a, b) =>
    (a.creationTime ?? '').localeCompare(b.creationTime ?? ''),
  ),
)

const pendingPreview = computed(() => pendingApplications.value.slice(0, 10))

const kpiCards = computed(() => [
  { title: '待处理检查', value: pendingChecks.value.length, tone: 'primary' as const },
  { title: '待处理检验', value: pendingInspections.value.length, tone: 'success' as const },
  { title: '待处理处置', value: pendingDisposals.value.length, tone: 'warning' as const },
  { title: '今日已完成', value: historical.value?.todayCompletedAll ?? 0, tone: 'ai' as const },
])

const historicalCards = computed(() => {
  const h = historical.value
  return [
    { title: '累计完成检查', value: h?.totalCompletedChecks ?? 0, hint: `今日 +${h?.todayCompletedChecks ?? 0}`, tone: 'primary' as const },
    { title: '累计完成检验', value: h?.totalCompletedInspections ?? 0, hint: `今日 +${h?.todayCompletedInspections ?? 0}`, tone: 'success' as const },
    { title: '累计完成处置', value: h?.totalCompletedDisposals ?? 0, hint: `今日 +${h?.todayCompletedDisposals ?? 0}`, tone: 'warning' as const },
    { title: '累计完成合计', value: h?.totalCompletedAll ?? 0, hint: `今日 +${h?.todayCompletedAll ?? 0}`, tone: 'ai' as const },
  ]
})

const scopeHint = computed(() => {
  if (!profile.value) return ''
  if (profile.value.adminAllAccess) return '管理员视图：显示全部医技科室申请。'
  if (profile.value.departmentName) return `当前科室：${profile.value.departmentName}`
  return '当前仅显示分配给本科室执行的申请。'
})

const quickEntries = [
  { title: '进入申请队列', description: '查看并执行待处理的检查、检验与处置申请。', path: '/medtech/check-queue', tone: 'primary' as const },
]

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function open(path: string) {
  router.push(path)
}

async function load() {
  loading.value = true
  try {
    const [checks, inspections, disposals, userProfile, summary] = await Promise.all([
      medtechApi.checkApplications({ checkState: PENDING_STATE.check }),
      medtechApi.inspectionApplications({ inspectionState: PENDING_STATE.inspection }),
      medtechApi.disposalApplications({ disposalState: PENDING_STATE.disposal }),
      medtechApi.profile().catch(() => null),
      medtechApi.statsSummary(),
    ])
    pendingChecks.value = checks
    pendingInspections.value = inspections
    pendingDisposals.value = disposals
    profile.value = userProfile
    historical.value = summary
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="medtech-dashboard u-page-grid">
    <PageHeader
      :title="`${authStore.realName || '医技人员'} · 你好`"
      :description="scopeHint || '汇总待处理的检查、检验与处置申请，快速进入执行队列。'"
      eyebrow="Role Medtech / Overview"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
        <ElButton type="primary" @click="open('/medtech/check-queue')">进入申请队列</ElButton>
      </template>
    </PageHeader>

    <section class="kpi-grid" v-loading="loading">
      <GlassCard v-for="card in kpiCards" :key="card.title" class="kpi-card" @click="open('/medtech/check-queue')">
        <StatusTag :tone="card.tone">{{ card.title }}</StatusTag>
        <strong>{{ card.value }}</strong>
        <span class="kpi-hint">点击进入申请队列</span>
      </GlassCard>
    </section>

    <section class="historical-section" v-loading="loading">
      <div class="panel__header historical-section__header">
        <div>
          <h3>历史累计统计</h3>
          <p>统计本科室（或全院）所有已完成的检查、检验与处置项目，含今日增量。</p>
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
            <h3>最新待处理申请</h3>
            <p>按申请时间排序，最多显示 10 条待执行记录。</p>
          </div>
          <ElButton link type="primary" @click="open('/medtech/check-queue')">查看全部</ElButton>
        </div>
        <div v-if="pendingPreview.length > 0" class="list-stack">
          <button
            v-for="item in pendingPreview"
            :key="`${item.techType}-${item.id}`"
            type="button"
            class="list-item list-item--column"
            @click="open('/medtech/check-queue')"
          >
            <div class="panel__header">
              <div class="list-item__title">
                <StatusTag :tone="TECH_TYPE_TONE[item.techType]">{{ TECH_TYPE_LABEL[item.techType] }}</StatusTag>
                <strong>{{ item.patientName || '-' }} · {{ item.techName || '-' }}</strong>
              </div>
              <span class="list-item__time">{{ formatTime(item.creationTime) }}</span>
            </div>
            <p>{{ item.caseNumber || '-' }} · {{ item.statusText || '待处理' }}</p>
          </button>
        </div>
        <div v-else class="empty-tip">
          <StatusTag tone="success">暂无待处理</StatusTag>
          <p>当前没有待执行的医技申请。</p>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--quick">
        <div class="panel__header">
          <div>
            <h3>快捷入口</h3>
            <p>从首页直接进入医技执行页面。</p>
          </div>
        </div>
        <div class="quick-grid">
          <button v-for="item in quickEntries" :key="item.title" type="button" class="quick-card" @click="open(item.path)">
            <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
            <p>{{ item.description }}</p>
          </button>
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
  display: grid;
  gap: var(--space-2);
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

.list-item__title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.list-item__time {
  color: var(--color-text-soft);
  font-size: 0.78rem;
  white-space: nowrap;
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
}
</style>
