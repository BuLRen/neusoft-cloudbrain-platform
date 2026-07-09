<script setup lang="ts">
import StatusTag from '@/shared/components/StatusTag.vue'
import { FOLLOW_UP_PRIORITY_LABELS } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpDashboardPatient } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    patient: FollowUpDashboardPatient
    observed?: boolean
    /** 已观察时是否变灰（待访谈池应关闭） */
    dimObserved?: boolean
    draggable?: boolean
    compact?: boolean
    /** 展示今日访谈 / 观察状态行 */
    showStatusRow?: boolean
    /** 展示监视人与联系状态 */
    showContactInfo?: boolean
  }>(),
  { observed: false, dimObserved: true, draggable: true, compact: false, showStatusRow: false, showContactInfo: false },
)

const emit = defineEmits<{
  click: [patient: FollowUpDashboardPatient]
  dragstart: [patient: FollowUpDashboardPatient, event: DragEvent]
}>()

function priorityTone(priority?: string) {
  if (priority === 'critical') return 'danger' as const
  if (priority === 'high') return 'warning' as const
  return 'primary' as const
}

function contactStatusLabel(status?: string) {
  if (status === 'contacted_today') return '今日已联系'
  if (status === 'due') return '待联系'
  if (status === 'overdue') return '已逾期'
  return '正常'
}

function contactStatusClass(status?: string) {
  if (status === 'contacted_today') return 'follow-up-patient-card__chip--observed'
  if (status === 'overdue') return 'follow-up-patient-card__chip--overdue'
  if (status === 'due') return 'follow-up-patient-card__chip--pending'
  return 'follow-up-patient-card__chip--interview'
}

function nextFollowUpLabel(patient: FollowUpDashboardPatient) {
  const date = patient.nextFollowUpDate ?? patient.nextContactDate
  if (!date) return '待排班'
  const days = patient.daysUntilNextFollowUp ?? patient.daysUntilNextContact
  if (days === 0) return `今日随访 · ${date}`
  if (days != null && days < 0) return `已过期 ${Math.abs(days)} 天 · ${date}`
  if (days != null && days <= 7) return `${days} 天后 · ${date}`
  return `下次随访 ${date}`
}

function nextFollowUpClass(patient: FollowUpDashboardPatient) {
  const days = patient.daysUntilNextFollowUp ?? patient.daysUntilNextContact
  if (days == null) return 'follow-up-patient-card__followup--muted'
  if (days < 0) return 'follow-up-patient-card__followup--overdue'
  if (days === 0) return 'follow-up-patient-card__followup--today'
  if (days <= 7) return 'follow-up-patient-card__followup--soon'
  return 'follow-up-patient-card__followup--normal'
}

function nextFollowUpTypeLabel(type?: string) {
  if (type === 'contact') return '排班联系'
  if (type === 'interview') return '计划访谈'
  return '待排班'
}

function onDragStart(event: DragEvent) {
  if (!props.draggable) return
  event.dataTransfer?.setData('application/x-followup-register-id', String(props.patient.registerId))
  event.dataTransfer?.setData('text/plain', String(props.patient.registerId))
  event.dataTransfer!.effectAllowed = 'move'
  emit('dragstart', props.patient, event)
}
</script>

