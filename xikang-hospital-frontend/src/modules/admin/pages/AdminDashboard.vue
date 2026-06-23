<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElTable, ElTableColumn } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type {
  DepartmentWorkloadItem,
  DailyTrendPoint,
  KpiSummary,
} from '@/shared/api/modules/registration'
import type { DrugOption } from '@/shared/types/pharmacy'

const router = useRouter()

const loading = ref(false)
const kpi = ref<KpiSummary | null>(null)
const trend = ref<DailyTrendPoint[]>([])
const workload = ref<DepartmentWorkloadItem[]>([])
const lowStock = ref<DrugOption[]>([])

const maxRegistrations = computed(() => Math.max(...trend.value.map((i) => i.registrations), 1))
const maxCharges = computed(() => Math.max(...trend.value.map((i) => Number(i.charges)), 1))

const kpiCards = computed(() => {
  const k = kpi.value
  return [
    { title: '在册科室', value: k?.departments ?? 0, tone: 'primary' as const, to: '/admin/master-data' },
    { title: '在册医生', value: k?.doctors ?? 0, tone: 'success' as const, to: '/admin/physicians' },
    { title: '药品目录', value: k?.drugs ?? 0, tone: 'warning' as const, to: '/admin/master-data' },
    { title: 'AI 导诊咨询', value: k?.aiTriageConsultations ?? 0, tone: 'ai' as const, to: '/admin/reports' },
  ]
})

const quickEntries = [
  { title: 'AI 分诊台', description: '处理 AI 分诊台和基础支撑动作。', path: '/admin/triage', tone: 'primary' as const },
  { title: '智能排班', description: '查看计划、确认调整、发布排班。', path: '/admin/schedule', tone: 'warning' as const },
  { title: '诊疗医生维护', description: '维护医生档案与登录账号。', path: '/admin/physicians', tone: 'success' as const },
  { title: '基础资料', description: '维护科室、挂号级别、药品与项目目录。', path: '/admin/master-data', tone: 'success' as const },
  { title: '统计报表', description: '查看真实经营分析与趋势。', path: '/admin/reports', tone: 'ai' as const },
]

const workloadTop = computed(() => [...workload.value].sort((a, b) => b.registrations - a.registrations).slice(0, 8))

function open(path: string) {
  router.push(path)
}

async function load() {
  loading.value = true
  try {
    const [k, t, w, ls] = await Promise.all([
      registrationApi.kpi(),
      registrationApi.dailyTrend(7),
      registrationApi.departmentWorkload(),
      pharmacyApi.lowStockDrugs(),
    ])
    kpi.value = k
    trend.value = t
    workload.value = w
    lowStock.value = ls
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-dashboard u-page-grid">
    <PageHeader
      title="管理员运营仪表盘"
      description="聚合挂号、分诊、排班、收费与库存的真实运营数据，辅助管理员日常治理与决策。"
      eyebrow="Role Admin / Overview"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
        <ElButton type="primary" @click="open('/admin/reports')">查看统计报表</ElButton>
      </template>
    </PageHeader>

    <section class="kpi-grid" v-loading="loading">
      <GlassCard v-for="card in kpiCards" :key="card.title" class="kpi-card" @click="open(card.to)">
        <StatusTag :tone="card.tone">{{ card.title }}</StatusTag>
        <strong>{{ card.value }}</strong>
        <span class="kpi-hint">点击查看详情</span>
      </GlassCard>
    </section>

    <section class="content-grid">
      <GlassCard class="panel panel--trend">
        <div class="panel__header">
          <div>
            <h3>近 7 天业务趋势</h3>
            <p>按日查看挂号量、收费额。</p>
          </div>
          <StatusTag tone="primary">真实数据</StatusTag>
        </div>
        <div class="trend-list">
          <div v-for="item in trend" :key="item.label" class="trend-row">
            <span class="trend-row__label">{{ item.label.slice(5) }}</span>
            <div class="trend-row__bars">
              <div class="bar-group">
                <span>挂号 {{ item.registrations }}</span>
                <div class="bar-track"><div class="bar-fill bar-fill--primary" :style="{ width: `${(item.registrations / maxRegistrations) * 100}%` }" /></div>
              </div>
              <div class="bar-group">
                <span>收费 ¥{{ Number(item.charges).toLocaleString('zh-CN') }}</span>
                <div class="bar-track"><div class="bar-fill bar-fill--success" :style="{ width: `${(Number(item.charges) / maxCharges) * 100}%` }" /></div>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--quick">
        <div class="panel__header">
          <div>
            <h3>快捷入口</h3>
            <p>从首页直接进入最常用的治理与支撑页面。</p>
          </div>
        </div>
        <div class="quick-grid">
          <button v-for="item in quickEntries" :key="item.title" type="button" class="quick-card" @click="open(item.path)">
            <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
            <p>{{ item.description }}</p>
          </button>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--table">
        <div class="panel__header">
          <div>
            <h3>科室工作量 Top 8</h3>
            <p>按挂号量排序的主要科室业务概览。</p>
          </div>
        </div>
        <ElTable :data="workloadTop">
          <ElTableColumn prop="departmentName" label="科室" min-width="120" />
          <ElTableColumn prop="registrations" label="挂号量" min-width="90" align="right" />
          <ElTableColumn prop="visits" label="接诊量" min-width="90" align="right" />
          <ElTableColumn prop="inspections" label="检查量" min-width="90" align="right" />
          <ElTableColumn prop="prescriptions" label="处方量" min-width="90" align="right" />
        </ElTable>
      </GlassCard>

      <GlassCard class="panel panel--alert">
        <div class="panel__header">
          <div>
            <h3>库存预警</h3>
            <p>来自 pharmacy-service · drug_stock 表的低库存药品。</p>
          </div>
          <ElButton link type="primary" @click="open('/admin/monitoring')">进入监控</ElButton>
        </div>
        <div v-if="lowStock.length > 0" class="list-stack">
          <div v-for="item in lowStock" :key="item.id" class="list-item list-item--column">
            <div class="panel__header">
              <strong>{{ item.name }}</strong>
              <StatusTag tone="danger">{{ item.stockQuantity ?? 0 }} {{ item.unit || '' }}</StatusTag>
            </div>
            <p>{{ item.manufacturer || '-' }} · 阈值 {{ item.lowStockThreshold ?? 0 }}</p>
          </div>
        </div>
        <div v-else class="empty-tip">
          <StatusTag tone="success">库存充足</StatusTag>
          <p>当前没有低库存药品。</p>
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
  transition: transform var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
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

.panel--trend,
.panel--table {
  grid-column: 1;
}

.panel--quick,
.panel--alert {
  grid-column: 2;
}

.trend-list,
.list-stack,
.quick-grid {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.trend-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
}

.trend-row__label {
  padding-block-start: 6px;
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 0.85rem;
}

.trend-row__bars,
.bar-group {
  display: grid;
  gap: var(--space-2);
}

.bar-group span {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.bar-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.4s ease;
}

.bar-fill--primary {
  background: var(--color-primary);
}

.bar-fill--success {
  background: var(--color-success);
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
  transition: border-color var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
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

  .panel--trend,
  .panel--table,
  .panel--quick,
  .panel--alert {
    grid-column: auto;
  }
}
</style>
