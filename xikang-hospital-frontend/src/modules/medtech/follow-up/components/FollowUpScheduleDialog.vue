<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import { resolveInterviewScheduleVisualStatus } from '@/modules/medtech/follow-up/constants/followUpPriority'
import { beijingTodayYmd, formatYmdWeekday } from '@/shared/utils/beijingDate'
import type { FollowUpDashboardPatient, FollowUpDayScheduleItem } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  scheduleDate: string
  patients: FollowUpDashboardPatient[]
  schedules: FollowUpDayScheduleItem[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: { registerId?: number; scheduleDate: string; itemType: string; title: string }]
}>()

const showAddForm = ref(false)
const itemType = ref<'interview' | 'custom'>('interview')
const registerId = ref<number | undefined>()
const title = ref('')

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const dateLabel = computed(() => formatYmdWeekday(props.scheduleDate))

const daySchedules = computed(() =>
  props.schedules.filter((item) => item.scheduleDate === props.scheduleDate),
)

const interviewSchedules = computed(() =>
  daySchedules.value.filter((item) => item.itemType === 'interview'),
)

const otherSchedules = computed(() =>
  daySchedules.value.filter((item) => item.itemType !== 'interview'),
)

const scheduledInterviewRegisterIds = computed(
  () =>
    new Set(
      interviewSchedules.value
        .map((item) => item.registerId)
        .filter((id): id is number => typeof id === 'number'),
    ),
)

const availablePatients = computed(() =>
  props.patients.filter((patient) => !scheduledInterviewRegisterIds.value.has(patient.registerId)),
)

const canAddInterview = computed(
  () => itemType.value !== 'interview' || availablePatients.value.length > 0,
)

const todayYmd = beijingTodayYmd()

const scheduleStatusLabel: Record<string, string> = {
  planned: '待进行',
  completed: '已完成',
  cancelled: '已取消',
}

function resetAddForm() {
  itemType.value = 'interview'
  registerId.value = availablePatients.value[0]?.registerId
  title.value = ''
  showAddForm.value = false
}

function eventVisualClass(item: FollowUpDayScheduleItem) {
  return `follow-up-day-dialog__event--${resolveInterviewScheduleVisualStatus(item, props.scheduleDate, todayYmd)}`
}

function eventTagType(item: FollowUpDayScheduleItem): 'success' | 'danger' | 'warning' | 'info' {
  const visual = resolveInterviewScheduleVisualStatus(item, props.scheduleDate, todayYmd)
  if (visual === 'completed') return 'success'
  if (visual === 'overdue') return 'danger'
  if (visual === 'planned') return 'warning'
  return 'info'
}

watch(
  () => props.visible,
  (open) => {
    if (!open) {
      resetAddForm()
      return
    }
    itemType.value = 'interview'
    registerId.value = availablePatients.value[0]?.registerId
    title.value = ''
    showAddForm.value = false
  },
)

watch(availablePatients, (list) => {
  if (!registerId.value || scheduledInterviewRegisterIds.value.has(registerId.value)) {
    registerId.value = list[0]?.registerId
  }
})