<template>
  <button
    type="button"
    class="follow-up-patient-card"
    :class="[
      `follow-up-patient-card--${patient.priorityLevel ?? 'normal'}`,
      {
        'follow-up-patient-card--observed': observed && dimObserved,
        'follow-up-patient-card--compact': compact,
      },
    ]"
    :draggable="draggable && !observed"
    @click="emit('click', patient)"
    @dragstart="onDragStart"
  >
    <div class="follow-up-patient-card__head">
      <strong>{{ patient.realName ?? '未知' }}</strong>
      <StatusTag :tone="priorityTone(patient.priorityLevel)">
        {{ FOLLOW_UP_PRIORITY_LABELS[patient.priorityLevel ?? 'normal'] }}
      </StatusTag>
    </div>
    <span class="follow-up-patient-card__meta">
      {{ patient.caseNumber ?? patient.registerId }} · {{ patient.gender ?? '—' }} · {{ patient.age ?? '—' }}岁
    </span>
    <div
      v-if="showContactInfo"
      class="follow-up-patient-card__followup"
      :class="nextFollowUpClass(patient)"
    >
      <span class="follow-up-patient-card__followup-type">{{ nextFollowUpTypeLabel(patient.nextFollowUpType) }}</span>
      <strong>{{ nextFollowUpLabel(patient) }}</strong>
    </div>
    <span v-if="patient.lastTrackedDate && !showStatusRow && !showContactInfo" class="follow-up-patient-card__track">
      最近跟踪：{{ patient.lastTrackedDate }}
    </span>
    <div v-if="showContactInfo" class="follow-up-patient-card__status-row">
      <span
        v-if="patient.contactStatus"
        class="follow-up-patient-card__chip"
        :class="contactStatusClass(patient.contactStatus)"
      >
        {{ contactStatusLabel(patient.contactStatus) }}
      </span>
      <span v-if="patient.monitoringEmployeeName && !patient.isMine" class="follow-up-patient-card__track follow-up-patient-card__track--inline">
        监视：{{ patient.monitoringEmployeeName }}
      </span>
      <span v-else-if="!patient.monitoringEmployeeId" class="follow-up-patient-card__track follow-up-patient-card__track--inline">
        未监视
      </span>
      <span
        v-if="patient.daysUntilDeadline != null"
        class="follow-up-patient-card__track follow-up-patient-card__track--inline"
      >
        {{ patient.daysUntilDeadline >= 0 ? `距期限 ${patient.daysUntilDeadline} 天` : `已超期 ${Math.abs(patient.daysUntilDeadline)} 天` }}
      </span>
    </div>
    <div v-if="showStatusRow" class="follow-up-patient-card__status-row">
      <span v-if="patient.interviewScheduledToday" class="follow-up-patient-card__chip follow-up-patient-card__chip--interview">
        今日访谈
      </span>
      <span
        v-if="patient.observedToday"
        class="follow-up-patient-card__chip follow-up-patient-card__chip--observed"
      >
        已观察
      </span>
      <span
        v-else-if="patient.observationDueToday"
        class="follow-up-patient-card__chip follow-up-patient-card__chip--pending"
      >
        待观察
      </span>
      <span v-if="patient.lastTrackedDate" class="follow-up-patient-card__track follow-up-patient-card__track--inline">
        最近：{{ patient.lastTrackedDate }}
      </span>
    </div>
    <span v-if="observed && !showStatusRow" class="follow-up-patient-card__badge">今日已观察</span>
  </button>
</template>

<style scoped>
.follow-up-patient-card {
  display: block;
  width: 100%;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface-strong);
  text-align: start;
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
}

.follow-up-patient-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(31, 140, 255, 0.08);
}

.follow-up-patient-card--normal {
  border-inline-start: 4px solid #1f8cff;
  background: color-mix(in srgb, #1f8cff 6%, var(--color-surface-strong));
}

.follow-up-patient-card--high {
  border-inline-start: 4px solid #f59f00;
  background: color-mix(in srgb, #f59f00 8%, var(--color-surface-strong));
}

.follow-up-patient-card--critical {
  border-inline-start: 4px solid #ef4d5a;
  background: color-mix(in srgb, #ef4d5a 8%, var(--color-surface-strong));
}

.follow-up-patient-card--observed {
  opacity: 0.62;
  filter: grayscale(0.35);
}

.follow-up-patient-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-block-end: var(--space-1);
}

.follow-up-patient-card__head strong {
  font-size: 15px;
}

.follow-up-patient-card__meta,
.follow-up-patient-card__track {
  display: block;
  color: var(--color-text-muted);
  font-size: 12px;
}

.follow-up-patient-card__followup {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-block-start: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.follow-up-patient-card__followup strong {
  font-size: 13px;
}

.follow-up-patient-card__followup-type {
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.02em;
  opacity: 0.85;
}

.follow-up-patient-card__followup--today {
  background: rgba(245, 159, 0, 0.12);
  color: #b45309;
}

.follow-up-patient-card__followup--soon {
  background: rgba(31, 140, 255, 0.1);
  color: var(--color-primary-strong);
}

.follow-up-patient-card__followup--overdue {
  background: rgba(239, 77, 90, 0.12);
  color: #c81e2d;
}

.follow-up-patient-card__followup--normal {
  background: var(--color-bg-soft);
  color: var(--color-text-muted);
}

.follow-up-patient-card__followup--muted {
  background: var(--color-bg-soft);
  color: var(--color-text-soft);
}

.follow-up-patient-card__track {
  margin-block-start: var(--space-1);
}

.follow-up-patient-card__status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-block-start: var(--space-2);
}

.follow-up-patient-card__chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 650;
}

.follow-up-patient-card__chip--interview {
  background: rgba(31, 140, 255, 0.14);
  color: var(--color-primary-strong);
}

.follow-up-patient-card__chip--observed {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.follow-up-patient-card__chip--pending {
  background: rgba(245, 159, 0, 0.14);
  color: #b45309;
}

.follow-up-patient-card__chip--overdue {
  background: rgba(239, 77, 90, 0.14);
  color: #c81e2d;
}

.follow-up-patient-card__track--inline {
  margin: 0;
  font-size: 10px;
}

.follow-up-patient-card__badge {
  display: inline-block;
  margin-block-start: var(--space-2);
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(95, 114, 136, 0.14);
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 650;
}

.follow-up-patient-card--compact .follow-up-patient-card__track {
  display: none;
}
</style>
