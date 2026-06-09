<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElTable, ElTableColumn } from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import {
  adminAlerts,
  adminDepartmentWorkload,
  adminKpiCards,
  adminQuickEntries,
  adminTodos,
  adminTrend,
} from '@/shared/mock/admin'

const authStore = useAuthStore()
const router = useRouter()

const maxRegistrations = computed(() => Math.max(...adminTrend.map((item) => item.registrations), 1))
const maxCharges = computed(() => Math.max(...adminTrend.map((item) => item.charges), 1))

function priorityTone(priority: 'high' | 'medium' | 'low') {
  if (priority === 'high') return 'danger'
  if (priority === 'medium') return 'warning'
  return 'primary'
}

function alertTone(level: 'critical' | 'warning' | 'info') {
  if (level === 'critical') return 'danger'
  if (level === 'warning') return 'warning'
  return 'primary'
}

function open(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="admin-dashboard u-page-grid">
    <PageHeader
      title="管理员运营仪表盘"
      description="聚合挂号、分诊、排班、收费与告警信息，帮助管理员完成日常治理与运营决策。"
      eyebrow="Role Admin / Overview"
    >
      <template #actions>
        <ElButton @click="open('/admin/monitoring')">查看运营监控</ElButton>
        <ElButton type="primary" @click="open('/admin/reports')">查看统计报表</ElButton>
      </template>
    </PageHeader>

    <section class="kpi-grid">
      <GlassCard v-for="card in adminKpiCards" :key="card.title" class="kpi-card">
        <StatusTag :tone="card.tone">{{ card.title }}</StatusTag>
        <strong>{{ card.value }}</strong>
        <span>{{ card.trend }}</span>
      </GlassCard>
    </section>

    <section class="content-grid">
      <GlassCard class="panel panel--trend">
        <div class="panel__header">
          <div>
            <h3>本周业务趋势</h3>
            <p>按日查看挂号量、收费额和待确认分诊积压。</p>
          </div>
          <StatusTag tone="primary">近 5 天</StatusTag>
        </div>
        <div class="trend-list">
          <div v-for="item in adminTrend" :key="item.label" class="trend-row">
            <span class="trend-row__label">{{ item.label }}</span>
            <div class="trend-row__bars">
              <div class="bar-group">
                <span>挂号 {{ item.registrations }}</span>
                <div class="bar-track"><div class="bar-fill bar-fill--primary" :style="{ width: `${(item.registrations / maxRegistrations) * 100}%` }" /></div>
              </div>
              <div class="bar-group">
                <span>收费 {{ item.charges }}</span>
                <div class="bar-track"><div class="bar-fill bar-fill--success" :style="{ width: `${(item.charges / maxCharges) * 100}%` }" /></div>
              </div>
              <div class="trend-inline">
                <StatusTag :tone="item.triagePending >= 15 ? 'warning' : 'success'">待分诊 {{ item.triagePending }}</StatusTag>
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
          <button v-for="item in adminQuickEntries" :key="item.title" type="button" class="quick-card" @click="open(item.path)">
            <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
            <p>{{ item.description }}</p>
          </button>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--table">
        <div class="panel__header">
          <div>
            <h3>科室工作量概览</h3>
            <p>面向管理员展示主要科室的挂号、接诊、检查与处方情况。</p>
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

      <GlassCard class="panel panel--todo">
        <div class="panel__header">
          <div>
            <h3>今日待办</h3>
            <p>{{ authStore.realName || '管理员' }} 当前需要优先关注的任务。</p>
          </div>
        </div>
        <div class="list-stack">
          <div v-for="item in adminTodos" :key="item.id" class="list-item">
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.owner }} · {{ item.dueLabel }}</p>
            </div>
            <StatusTag :tone="priorityTone(item.priority)">{{ item.priority === 'high' ? '高优先级' : item.priority === 'medium' ? '中优先级' : '低优先级' }}</StatusTag>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="panel panel--alert">
        <div class="panel__header">
          <div>
            <h3>重点告警</h3>
            <p>从跨模块视角快速发现异常，进入处理闭环。</p>
          </div>
          <ElButton link type="primary" @click="open('/admin/monitoring')">进入监控</ElButton>
        </div>
        <div class="list-stack">
          <div v-for="item in adminAlerts" :key="item.id" class="list-item list-item--column">
            <div class="panel__header">
              <strong>{{ item.title }}</strong>
              <StatusTag :tone="alertTone(item.level)">{{ item.source }}</StatusTag>
            </div>
            <p>{{ item.summary }}</p>
          </div>
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
}

.kpi-card strong {
  font-size: 28px;
  letter-spacing: -0.04em;
}

.kpi-card span {
  color: var(--color-text-muted);
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
}

.panel--trend,
.panel--table {
  grid-column: 1;
}

.panel--quick,
.panel--todo,
.panel--alert {
  grid-column: 2;
}

.trend-list,
.list-stack {
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
}

.trend-row__bars,
.bar-group {
  display: grid;
  gap: var(--space-2);
}

.bar-group span {
  font-size: 13px;
  color: var(--color-text-muted);
}

.bar-track {
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: inherit;
}

.bar-fill--primary {
  background: var(--color-primary);
}

.bar-fill--success {
  background: var(--color-success);
}

.trend-inline {
  margin-block-start: var(--space-1);
}

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
}

.quick-card p {
  color: var(--color-text-muted);
  line-height: 1.7;
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

@media (max-width: 1200px) {
  .kpi-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .panel--trend,
  .panel--table,
  .panel--quick,
  .panel--todo,
  .panel--alert {
    grid-column: auto;
  }
}
</style>
