<script setup lang="ts">
import { ElDialog, ElEmpty, ElTag } from 'element-plus'
import type { FollowUpStaffShift } from '@/shared/types/medtechFollowUp'

defineProps<{
  date: string
  shifts: FollowUpStaffShift[]
}>()

const visible = defineModel<boolean>({ default: false })

const shiftTypeLabel: Record<string, string> = {
  full: '全天',
  am: '上午',
  pm: '下午',
}
</script>

<template>
  <ElDialog v-model="visible" :title="`${date} 排班明细`" width="640px">
    <div v-if="shifts.length" class="shift-day-detail">
      <section v-for="shift in shifts" :key="shift.id" class="shift-day-detail__block">
        <header class="shift-day-detail__header">
          <div>
            <strong>{{ shift.employeeName ?? `员工 #${shift.employeeId}` }}</strong>
            <p>
              {{ shiftTypeLabel[shift.shiftType ?? 'full'] ?? shift.shiftType ?? '全天' }}
              · 联系 {{ shift.contactTasks?.length ?? 0 }} 人
              <template v-if="shift.status">
                · <ElTag size="small" effect="plain">{{ shift.status }}</ElTag>
              </template>
            </p>
          </div>
        </header>
        <ul v-if="shift.contactTasks?.length" class="shift-day-detail__tasks">
          <li v-for="task in shift.contactTasks" :key="task.id ?? task.registerId">
            <span>{{ task.patientName ?? `患者 #${task.registerId}` }}</span>
            <span class="shift-day-detail__meta">
              {{ task.caseNumber ?? task.registerId }}
              · {{ task.priorityLevel ?? 'normal' }}
            </span>
          </li>
        </ul>
        <p v-else class="shift-day-detail__empty">当日暂无联系任务</p>
      </section>
    </div>
    <ElEmpty v-else description="当日无排班" />
  </ElDialog>
</template>

<style scoped>
.shift-day-detail {
  display: grid;
  gap: var(--space-4);
  max-height: 60vh;
  overflow: auto;
}

.shift-day-detail__block {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.shift-day-detail__header strong {
  font-size: 15px;
}

.shift-day-detail__header p {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.shift-day-detail__tasks {
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.shift-day-detail__tasks li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-strong);
}

.shift-day-detail__meta {
  color: var(--color-text-soft);
  font-size: 12px;
}

.shift-day-detail__empty {
  margin: var(--space-2) 0 0;
  color: var(--color-text-soft);
  font-size: 13px;
}
</style>
