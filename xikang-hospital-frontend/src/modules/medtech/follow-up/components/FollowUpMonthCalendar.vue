<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { buildMonthCells, resolveInterviewScheduleVisualStatus } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpDayScheduleItem } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  year: number
  month: number
  todayYmd: string
  schedules: FollowUpDayScheduleItem[]
  shiftDates?: string[]
}>()

const emit = defineEmits<{
  'update:year': [value: number]
  'update:month': [value: number]
  openDay: [date: string]
  openShiftDay: [date: string]
}>()

const shiftDateSet = computed(() => new Set(props.shiftDates ?? []))

function onCellClick(date: string) {
  if (shiftDateSet.value.has(date)) {
    emit('openShiftDay', date)
  } else {
    emit('openDay', date)
  }
}

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
</script>

<template>
  <GlassCard class="follow-up-calendar">
    <div class="follow-up-calendar__head">
      <div>
        <h3>工作安排</h3>
        <p>点击日期查看当日访谈与其他日程，并添加新事项。</p>
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
        }"
        @click="onCellClick(cell.date)"
      >
        <span class="follow-up-calendar__day">
          {{ cell.date.slice(-2) }}
          <em v-if="shiftDateSet.has(cell.date)" class="follow-up-calendar__shift">班</em>
        </span>
        <div class="follow-up-calendar__chips">
          <span
            v-for="item in (schedulesByDate.get(cell.date) ?? []).slice(0, 2)"
            :key="item.id"
            class="follow-up-calendar__chip"
            :data-status="resolveInterviewScheduleVisualStatus(item, cell.date, todayYmd)"
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
    <div class="follow-up-calendar__legend">
      <span><i data-status="planned" />待进行</span>
      <span><i data-status="completed" />已完成</span>
      <span><i data-status="overdue" />逾期未访</span>
      <span><i data-status="custom" />自定义</span>
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

.follow-up-calendar__day {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
}

.follow-up-calendar__shift {
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(124, 92, 255, 0.18);
  color: #7c5cff;
  font-size: 9px;
  font-style: normal;
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
  font-size: 10px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.follow-up-calendar__chip[data-status='planned'] {
  background: rgba(31, 140, 255, 0.12);
  color: var(--color-primary-strong);
}

.follow-up-calendar__chip[data-status='custom'] {
  background: rgba(124, 92, 255, 0.12);
  color: #7c5cff;
}

.follow-up-calendar__chip[data-status='completed'] {
  background: rgba(34, 197, 94, 0.16);
  color: #15803d;
}

.follow-up-calendar__chip[data-status='overdue'] {
  background: rgba(239, 77, 90, 0.16);
  color: #c81e2d;
}

.follow-up-calendar__more {
  font-size: 10px;
  color: var(--color-text-soft);
}

.follow-up-calendar__legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
}

.follow-up-calendar__legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.follow-up-calendar__legend i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  font-style: normal;
}

.follow-up-calendar__legend i[data-status='planned'] {
  background: var(--color-primary-strong);
}

.follow-up-calendar__legend i[data-status='completed'] {
  background: #22c55e;
}

.follow-up-calendar__legend i[data-status='overdue'] {
  background: #ef4d5a;
}

.follow-up-calendar__legend i[data-status='custom'] {
  background: #7c5cff;
}
</style>
