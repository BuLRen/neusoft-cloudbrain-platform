<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { buildMonthCells } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpDayScheduleItem } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  year: number
  month: number
  todayYmd: string
  selectedDate: string
  schedules: FollowUpDayScheduleItem[]
}>()

const emit = defineEmits<{
  'update:year': [value: number]
  'update:month': [value: number]
  selectDate: [date: string]
  dropPatient: [registerId: number, date: string]
  addSchedule: [date: string]
}>()

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const monthLabel = computed(() => `${props.year}年${props.month}月`)

const cells = computed(() => buildMonthCells(props.year, props.month, props.todayYmd))

const schedulesByDate = computed(() => {
  const map = new Map<string, FollowUpDayScheduleItem[]>()
  for (const item of props.schedules) {
    const list = map.get(item.scheduleDate) ?? []
    list.push(item)
    map.set(item.scheduleDate, list)
  }
  return map
})

function prevMonth() {
  if (props.month === 1) {
    emit('update:year', props.year - 1)
    emit('update:month', 12)
    return
  }
  emit('update:month', props.month - 1)
}

function nextMonth() {
  if (props.month === 12) {
    emit('update:year', props.year + 1)
    emit('update:month', 1)
    return
  }
  emit('update:month', props.month + 1)
}

function onDrop(date: string, event: DragEvent) {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('application/x-followup-register-id')
    || event.dataTransfer?.getData('text/plain')
  const registerId = Number(raw)
  if (!registerId) return
  emit('dropPatient', registerId, date)
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}
</script>

<template>
  <GlassCard class="follow-up-calendar">
    <div class="follow-up-calendar__head">
      <div>
        <h3>工作安排</h3>
        <p>点击日期添加事项，或将患者卡片拖拽到日期格安排访谈。</p>
      </div>
      <div class="follow-up-calendar__nav">
        <ElButton @click="prevMonth">上月</ElButton>
        <strong>{{ monthLabel }}</strong>
        <ElButton @click="nextMonth">下月</ElButton>
      </div>
    </div>

    <div class="follow-up-calendar__weekdays">
      <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
    </div>

    <div class="follow-up-calendar__grid">
      <button
        v-for="cell in cells"
        :key="cell.date"
        type="button"
        class="follow-up-calendar__cell"
        :class="{
          'follow-up-calendar__cell--muted': !cell.inMonth,
          'follow-up-calendar__cell--today': cell.isToday,
          'follow-up-calendar__cell--selected': cell.date === selectedDate,
        }"
        @click="emit('selectDate', cell.date)"
        @dblclick="emit('addSchedule', cell.date)"
        @dragover="onDragOver"
        @drop="onDrop(cell.date, $event)"
      >
        <span class="follow-up-calendar__day">{{ cell.date.slice(-2) }}</span>
        <div class="follow-up-calendar__chips">
          <span
            v-for="item in (schedulesByDate.get(cell.date) ?? []).slice(0, 2)"
            :key="item.id"
            class="follow-up-calendar__chip"
            :data-type="item.itemType"
          >
            {{ item.patientName ?? item.title }}
          </span>
          <span
            v-if="(schedulesByDate.get(cell.date) ?? []).length > 2"
            class="follow-up-calendar__more"
          >
            +{{ (schedulesByDate.get(cell.date) ?? []).length - 2 }}
          </span>
        </div>
      </button>
    </div>
  </GlassCard>
</template>

<style scoped>
.follow-up-calendar {
  padding: var(--space-4);
}

.follow-up-calendar__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-end: var(--space-4);
}

.follow-up-calendar__head h3 {
  margin: 0;
}

.follow-up-calendar__head p {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.follow-up-calendar__nav {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.follow-up-calendar__weekdays,
.follow-up-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.follow-up-calendar__weekdays {
  margin-block-end: 8px;
  color: var(--color-text-soft);
  font-size: 12px;
  text-align: center;
}

.follow-up-calendar__cell {
  min-height: 92px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  text-align: start;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.follow-up-calendar__cell:hover {
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
}

.follow-up-calendar__cell--muted {
  opacity: 0.45;
}

.follow-up-calendar__cell--today {
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--color-primary) 55%, transparent);
}

.follow-up-calendar__cell--selected {
  background: var(--color-primary-soft);
}

.follow-up-calendar__day {
  display: block;
  font-size: 13px;
  font-weight: 700;
}

.follow-up-calendar__chips {
  display: grid;
  gap: 4px;
  margin-block-start: 6px;
}

.follow-up-calendar__chip {
  display: block;
  overflow: hidden;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(31, 140, 255, 0.12);
  color: var(--color-primary-strong);
  font-size: 10px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.follow-up-calendar__chip[data-type='custom'] {
  background: rgba(124, 92, 255, 0.12);
  color: #7c5cff;
}

.follow-up-calendar__more {
  font-size: 10px;
  color: var(--color-text-soft);
}
</style>
