<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import type {
  DepartmentWorkloadItem,
  DailyTrendPoint,
} from '@/shared/api/modules/registration'

const loading = ref(false)
const workload = ref<DepartmentWorkloadItem[]>([])
const trend = ref<DailyTrendPoint[]>([])

const filter = reactive({
  period: 'week' as 'week' | 'month',
})

const periodDays = computed(() => (filter.period === 'week' ? 7 : 30))

const maxRegistrations = computed(() => Math.max(...trend.value.map((i) => i.registrations), 1))
const maxCharges = computed(() => Math.max(...trend.value.map((i) => Number(i.charges)), 1))

const summary = computed(() => {
  const totalReg = workload.value.reduce((s, i) => s + i.registrations, 0)
  const totalVisit = workload.value.reduce((s, i) => s + i.visits, 0)
  const totalInsp = workload.value.reduce((s, i) => s + i.inspections, 0)
  const totalRx = workload.value.reduce((s, i) => s + i.prescriptions, 0)
  const totalCharge = trend.value.reduce((s, i) => s + Number(i.charges), 0)
  return {
    totalReg,
    totalVisit,
    totalCharge,
    avgPerDept: workload.value.length ? Math.round(totalReg / workload.value.length) : 0,
  }
})

// 业务排行：按挂号量 Top 5
const ranking = computed(() =>
  [...workload.value]
    .sort((a, b) => b.registrations - a.registrations)
    .slice(0, 5)
    .map((item, idx) => ({
      rank: idx + 1,
      name: item.departmentName,
      value: `${item.registrations} 挂号 / ${item.visits} 接诊`,
      note: `检查 ${item.inspections} · 处方 ${item.prescriptions}`,
    })),
)

function formatMoney(v: number | string) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const [w, t] = await Promise.all([
      registrationApi.departmentWorkload(),
      registrationApi.dailyTrend(periodDays.value),
    ])
    workload.value = w
    trend.value = t
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="statistics-reports-page u-page-grid">
    <PageHeader
      title="统计报表"
      description="基于 register / medical_record / check_request / inspection_request / disposal_request / prescription / expense_record 真实数据聚合。"
      eyebrow="Role Admin / Reports"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="filter-card">
      <div class="filter-row">
        <ElSelect v-model="filter.period" class="field field--small" @change="load">
          <ElOption label="近 7 天" value="week" />
          <ElOption label="近 30 天" value="month" />
        </ElSelect>
        <StatusTag tone="primary">数据来自 registration-service 实时聚合</StatusTag>
      </div>
    </GlassCard>

    <section class="summary-grid" v-loading="loading">
      <GlassCard class="summary-card">
        <StatusTag tone="primary">总挂号量</StatusTag>
        <strong>{{ summary.totalReg }}</strong>
        <span>所有科室合计</span>
      </GlassCard>
      <GlassCard class="summary-card">
        <StatusTag tone="success">总接诊量</StatusTag>
        <strong>{{ summary.totalVisit }}</strong>
        <span>已写病历数</span>
      </GlassCard>
      <GlassCard class="summary-card">
        <StatusTag tone="warning">总收入</StatusTag>
        <strong>¥ {{ formatMoney(summary.totalCharge) }}</strong>
        <span>已缴费费用合计</span>
      </GlassCard>
      <GlassCard class="summary-card">
        <StatusTag tone="ai">科室均值</StatusTag>
        <strong>{{ summary.avgPerDept }}</strong>
        <span>每科室平均挂号量</span>
      </GlassCard>
    </section>

    <section class="content-grid">
      <GlassCard class="panel">
        <div class="panel__header">
          <div>
            <h3>每日趋势</h3>
            <p>展示每日挂号量与已缴费金额。</p>
          </div>
        </div>
        <div class="trend-list">
          <div v-for="item in trend" :key="item.label" class="trend-row">
            <span class="trend-label">{{ item.label }}</span>
            <div class="trend-main">
              <div class="bar-track">
                <div class="bar-fill bar-fill--primary" :style="{ width: `${(item.registrations / maxRegistrations) * 100}%` }" />
              </div>
              <div class="bar-track">
                <div class="bar-fill bar-fill--success" :style="{ width: `${(Number(item.charges) / maxCharges) * 100}%` }" />
              </div>
              <div class="trend-meta">
                <span>挂号 {{ item.registrations }}</span>
                <span>收费 ¥{{ formatMoney(item.charges) }}</span>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="panel">
        <div class="panel__header">
          <div>
            <h3>业务排行 Top 5</h3>
            <p>按挂号量排序的科室排行。</p>
          </div>
        </div>
        <div class="ranking-list">
          <div v-for="item in ranking" :key="item.rank + item.name" class="ranking-item">
            <strong>#{{ item.rank }}</strong>
            <div>
              <h4>{{ item.name }}</h4>
              <p>{{ item.note }}</p>
            </div>
            <StatusTag tone="primary">{{ item.value }}</StatusTag>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--wide">
        <div class="panel__header">
          <div>
            <h3>科室业务汇总</h3>
            <p>挂号量 / 接诊量 / 检查量 / 处方量，按挂号量降序。</p>
          </div>
        </div>
        <ElTable :data="workload">
          <ElTableColumn prop="departmentName" label="科室" min-width="120" />
          <ElTableColumn prop="registrations" label="挂号量" min-width="100" align="right" />
          <ElTableColumn prop="visits" label="接诊量" min-width="100" align="right" />
          <ElTableColumn prop="inspections" label="检查量" min-width="100" align="right" />
          <ElTableColumn prop="prescriptions" label="处方量" min-width="100" align="right" />
        </ElTable>
      </GlassCard>
    </section>
  </div>
</template>

<style scoped>
.filter-card,
.summary-card,
.panel {
  padding: var(--space-5);
}

.filter-row,
.panel__header,
.ranking-item,
.trend-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.summary-card {
  display: grid;
  gap: var(--space-3);
}

.summary-card strong {
  font-size: 28px;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.summary-card span,
.panel__header p,
.ranking-item p {
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 0.85rem;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.panel--wide {
  grid-column: 1 / -1;
}

.trend-list,
.ranking-list {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.trend-row {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: center;
}

.trend-label {
  color: var(--color-text-muted);
  font-weight: 600;
  font-size: 0.85rem;
}

.trend-main {
  display: grid;
  gap: var(--space-2);
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

.trend-meta {
  justify-content: space-between;
  margin-block-start: 2px;
}

.trend-meta span {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.ranking-item {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
}

.ranking-item strong {
  font-size: 1.1rem;
  color: var(--color-primary);
}

.ranking-item h4 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--color-text);
}

.field--small {
  width: 160px;
}

@media (max-width: 1200px) {
  .summary-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .panel--wide {
    grid-column: auto;
  }
}
</style>
