<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import { buildMonthCells } from '@/modules/medtech/follow-up/constants/followUpPriority'
import { beijingTodayYmd } from '@/shared/utils/beijingDate'
import type { FollowUpStaffShift } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  year: number
  month: number
  shifts: FollowUpStaffShift[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:year': [value: number]
  'update:month': [value: number]
  openDay: [date: string, dayShifts: FollowUpStaffShift[]]
}>()

const todayYmd = beijingTodayYmd()
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const DOCTOR_COLORS = [
  '#1f8cff',
  '#7c5cff',
  '#0d9488',
  '#ea580c',
  '#db2777',
  '#4f46e5',
  '#059669',
  '#ca8a04',
]

const monthLabel = computed(() => `${props.year}年${props.month}月`)
const cells = computed(() => buildMonthCells(props.year, props.month, todayYmd))

const shiftsByDate = computed(() => {
  const map = new Map<string, FollowUpStaffShift[]>()
  for (const shift of props.shifts) {
    const list = map.get(shift.workDate) ?? []
    list.push(shift)
    map.set(shift.workDate, list)
  }
  for (const list of map.values()) {
    list.sort((a, b) => String(a.employeeName ?? '').localeCompare(String(b.employeeName ?? ''), 'zh-CN'))
  }
  return map
})

const doctorLegend = computed(() => {
  const map = new Map<number, { employeeId: number; name: string; color: string; shiftDays: number }>()
  for (const shift of props.shifts) {
    const employeeId = shift.employeeId ?? 0
    if (!employeeId) continue
    const existing = map.get(employeeId)
    if (existing) {
      existing.shiftDays += 1
      continue
    }
    map.set(employeeId, {
      employeeId,
      name: shift.employeeName ?? `员工 #${employeeId}`,
      color: doctorColor(employeeId),
      shiftDays: 1,
    })
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
})

function doctorColor(employeeId: number) {
  return DOCTOR_COLORS[Math.abs(employeeId) % DOCTOR_COLORS.length]!
}

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

function openDay(date: string) {
  const dayShifts = shiftsByDate.value.get(date) ?? []
  if (!dayShifts.length) return
  emit('openDay', date, dayShifts)
}

function isWeekend(date: string) {
  const day = new Date(`${date}T12:00:00`).getDay()
  return day === 0 || day === 6
}
</script>

<template>
  <div class="admin-shift-calendar" :aria-busy="loading">
    <div class="admin-shift-calendar__head">
      <div>
        <p class="admin-shift-calendar__hint">点击有排班的日期查看当日各医生联系任务明细</p>
      </div>
      <div class="admin-shift-calendar__nav">
        <ElButton size="small" @click="prevMonth">上月</ElButton>
        <strong>{{ monthLabel }}</strong>
        <ElButton size="small" @click="nextMonth">下月</ElButton>
      </div>
    </div>

    <div class="admin-shift-calendar__weekdays">
      <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
    </div>

    <div class="admin-shift-calendar__grid">
      <button
        v-for="cell in cells"
        :key="cell.date"
        type="button"
        class="admin-shift-calendar__cell"
        :class="{
          'admin-shift-calendar__cell--muted': !cell.inMonth,
          'admin-shift-calendar__cell--today': cell.isToday,
          'admin-shift-calendar__cell--weekend': isWeekend(cell.date),
          'admin-shift-calendar__cell--empty': !(shiftsByDate.get(cell.date)?.length),
        }"
        :disabled="!(shiftsByDate.get(cell.date)?.length)"
        @click="openDay(cell.date)"
      >
        <span class="admin-shift-calendar__day">{{ cell.date.slice(-2) }}</span>
        <div v-if="shiftsByDate.get(cell.date)?.length" class="admin-shift-calendar__entries">
          <span
            v-for="shift in (shiftsByDate.get(cell.date) ?? []).slice(0, 3)"
            :key="`${shift.id}-${shift.employeeId}`"
            class="admin-shift-calendar__entry"
            :style="{ '--doctor-color': doctorColor(shift.employeeId ?? 0) }"
          >
            <i class="admin-shift-calendar__dot" />
            {{ shift.employeeName ?? '未命名' }}
            · {{ shift.contactTasks?.length ?? 0 }}人
          </span>
          <span
            v-if="(shiftsByDate.get(cell.date) ?? []).length > 3"
            class="admin-shift-calendar__more"
          >
            +{{ (shiftsByDate.get(cell.date) ?? []).length - 3 }} 位医生
          </span>
        </div>
      </button>
    </div>

    <div v-if="doctorLegend.length" class="admin-shift-calendar__legend">
      <span
        v-for="doctor in doctorLegend"
        :key="doctor.employeeId"
        class="admin-shift-calendar__legend-item"
      >
        <i :style="{ background: doctor.color }" />
        {{ doctor.name }}（{{ doctor.shiftDays }} 天）
      </span>
    </div>
  </div>
</template>

<style scoped>
.admin-shift-calendar {
  display: grid;
  gap: var(--space-3);
}

.admin-shift-calendar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.admin-shift-calendar__hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.admin-shift-calendar__nav {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.admin-shift-calendar__weekdays,
.admin-shift-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.admin-shift-calendar__weekdays {
  color: var(--color-text-soft);
  font-size: 12px;
  text-align: center;
}

.admin-shift-calendar__cell {
  min-height: 108px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  text-align: start;
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.admin-shift-calendar__cell:not(:disabled):hover {
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.admin-shift-calendar__cell:disabled {
  cursor: default;
  opacity: 0.72;
}

.admin-shift-calendar__cell--muted {
  opacity: 0.45;
}

.admin-shift-calendar__cell--weekend:not(.admin-shift-calendar__cell--empty) {
  background: color-mix(in srgb, var(--color-bg-soft) 65%, var(--color-surface-strong));
}

.admin-shift-calendar__cell--today {
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--color-primary) 55%, transparent);
}

.admin-shift-calendar__day {
  display: block;
  font-size: 13px;
  font-weight: 700;
}

.admin-shift-calendar__entries {
  display: grid;
  gap: 4px;
  margin-block-start: 6px;
}

.admin-shift-calendar__entry {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--doctor-color) 14%, transparent);
  color: var(--doctor-color);
  font-size: 10px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.admin-shift-calendar__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--doctor-color);
}

.admin-shift-calendar__more {
  font-size: 10px;
  color: var(--color-text-soft);
}

.admin-shift-calendar__legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  padding-block-start: var(--space-2);
  border-block-start: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 12px;
}

.admin-shift-calendar__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.admin-shift-calendar__legend-item i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  font-style: normal;
}
</style>
