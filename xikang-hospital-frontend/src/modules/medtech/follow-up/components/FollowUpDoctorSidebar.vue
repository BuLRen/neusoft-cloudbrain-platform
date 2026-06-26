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
}>()

const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(
  () => props.context?.employeeRealName || authStore.realName || '随访医生',
)

const stats = computed(() => props.context?.stats ?? {})
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

      <div class="follow-up-sidebar__stats">
        <div>
          <span>在管患者</span>
          <strong>{{ stats.totalPatients ?? 0 }}</strong>
        </div>
        <div>
          <span>今日待访谈</span>
          <strong>{{ stats.todayInterviewsPlanned ?? 0 }}</strong>
        </div>
        <div>
          <span>今日待观察</span>
          <strong>{{ stats.todayObservationPending ?? 0 }}</strong>
        </div>
      </div>

      <div class="follow-up-sidebar__actions">
        <ElButton type="primary" plain @click="router.push('/follow-up/outcome')">
          疗效评估
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
</style>
