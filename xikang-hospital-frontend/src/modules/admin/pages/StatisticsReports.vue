<script setup lang="ts">
import { computed, reactive } from 'vue'
import { ElButton, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { adminDepartmentWorkload, reportRankings, reportSummaryCards, reportTrend } from '@/shared/mock/admin'

const filter = reactive({
  period: 'week',
  department: 'all',
})

const maxChargeAmount = computed(() => Math.max(...reportTrend.map((item) => item.chargeAmount), 1))
const filteredRanking = computed(() => filter.department === 'all'
  ? reportRankings
  : reportRankings.filter((item) => item.name.includes(filter.department)))
</script>

<template>
  <div class="statistics-reports-page u-page-grid">
    <PageHeader
      title="统计报表"
      description="从挂号、收费、AI 分诊和科室业务量角度展示面向管理员的经营分析与趋势报表。"
      eyebrow="Role Admin / Reports"
    >
      <template #actions>
        <ElButton>导出报表</ElButton>
        <ElButton type="primary">生成周报</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="filter-card">
      <div class="filter-row">
        <ElSelect v-model="filter.period" class="field field--small">
          <ElOption label="本周" value="week" />
          <ElOption label="本月" value="month" />
        </ElSelect>
        <ElSelect v-model="filter.department" class="field field--small">
          <ElOption label="全部科室" value="all" />
          <ElOption label="内科" value="内科" />
          <ElOption label="骨科" value="骨科" />
          <ElOption label="儿科" value="儿科" />
        </ElSelect>
        <StatusTag tone="ai">前端分析结构已就绪</StatusTag>
      </div>
    </GlassCard>

    <section class="summary-grid">
      <GlassCard v-for="item in reportSummaryCards" :key="item.title" class="summary-card">
        <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
        <strong>{{ item.value }}</strong>
        <span>{{ item.compare }}</span>
      </GlassCard>
    </section>

    <section class="content-grid">
      <GlassCard class="panel">
        <div class="panel__header">
          <div>
            <h3>收费趋势</h3>
            <p>展示当前周期内收费额与 AI 分诊使用率变化。</p>
          </div>
        </div>
        <div class="trend-list">
          <div v-for="item in reportTrend" :key="item.label" class="trend-row">
            <span class="trend-label">{{ item.label }}</span>
            <div class="trend-main">
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: `${(item.chargeAmount / maxChargeAmount) * 100}%` }" />
              </div>
              <div class="trend-meta">
                <span>收费 {{ item.chargeAmount }}</span>
                <StatusTag :tone="item.triageUsage >= 66 ? 'ai' : 'primary'">AI 使用率 {{ item.triageUsage }}%</StatusTag>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="panel">
        <div class="panel__header">
          <div>
            <h3>业务排行</h3>
            <p>用于快速识别当前运营高峰科室与业务单元。</p>
          </div>
        </div>
        <div class="ranking-list">
          <div v-for="item in filteredRanking" :key="item.rank + item.name" class="ranking-item">
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
            <p>支持后续扩展为更完整的统计图表与导出能力。</p>
          </div>
        </div>
        <ElTable :data="adminDepartmentWorkload">
          <ElTableColumn prop="departmentName" label="科室" min-width="120" />
          <ElTableColumn prop="registrations" label="挂号量" min-width="100" />
          <ElTableColumn prop="visits" label="接诊量" min-width="100" />
          <ElTableColumn prop="inspections" label="检查量" min-width="100" />
          <ElTableColumn prop="prescriptions" label="处方量" min-width="100" />
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
}

.summary-card span,
.panel__header p,
.ranking-item p {
  color: var(--color-text-muted);
  line-height: 1.7;
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
  grid-template-columns: 48px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: center;
}

.trend-label {
  color: var(--color-text-muted);
  font-weight: 600;
}

.trend-main {
  display: grid;
  gap: var(--space-2);
}

.bar-track {
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--color-primary);
}

.ranking-item {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
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
