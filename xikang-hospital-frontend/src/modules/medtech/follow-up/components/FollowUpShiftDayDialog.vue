<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDialog, ElEmpty } from 'element-plus'
import type {
  FollowUpDashboardPatient,
  FollowUpMonitoredRosterItem,
  FollowUpShiftContactTask,
} from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  date: string
  tasks?: FollowUpShiftContactTask[]
  monitoredPatients?: FollowUpMonitoredRosterItem[]
}>()

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  openPatient: [patient: FollowUpDashboardPatient]
}>()

const taskRegisterIds = computed(() => new Set((props.tasks ?? []).map((task) => task.registerId)))

const otherMonitoredPatients = computed(() =>
  (props.monitoredPatients ?? []).filter((patient) => !taskRegisterIds.value.has(patient.registerId)),
)

function openByRegisterId(registerId: number, name?: string) {
  emit('openPatient', { registerId, realName: name } as FollowUpDashboardPatient)
}

function isContactDueOnDate(patient: FollowUpMonitoredRosterItem) {
  return patient.nextContactDate === props.date
}
</script>

<template>
  <ElDialog v-model="visible" :title="`${date} 工作安排`" width="520px">
    <section v-if="tasks?.length" class="shift-day-dialog__section">
      <h4>排班联系任务</h4>
      <div class="shift-day-dialog__list">
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
    </section>

    <section v-if="otherMonitoredPatients.length" class="shift-day-dialog__section">
      <h4>我监视的患者</h4>
      <div class="shift-day-dialog__list">
        <div
          v-for="patient in otherMonitoredPatients"
          :key="`monitor-${patient.registerId}`"
          class="shift-day-dialog__item"
        >
          <div>
            <strong>{{ patient.realName ?? `患者 #${patient.registerId}` }}</strong>
            <p>
              {{ patient.caseNumber ?? patient.registerId }} · {{ patient.priorityLevel ?? 'normal' }}
              <span v-if="patient.nextContactDate">
                · 下次联系 {{ patient.nextContactDate }}
                <template v-if="isContactDueOnDate(patient)">（今日）</template>
              </span>
              <span v-else> · 暂无排班联系</span>
            </p>
          </div>
          <ElButton size="small" @click="openByRegisterId(patient.registerId, patient.realName)">
            进入评估
          </ElButton>
        </div>
      </div>
    </section>

    <ElEmpty
      v-if="!tasks?.length && !otherMonitoredPatients.length"
      description="当日无工作安排"
    />
  </ElDialog>
</template>

<style scoped>
.shift-day-dialog__section + .shift-day-dialog__section {
  margin-block-start: var(--space-4);
}

.shift-day-dialog__section h4 {
  margin: 0 0 var(--space-2);
  font-size: 13px;
  color: var(--color-text-muted);
}

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