function handleSubmit() {
  if (itemType.value === 'interview') {
    if (!registerId.value) return
    if (scheduledInterviewRegisterIds.value.has(registerId.value)) return
  }
  const payload = {
    registerId: itemType.value === 'interview' ? registerId.value : undefined,
    scheduleDate: props.scheduleDate,
    itemType: itemType.value === 'interview' ? 'interview' : 'custom',
    title: title.value.trim() || (itemType.value === 'custom' ? '自定义事项' : ''),
  }
  emit('submit', payload)
  resetAddForm()
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    width="520px"
    :show-close="true"
    :lock-scroll="false"
    modal-class="outcome-dialog-overlay follow-up-day-dialog-overlay"
    class="follow-up-day-dialog"
  >
    <template #header>
      <div class="follow-up-day-dialog__header">
        <p class="follow-up-day-dialog__eyebrow">日程详情</p>
        <h3 class="follow-up-day-dialog__date">{{ dateLabel }}</h3>
      </div>
    </template>

    <section class="follow-up-day-dialog__section">
      <div class="follow-up-day-dialog__section-head">
        <h4>访谈安排</h4>
        <span>{{ interviewSchedules.length }} 项</span>
      </div>
      <div v-if="interviewSchedules.length" class="follow-up-day-dialog__list">
        <article
          v-for="item in interviewSchedules"
          :key="item.id"
          class="follow-up-day-dialog__event follow-up-day-dialog__event--interview"
          :class="eventVisualClass(item)"
        >
          <span class="follow-up-day-dialog__dot" />
          <div class="follow-up-day-dialog__event-body">
            <strong>{{ item.patientName ?? item.title }}</strong>
            <p>{{ item.title }}</p>
            <span v-if="item.caseNumber" class="follow-up-day-dialog__meta">病历号 {{ item.caseNumber }}</span>
          </div>
          <ElTag size="small" :type="eventTagType(item)" effect="plain">
            {{
              resolveInterviewScheduleVisualStatus(item, scheduleDate, todayYmd) === 'overdue'
                ? '未完成'
                : scheduleStatusLabel[item.status ?? 'planned'] ?? '待进行'
            }}
          </ElTag>
        </article>
      </div>
      <ElEmpty v-else description="当日暂无访谈安排" :image-size="56" />
    </section>

    <section class="follow-up-day-dialog__section">
      <div class="follow-up-day-dialog__section-head">
        <h4>其他日程</h4>
        <span>{{ otherSchedules.length }} 项</span>
      </div>
      <div v-if="otherSchedules.length" class="follow-up-day-dialog__list">
        <article
          v-for="item in otherSchedules"
          :key="item.id"
          class="follow-up-day-dialog__event follow-up-day-dialog__event--custom"
        >
          <span class="follow-up-day-dialog__dot" />
          <div class="follow-up-day-dialog__event-body">
            <strong>{{ item.title }}</strong>
            <p v-if="item.patientName">{{ item.patientName }}</p>
          </div>
          <ElTag size="small" effect="plain">
            {{ scheduleStatusLabel[item.status ?? 'planned'] ?? '待进行' }}
          </ElTag>
        </article>
      </div>
      <ElEmpty v-else description="当日暂无其他日程" :image-size="56" />
    </section>

    <section class="follow-up-day-dialog__add">
      <button
        v-if="!showAddForm"
        type="button"
        class="follow-up-day-dialog__add-trigger"
        @click="showAddForm = true"
      >
        <span class="follow-up-day-dialog__add-icon">+</span>
        添加日程
      </button>

      <div v-else class="follow-up-day-dialog__add-panel">
        <ElInput
          v-model="title"
          class="follow-up-day-dialog__title-input"
          placeholder="添加标题"
          size="large"
        />
        <ElForm label-position="top" class="follow-up-day-dialog__form">
          <ElFormItem label="日程类型">
            <ElSelect v-model="itemType" style="width: 100%">
              <ElOption label="患者访谈" value="interview" />
              <ElOption label="自定义事项" value="custom" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="itemType === 'interview'" label="选择患者">
            <ElSelect
              v-model="registerId"
              style="width: 100%"
              placeholder="请选择患者"
              :disabled="!availablePatients.length"
            >
              <ElOption
                v-for="patient in availablePatients"
                :key="patient.registerId"
                :label="`${patient.realName}（${patient.caseNumber ?? patient.registerId}）`"
                :value="patient.registerId"
              />
            </ElSelect>
            <p v-if="!availablePatients.length" class="follow-up-day-dialog__hint">
              当日待访谈患者已全部排入日程，无法重复添加。
            </p>
          </ElFormItem>
        </ElForm>
        <div class="follow-up-day-dialog__add-actions">
          <ElButton @click="resetAddForm">取消</ElButton>
          <ElButton type="primary" :disabled="!canAddInterview" @click="handleSubmit">添加到日程</ElButton>
        </div>
      </div>
    </section>
  </ElDialog>
</template>

<style scoped>
.follow-up-day-dialog__header {
  padding-inline-end: var(--space-6);
}

.follow-up-day-dialog__eyebrow {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.follow-up-day-dialog__date {
  margin: var(--space-1) 0 0;
  font-size: 22px;
  line-height: 1.2;
}

.follow-up-day-dialog__section + .follow-up-day-dialog__section {
  margin-block-start: var(--space-4);
}

.follow-up-day-dialog__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-block-end: var(--space-2);
}

.follow-up-day-dialog__section-head h4 {
  margin: 0;
  font-size: 15px;
}

.follow-up-day-dialog__section-head span {
  color: var(--color-text-soft);
  font-size: 12px;
}

.follow-up-day-dialog__list {
  display: grid;
  gap: var(--space-2);
}

.follow-up-day-dialog__event {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: start;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
}

.follow-up-day-dialog__dot {
  width: 10px;
  height: 10px;
  margin-block-start: 6px;
  border-radius: 999px;
  background: var(--color-primary);
}

.follow-up-day-dialog__event--custom .follow-up-day-dialog__dot {
  background: #7c5cff;
}

.follow-up-day-dialog__event-body strong {
  display: block;
  font-size: 14px;
}

.follow-up-day-dialog__event-body p,
.follow-up-day-dialog__meta {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.follow-up-day-dialog__event--completed {
  border-color: rgba(34, 197, 94, 0.35);
  background: rgba(34, 197, 94, 0.06);
}

.follow-up-day-dialog__event--completed .follow-up-day-dialog__dot {
  background: #22c55e;
}

.follow-up-day-dialog__event--overdue {
  border-color: rgba(239, 77, 90, 0.4);
  background: rgba(239, 77, 90, 0.06);
}

.follow-up-day-dialog__event--overdue .follow-up-day-dialog__dot {
  background: #ef4d5a;
}

.follow-up-day-dialog__hint {
  margin: var(--space-1) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.follow-up-day-dialog__add {
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.follow-up-day-dialog__add-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3);
  border: 1px dashed color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-primary) 4%, #fff);
  color: var(--color-primary-strong);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.follow-up-day-dialog__add-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 16px;
  line-height: 1;
}

.follow-up-day-dialog__add-panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-soft) 55%, #fff);
}

.follow-up-day-dialog__title-input :deep(.el-input__wrapper) {
  box-shadow: none;
  border: 1px solid var(--color-border);
  background: #fff;
}

.follow-up-day-dialog__form {
  margin-block-start: calc(var(--space-2) * -1);
}

.follow-up-day-dialog__add-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
