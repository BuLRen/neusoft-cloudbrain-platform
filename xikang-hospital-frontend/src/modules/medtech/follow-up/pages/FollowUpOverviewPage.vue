<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElTag } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { beijingTodayYmd, formatYmdWeekday } from '@/shared/utils/beijingDate'
import type { FollowUpDashboardContext } from '@/shared/types/medtechFollowUp'

const router = useRouter()
const loading = ref(false)
const context = ref<FollowUpDashboardContext | null>(null)
const communicationUnread = ref(0)

const todayYmd = beijingTodayYmd()
const todayLabel = formatYmdWeekday(todayYmd)

const stats = computed(() => context.value?.stats ?? {})

const completionRate = computed(() => {
  const rate = stats.value.contactCompletionRate
  if (typeof rate === 'number') return Math.min(100, Math.max(0, rate))
  const total = stats.value.myMonitoringCount ?? 0
  const done = stats.value.todayContacted ?? 0
  return total > 0 ? Math.round((done / total) * 100) : 100
})

const summaryCards = computed(() => [
  {
    label: '今日随访患者',
    value: stats.value.nextFollowUpToday ?? 0,
    hint: '排班联系或计划访谈在今日',
    tone: 'primary',
  },
  {
    label: '今日已联系',
    value: stats.value.todayContacted ?? 0,
    hint: `我的监视 ${stats.value.myMonitoringCount ?? 0} 人`,
    tone: 'success',
  },
  {
    label: '今日待联系',
    value: stats.value.todayContactDue ?? stats.value.contactDue ?? 0,
    hint: '超过联系周期未跟进',
    tone: (stats.value.todayContactDue ?? stats.value.contactDue ?? 0) > 0 ? 'warning' : 'neutral',
  },
  {
    label: '科室在管患者',
    value: stats.value.enrolledPatients ?? stats.value.totalPatients ?? 0,
    hint: `可见患者池 ${stats.value.totalPatients ?? 0} 人`,
    tone: 'info',
  },
  {
    label: '今日计划访谈',
    value: stats.value.todayInterviewsPlanned ?? 0,
    hint: '科室今日访谈安排',
    tone: 'info',
  },
  {
    label: '今日待观察',
    value: stats.value.todayObservationPending ?? 0,
    hint: '尚未完成健康观察确认',
    tone: (stats.value.todayObservationPending ?? 0) > 0 ? 'warning' : 'neutral',
  },
  {
    label: '7 日内随访',
    value: stats.value.nextFollowUpThisWeek ?? 0,
    hint: '含今日在内的近期安排',
    tone: 'primary',
  },
  {
    label: '随访期限逾期',
    value: stats.value.contactOverdue ?? 0,
    hint: '超出随访期限需优先处理',
    tone: (stats.value.contactOverdue ?? 0) > 0 ? 'danger' : 'neutral',
  },
])

const moduleLinks = [
  {
    title: '工作台',
    description: '监视患者、今日联系任务、科室患者池与排班日历。',
    path: '/follow-up/dashboard',
    tone: 'primary' as const,
  },
  {
    title: '疗效评估',
    description: '查看指标趋势、确认观察并编辑随访报告。',
    path: '/follow-up/outcome',
    tone: 'ai' as const,
  },
  {
    title: '医患沟通',
    description: '会话消息、病例总结与复诊提醒。',
    path: '/follow-up/communication',
    tone: 'primary' as const,
    badge: () => communicationUnread.value,
  },
  {
    title: '随访记录',
    description: '按患者查看历史事件与时间线。',
    path: '/follow-up/records',
    tone: 'neutral' as const,
  },
]

async function loadOverview() {
  loading.value = true
  try {
    const [ctx, unread] = await Promise.all([
      medtechFollowUpApi.getDashboardContext({ date: todayYmd }),
      medtechFollowUpApi.getDoctorCommunicationUnreadSummary().catch(() => ({ totalUnread: 0 })),
    ])
    context.value = ctx
    communicationUnread.value = unread.totalUnread ?? 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadOverview()
})

onActivated(() => {
  void loadOverview()
})
</script>

