<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElEmpty, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import FollowUpPatientCard from '@/modules/medtech/follow-up/components/FollowUpPatientCard.vue'
import { FOLLOW_UP_PRIORITY_LABELS } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpDashboardPatient, FollowUpPriorityLevel } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  patients: FollowUpDashboardPatient[]
  schedulingId?: number | null
}>()

const emit = defineEmits<{
  open: [patient: FollowUpDashboardPatient]
  scheduleToday: [patient: FollowUpDashboardPatient]
}>()

const keyword = ref('')
const priorityFilter = ref<'all' | FollowUpPriorityLevel>('all')
const statusFilter = ref<'all' | 'interview' | 'observation' | 'observed'>('all')

const filteredPatients = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return props.patients.filter((patient) => {
    if (priorityFilter.value !== 'all' && patient.priorityLevel !== priorityFilter.value) {
      return false
    }
    if (statusFilter.value === 'interview' && !patient.interviewScheduledToday) return false
    if (statusFilter.value === 'observation' && !patient.observationDueToday) return false
    if (statusFilter.value === 'observed' && !patient.observedToday) return false
    if (!q) return true
    const haystack = [
      patient.realName,
      patient.caseNumber,
      String(patient.registerId),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return haystack.includes(q)
  })
})

const statusSummary = computed(() => ({
  total: props.patients.length,
  interview: props.patients.filter((p) => p.interviewScheduledToday).length,
  observation: props.patients.filter((p) => p.observationDueToday).length,
  observed: props.patients.filter((p) => p.observedToday).length,
}))
</script>

<template>
  <div class="all-patients-pool">
    <div class="all-patients-pool__toolbar">
      <ElInput
        v-model="keyword"
        class="all-patients-pool__search"
        clearable
        placeholder="搜索姓名 / 病历号"
      />
      <ElSelect v-model="priorityFilter" class="all-patients-pool__filter">
        <ElOption label="全部优先级" value="all" />
        <ElOption
          v-for="(label, key) in FOLLOW_UP_PRIORITY_LABELS"
          :key="key"
          :label="label"
          :value="key"
        />
      </ElSelect>
      <ElSelect v-model="statusFilter" class="all-patients-pool__filter">
        <ElOption label="全部状态" value="all" />
        <ElOption label="今日有访谈" value="interview" />
        <ElOption label="待观察" value="observation" />
        <ElOption label="已观察" value="observed" />
      </ElSelect>
    </div>

    <div class="all-patients-pool__summary">
      <ElTag effect="plain">在管 {{ statusSummary.total }}</ElTag>
      <ElTag type="warning" effect="plain">今日访谈 {{ statusSummary.interview }}</ElTag>
      <ElTag type="danger" effect="plain">待观察 {{ statusSummary.observation }}</ElTag>
      <ElTag type="success" effect="plain">已观察 {{ statusSummary.observed }}</ElTag>
    </div>

    <div v-if="filteredPatients.length" class="all-patients-pool__cards">
      <div
        v-for="patient in filteredPatients"
        :key="patient.registerId"
        class="all-patients-pool__item"
      >
        <FollowUpPatientCard
          :patient="patient"
          :observed="patient.observedToday"
          :dim-observed="false"
          compact
          show-status-row
          :draggable="false"
          @click="emit('open', patient)"
        />
        <div class="all-patients-pool__actions">
          <ElButton
            v-if="!patient.interviewScheduledToday"
            size="small"
            type="primary"
            plain
            :loading="schedulingId === patient.registerId"
            @click="emit('scheduleToday', patient)"
          >
            排今日访谈
          </ElButton>
          <ElButton size="small" @click="emit('open', patient)">疗效评估</ElButton>
        </div>
      </div>
    </div>
    <ElEmpty v-else description="没有匹配的在管患者" />
  </div>
</template>

<style scoped>
.all-patients-pool__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-end: var(--space-3);
}

.all-patients-pool__search {
  flex: 1 1 220px;
  min-width: 180px;
}

.all-patients-pool__filter {
  width: 150px;
}

.all-patients-pool__summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.all-patients-pool__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-3);
}

.all-patients-pool__item {
  display: grid;
  gap: var(--space-2);
}

.all-patients-pool__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-inline: var(--space-1);
}
</style>
