<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'
import type { FollowUpDashboardContext } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  context: FollowUpDashboardContext | null
  targetDate: string
  communicationUnread?: number
  contactStats?: {
    myMonitoring?: number
    contacted?: number
    due?: number
    overdue?: number
  }
}>()

const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(
  () => props.context?.employeeRealName || authStore.realName || '随访医生',
)

const stats = computed(() => props.context?.stats ?? {})
const contactStats = computed(() => props.contactStats ?? {})

const completionRate = computed(() => {
  const rate = stats.value.contactCompletionRate
  if (typeof rate === 'number') return Math.min(100, Math.max(0, rate))
  const total = contactStats.value.myMonitoring ?? stats.value.myMonitoringCount ?? 0
  const done = contactStats.value.contacted ?? stats.value.todayContacted ?? 0
  return total > 0 ? Math.round((done / total) * 100) : 100
})

const kpiCards = computed(() => [
  {
    label: '今日联系完成率',
    value: `${completionRate.value}%`,
    hint: `${contactStats.value.contacted ?? stats.value.todayContacted ?? 0} / ${contactStats.value.myMonitoring ?? stats.value.myMonitoringCount ?? 0} 人`,
    tone: completionRate.value >= 80 ? 'success' : completionRate.value >= 50 ? 'warning' : 'danger',
  },
  {
    label: '今日排班随访',
    value: stats.value.nextFollowUpToday ?? 0,
    hint: '排班联系或计划访谈在今日',
    tone: (stats.value.nextFollowUpToday ?? 0) > 0 ? 'warning' : 'neutral',
  },
  {
    label: '7 日内随访',
    value: stats.value.nextFollowUpThisWeek ?? 0,
    hint: '含今日在内的近期安排',
    tone: 'info',
  },
  {
    label: '待联系',
    value: contactStats.value.due ?? stats.value.contactDue ?? 0,
    hint: '超过 1 天未联系',
    tone: (contactStats.value.due ?? stats.value.contactDue ?? 0) > 0 ? 'warning' : 'neutral',
  },
  {
    label: '随访期限逾期',
    value: contactStats.value.overdue ?? stats.value.contactOverdue ?? 0,
    hint: '超出 6 个月随访期',
    tone: (contactStats.value.overdue ?? stats.value.contactOverdue ?? 0) > 0 ? 'danger' : 'neutral',
  },
  {
    label: '待排班',
    value: stats.value.noNextFollowUp ?? 0,
    hint: '尚无下次随访日期',
    tone: (stats.value.noNextFollowUp ?? 0) > 0 ? 'info' : 'neutral',
  },
])
</script>

<template>
  <aside class="follow-up-sidebar">
    <GlassCard class="follow-up-sidebar__card">
      <div class="follow-up-sidebar__avatar">{{ displayName.slice(0, 1) }}</div>
      <h3 class="follow-up-sidebar__name">{{ displayName }}</h3>
      <p class="follow-up-sidebar__dept">
        {{ context?.departmentName ?? '未绑定科室' }}
      </p>
      <StatusTag v-if="context?.adminAllAccess" tone="ai">管理员视图</StatusTag>
      <StatusTag v-else tone="primary">随访医生</StatusTag>

      <div class="follow-up-sidebar__divider" />

      <p class="follow-up-sidebar__label">工作台日期</p>
      <strong class="follow-up-sidebar__date">{{ targetDate }}</strong>

      <div class="follow-up-sidebar__progress">
        <div class="follow-up-sidebar__progress-head">
          <span>今日联系 KPI</span>
          <strong>{{ completionRate }}%</strong>
        </div>
        <div class="follow-up-sidebar__progress-bar" role="progressbar" :aria-valuenow="completionRate">
          <i :style="{ width: `${completionRate}%` }" />
        </div>
      </div>

      <div class="follow-up-sidebar__kpi-grid">
        <div
          v-for="card in kpiCards.slice(1)"
          :key="card.label"
          class="follow-up-sidebar__kpi"
          :class="`follow-up-sidebar__kpi--${card.tone}`"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </div>

      <div class="follow-up-sidebar__stats">
        <div>
          <span>在管患者</span>
          <strong>{{ stats.enrolledPatients ?? stats.totalPatients ?? 0 }}</strong>
        </div>
        <div>
          <span>我的监视</span>
          <strong>{{ contactStats?.myMonitoring ?? stats.myMonitoringCount ?? 0 }}</strong>
        </div>
        <div>
          <span>今日待访谈</span>
          <strong>{{ stats.todayInterviewsPlanned ?? 0 }}</strong>
        </div>
      </div>

      <div class="follow-up-sidebar__actions">
        <ElButton type="primary" plain @click="router.push('/follow-up/outcome')">
          疗效评估
        </ElButton>
        <ElButton plain @click="router.push('/follow-up/communication')">
          医患沟通
          <span v-if="(communicationUnread ?? 0) > 0" class="follow-up-sidebar__badge">{{ communicationUnread }}</span>
        </ElButton>
      </div>
    </GlassCard>
  </aside>
</template>

<style scoped>
.follow-up-sidebar {
  position: sticky;
  top: var(--space-3);
  align-self: start;
}

.follow-up-sidebar__card {
  padding: var(--space-4);
}

.follow-up-sidebar__avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  color: #fff;
  background: var(--gradient-primary);
  font-size: 22px;
  font-weight: 800;
}

.follow-up-sidebar__name {
  margin: var(--space-3) 0 var(--space-1);
  font-size: 18px;
}

.follow-up-sidebar__dept {
  margin: 0 0 var(--space-2);
  color: var(--color-text-muted);
  font-size: 13px;
}

.follow-up-sidebar__divider {
  height: 1px;
  margin: var(--space-4) 0;
  background: var(--color-border);
}

.follow-up-sidebar__label {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.follow-up-sidebar__date {
  display: block;
  margin-block: var(--space-1) var(--space-3);
  font-size: 20px;
}

.follow-up-sidebar__progress {
  margin-block-end: var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.follow-up-sidebar__progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-block-end: var(--space-2);
  font-size: 13px;
}

.follow-up-sidebar__progress-head strong {
  font-size: 18px;
  color: var(--color-primary-strong);
}

.follow-up-sidebar__progress-bar {
  height: 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-border) 80%, transparent);
  overflow: hidden;
}

.follow-up-sidebar__progress-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1f8cff, #22c55e);
  transition: width 0.25s ease;
}

.follow-up-sidebar__kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.follow-up-sidebar__kpi {
  display: grid;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 12px;
}

.follow-up-sidebar__kpi strong {
  font-size: 20px;
  line-height: 1.1;
}

.follow-up-sidebar__kpi small {
  color: var(--color-text-soft);
  font-size: 10px;
  line-height: 1.35;
}

.follow-up-sidebar__kpi--warning strong {
  color: #b45309;
}

.follow-up-sidebar__kpi--danger strong {
  color: #c81e2d;
}

.follow-up-sidebar__kpi--info strong {
  color: var(--color-primary-strong);
}

.follow-up-sidebar__kpi--success strong {
  color: #15803d;
}

.follow-up-sidebar__stats {
  display: grid;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.follow-up-sidebar__stats div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  font-size: 13px;
}

.follow-up-sidebar__stats strong {
  font-size: 18px;
}

.follow-up-sidebar__actions {
  display: grid;
  gap: var(--space-2);
}

.follow-up-sidebar__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-inline-start: 6px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger, #e74c3c);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}
</style>