<template>
  <div class="follow-up-overview u-page-grid" v-loading="loading">
    <PageHeader
      title="随访仪表盘"
      description="科室随访总体数据一览，掌握今日患者联系与近期安排后再进入随访系统各功能页面。"
      eyebrow="仪表盘"
    >
      <template #actions>
        <ElTag type="info" effect="plain">{{ todayLabel }}</ElTag>
        <ElButton type="primary" @click="router.push('/follow-up/dashboard')">进入工作台</ElButton>
        <ElButton @click="loadOverview">刷新</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="follow-up-overview__welcome">
      <div class="follow-up-overview__welcome-main">
        <div class="follow-up-overview__avatar">
          {{ (context?.employeeRealName ?? '医').slice(0, 1) }}
        </div>
        <div>
          <p class="follow-up-overview__eyebrow">{{ context?.departmentName ?? '未绑定科室' }}</p>
          <h2>{{ context?.employeeRealName ?? '随访医生' }}，欢迎回来</h2>
          <p>统计日期 {{ context?.targetDate ?? todayYmd }} · 联系完成率 {{ completionRate }}%</p>
        </div>
      </div>
      <div class="follow-up-overview__welcome-kpi">
        <div>
          <span>我的监视</span>
          <strong>{{ stats.myMonitoringCount ?? 0 }}</strong>
        </div>
        <div>
          <span>待排班</span>
          <strong>{{ stats.noNextFollowUp ?? 0 }}</strong>
        </div>
        <StatusTag v-if="context?.adminAllAccess" tone="ai">管理员视图</StatusTag>
        <StatusTag v-else tone="primary">随访医生</StatusTag>
      </div>
    </GlassCard>

    <section class="follow-up-overview__stats" aria-label="总体数据">
      <GlassCard
        v-for="card in summaryCards"
        :key="card.label"
        class="follow-up-overview__stat"
        :class="`follow-up-overview__stat--${card.tone}`"
      >
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.hint }}</small>
      </GlassCard>
    </section>

    <section class="follow-up-overview__modules">
      <GlassCard
        v-for="item in moduleLinks"
        :key="item.path"
        class="follow-up-overview__module"
        @click="router.push(item.path)"
      >
        <div class="follow-up-overview__module-head">
          <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
          <span v-if="item.badge?.()" class="follow-up-overview__badge">{{ item.badge() }}</span>
        </div>
        <h3>{{ item.title }}</h3>
        <p>{{ item.description }}</p>
      </GlassCard>
    </section>
  </div>
</template>

<style scoped>
.follow-up-overview__welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5);
}

.follow-up-overview__welcome-main {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.follow-up-overview__avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 20px;
  color: #fff;
  background: var(--gradient-primary);
  font-size: 24px;
  font-weight: 800;
}

.follow-up-overview__eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--color-text-soft);
  font-size: 13px;
}

.follow-up-overview__welcome-main h2 {
  margin: 0 0 var(--space-1);
  font-size: 24px;
}

.follow-up-overview__welcome-main p {
  margin: 0;
  color: var(--color-text-muted);
}

.follow-up-overview__welcome-kpi {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.follow-up-overview__welcome-kpi div {
  display: grid;
  gap: 2px;
  min-width: 88px;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  text-align: center;
}

.follow-up-overview__welcome-kpi span {
  color: var(--color-text-soft);
  font-size: 12px;
}

.follow-up-overview__welcome-kpi strong {
  font-size: 24px;
}

.follow-up-overview__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.follow-up-overview__stat {
  display: grid;
  gap: 4px;
  padding: var(--space-4);
}

.follow-up-overview__stat span {
  color: var(--color-text-soft);
  font-size: 13px;
}

.follow-up-overview__stat strong {
  font-size: 32px;
  line-height: 1.1;
}

.follow-up-overview__stat small {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.follow-up-overview__stat--success strong {
  color: #15803d;
}

.follow-up-overview__stat--warning strong {
  color: #b45309;
}

.follow-up-overview__stat--danger strong {
  color: #c81e2d;
}

.follow-up-overview__stat--primary strong,
.follow-up-overview__stat--info strong {
  color: var(--color-primary-strong);
}

.follow-up-overview__modules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.follow-up-overview__module {
  min-height: 148px;
  padding: var(--space-5);
  cursor: pointer;
  transition:
    transform var(--duration-base) var(--ease-standard),
    box-shadow var(--duration-base) var(--ease-standard);
}

.follow-up-overview__module:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
}

.follow-up-overview__module-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.follow-up-overview__module h3 {
  margin-block: var(--space-3) var(--space-2);
  font-size: 20px;
}

.follow-up-overview__module p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.follow-up-overview__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-danger, #e74c3c);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .follow-up-overview__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .follow-up-overview__welcome {
    flex-direction: column;
    align-items: flex-start;
  }

  .follow-up-overview__stats,
  .follow-up-overview__modules {
    grid-template-columns: 1fr;
  }
}
</style>
