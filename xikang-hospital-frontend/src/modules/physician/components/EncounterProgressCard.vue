<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElIcon, ElSkeleton } from 'element-plus'
import {
  CircleCheck,
  Document,
  DocumentChecked,
  FirstAidKit,
  MagicStick,
  Tickets,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { clinicalRecordApi, type ClinicalTimelineEntry } from '@/shared/api/modules/clinicalRecord'
import { VISIT_STATE } from '../constants/visitState'

const WORKFLOW_STEPS = [
  { key: 'queue', label: '待诊接诊' },
  { key: 'record', label: '病历' },
  { key: 'orders', label: '申请' },
  { key: 'results', label: '结果' },
  { key: 'diagnosis', label: '确诊' },
  { key: 'prescription', label: '处方' },
] as const

const EVENT_STEP_INDEX: Record<string, number> = {
  visit_start: 0,
  triage: 0,
  pre_consultation: 0,
  medical_record: 1,
  preliminary_diagnosis: 1,
  check_order: 2,
  inspection_order: 2,
  disposal_order: 2,
  check_result: 3,
  inspection_result: 3,
  disposal_result: 3,
  diagnosis: 4,
  prescription: 5,
  visit_archived: 5,
}

const EVENT_ICON: Record<string, Component> = {
  visit_start: Tickets,
  triage: MagicStick,
  pre_consultation: MagicStick,
  medical_record: Document,
  preliminary_diagnosis: DocumentChecked,
  check_order: Document,
  inspection_order: Document,
  disposal_order: Document,
  check_result: FirstAidKit,
  inspection_result: FirstAidKit,
  disposal_result: FirstAidKit,
  diagnosis: DocumentChecked,
  prescription: Tickets,
  visit_archived: CircleCheck,
}

const props = defineProps<{
  registerId: number | null | undefined
  visitState: number
}>()

const loading = ref(false)
const timeline = ref<ClinicalTimelineEntry[]>([])
let loadSeq = 0

async function loadTimeline(registerId: number) {
  const seq = ++loadSeq
  loading.value = true
  try {
    const data = await clinicalRecordApi.physicianTimeline(registerId)
    if (seq !== loadSeq) return
    timeline.value = data.timeline || []
  } catch (error) {
    if (seq !== loadSeq) return
    console.warn('加载就诊时间线失败:', error)
    timeline.value = []
  } finally {
    if (seq === loadSeq) {
      loading.value = false
    }
  }
}

watch(
  () => props.registerId,
  (registerId) => {
    timeline.value = []
    if (!registerId) return
    void loadTimeline(registerId)
  },
  { immediate: true },
)

function stepIndexFromTimeline(events: ClinicalTimelineEntry[]): number {
  let max = 0
  for (const event of events) {
    const idx = EVENT_STEP_INDEX[event.eventType]
    if (idx != null && idx > max) max = idx
  }
  return max
}

function stepIndexFromVisitState(visitState: number): number {
  if (visitState === VISIT_STATE.REGISTERED) return 0
  if (visitState === VISIT_STATE.IN_PROGRESS) return 1
  if (visitState === VISIT_STATE.EXAM_PENDING) return 2
  if (visitState === VISIT_STATE.EXAM_COMPLETED) return 3
  return 0
}

const currentStepIndex = computed(() => {
  const fromTimeline = stepIndexFromTimeline(timeline.value)
  const fromState = stepIndexFromVisitState(props.visitState)
  return Math.max(fromTimeline, fromState)
})

const nextHint = computed(() => {
  const state = props.visitState
  if (state === VISIT_STATE.REGISTERED) return '建议进入病历与初步诊断'
  if (state === VISIT_STATE.IN_PROGRESS) {
    if (currentStepIndex.value >= 2) return '建议继续开立检查检验或查看已有进度'
    return '建议继续填写病历与初步诊断'
  }
  if (state === VISIT_STATE.EXAM_PENDING) return '检查检验进行中，完成后可查看结果'
  if (state === VISIT_STATE.EXAM_COMPLETED) return '结果已出，建议继续确诊开方'
  return '点击「进入流程」继续本次诊疗'
})

const recentEvents = computed(() => {
  const events = [...timeline.value]
  return events.slice(-3).reverse()
})

function formatTime(value?: string) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function resolveEventIcon(eventType: string) {
  return EVENT_ICON[eventType] || Document
}
</script>

<template>
  <section class="encounter-progress">
    <header class="encounter-progress__header">
      <h3 class="encounter-progress__title">就诊进度</h3>
      <p class="encounter-progress__hint">{{ nextHint }}</p>
    </header>

    <ElSkeleton v-if="loading && !timeline.length" :rows="2" animated />

    <template v-else>
      <ol class="encounter-progress__steps" aria-label="诊疗流程进度">
        <li
          v-for="(step, index) in WORKFLOW_STEPS"
          :key="step.key"
          class="encounter-progress__step"
          :class="{
            'is-done': index < currentStepIndex,
            'is-current': index === currentStepIndex,
            'is-pending': index > currentStepIndex,
          }"
        >
          <span class="encounter-progress__marker" aria-hidden="true">
            <ElIcon v-if="index < currentStepIndex" :size="14"><CircleCheck /></ElIcon>
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="encounter-progress__label">{{ step.label }}</span>
          <span
            v-if="index < WORKFLOW_STEPS.length - 1"
            class="encounter-progress__connector"
            :class="{ 'is-done': index < currentStepIndex }"
            aria-hidden="true"
          />
        </li>
      </ol>

      <div v-if="recentEvents.length" class="encounter-progress__events">
        <p class="encounter-progress__events-title">最近动态</p>
        <ul class="encounter-progress__timeline">
          <li
            v-for="(event, index) in recentEvents"
            :key="`${event.eventType}-${index}`"
            class="encounter-progress__timeline-item"
          >
            <span class="encounter-progress__timeline-icon" aria-hidden="true">
              <ElIcon :size="16">
                <component :is="resolveEventIcon(event.eventType)" />
              </ElIcon>
            </span>
            <div class="encounter-progress__timeline-body">
              <div class="encounter-progress__timeline-head">
                <strong>{{ event.title }}</strong>
                <time v-if="event.occurredAt">{{ formatTime(event.occurredAt) }}</time>
              </div>
              <p v-if="event.summary">{{ event.summary }}</p>
            </div>
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.encounter-progress {
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.encounter-progress__header {
  margin-block-end: var(--space-4);
}

.encounter-progress__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.encounter-progress__hint {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.encounter-progress__steps {
  display: flex;
  align-items: flex-start;
  gap: 0;
  margin: 0;
  padding: var(--space-2) 0 var(--space-4);
  list-style: none;
  overflow-x: auto;
}

.encounter-progress__step {
  position: relative;
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  align-items: center;
  min-width: 72px;
  padding: 0 6px;
  text-align: center;
}

.encounter-progress__marker {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-soft);
  background: #f1f5f9;
  box-shadow: inset 0 0 0 1px #e2e8f0;
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.encounter-progress__step.is-done .encounter-progress__marker {
  color: white;
  background: var(--color-primary);
  box-shadow: none;
}

.encounter-progress__step.is-current .encounter-progress__marker {
  color: white;
  background: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-soft);
}

.encounter-progress__label {
  margin-block-start: 10px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--color-text-muted);
}

.encounter-progress__step.is-current .encounter-progress__label {
  color: var(--color-primary-strong);
  font-weight: 700;
}

.encounter-progress__step.is-done .encounter-progress__label {
  color: var(--color-text);
}

.encounter-progress__connector {
  position: absolute;
  top: 15px;
  left: calc(50% + 18px);
  width: calc(100% - 36px);
  height: 3px;
  border-radius: 999px;
  background: #e2e8f0;
}

.encounter-progress__connector.is-done {
  background: linear-gradient(90deg, var(--color-primary), rgba(31, 140, 255, 0.45));
}

.encounter-progress__events {
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.encounter-progress__events-title {
  margin: 0 0 var(--space-3);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.encounter-progress__timeline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.encounter-progress__timeline-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-3);
  align-items: flex-start;
}

.encounter-progress__timeline-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.encounter-progress__timeline-body {
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px #eef2f7;
}

.encounter-progress__timeline-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: 13px;
}

.encounter-progress__timeline-head time {
  flex-shrink: 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.encounter-progress__timeline-body p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--color-text-muted);
}
</style>
