<script setup lang="ts">
import StatusTag from '@/shared/components/StatusTag.vue'
import { FOLLOW_UP_PRIORITY_LABELS } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpDashboardPatient } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    patient: FollowUpDashboardPatient
    observed?: boolean
    draggable?: boolean
    compact?: boolean
  }>(),
  { observed: false, draggable: true, compact: false },
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
      { 'follow-up-patient-card--observed': observed, 'follow-up-patient-card--compact': compact },
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
    <span v-if="patient.lastTrackedDate" class="follow-up-patient-card__track">
      最近跟踪：{{ patient.lastTrackedDate }}
    </span>
    <span v-if="observed" class="follow-up-patient-card__badge">今日已观察</span>
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

.follow-up-patient-card__track {
  margin-block-start: var(--space-1);
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
