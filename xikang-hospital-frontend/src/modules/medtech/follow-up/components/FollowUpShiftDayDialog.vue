<script setup lang="ts">
import { ElButton, ElDialog, ElEmpty } from 'element-plus'
import type { FollowUpDashboardPatient, FollowUpShiftContactTask } from '@/shared/types/medtechFollowUp'

defineProps<{
  date: string
  tasks?: FollowUpShiftContactTask[]
}>()

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  openPatient: [patient: FollowUpDashboardPatient]
}>()

function openByRegisterId(registerId: number, name?: string) {
  emit('openPatient', { registerId, realName: name } as FollowUpDashboardPatient)
}
</script>

<template>
  <ElDialog v-model="visible" :title="`${date} 联系任务`" width="480px">
    <div v-if="tasks?.length" class="shift-day-dialog__list">
      <div v-for="task in tasks" :key="task.id ?? task.registerId" class="shift-day-dialog__item">
        <div>
          <strong>{{ task.patientName ?? `患者 #${task.registerId}` }}</strong>
          <p>{{ task.caseNumber ?? task.registerId }} · {{ task.priorityLevel ?? 'normal' }}</p>
        </div>
        <ElButton size="small" @click="openByRegisterId(task.registerId, task.patientName)">
          进入评估
        </ElButton>
      </div>
    </div>
    <ElEmpty v-else description="当日无排班联系任务" />
  </ElDialog>
</template>

<style scoped>
.shift-day-dialog__list {
  display: grid;
  gap: var(--space-2);
}

.shift-day-dialog__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.shift-day-dialog__item p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
