<script setup lang="ts">
import { computed } from 'vue'
import type { DoctorSchedule } from '@/shared/types/schedule'
import {
  WEEKDAY_LABELS,
  TIME_SLOT_ORDER,
  resolveScheduleTypeColor,
  resolveScheduleStatus,
  isTodayIso,
} from '../../constants/scheduleStyle'

const props = defineProps<{
  /** 本周 7 天 ISO 日期，从周一开始 */
  weekDates: string[]
  /** 本周排班数据 */
  schedules: DoctorSchedule[]
  /** 当前选中的排班 ID（用于高亮） */
  selectedScheduleId?: number | null
}>()

const emit = defineEmits<{
  (e: 'select', schedule: DoctorSchedule): void
}>()

// 把 schedules 按 workDate + timeSlot 分桶
const scheduleMap = computed(() => {
  const map = new Map<string, DoctorSchedule>()
  for (const s of props.schedules) {
    map.set(`${s.workDate}|${s.timeSlot}`, s)
  }
  return map
})

function getSchedule(iso: string, slot: string): DoctorSchedule | undefined {
  return scheduleMap.value.get(`${iso}|${slot}`)
}

// 实际要渲染的时段行（至少显示上午/下午，若有晚上排班再加晚上行）
const activeSlots = computed(() => {
  const slots = new Set(props.schedules.map((s) => s.timeSlot))
  const result: string[] = []
  for (const slot of TIME_SLOT_ORDER) {
    if (slots.has(slot) || slot === '上午' || slot === '下午') {
      result.push(slot)
    }
  }
  return result
})

function quotaRatio(s: DoctorSchedule): number {
  return s.totalQuota > 0 ? s.usedQuota / s.totalQuota : 0
}

function slotTimeHint(slot: string): string {
  if (slot === '上午') return '08:00–12:00'
  if (slot === '下午') return '14:00–17:30'
  return '18:00–21:00'
}
</script>

<template>
  <div class="ws-grid" role="grid" aria-label="本周排班">
    <!-- 表头：时段列空位 + 7 天 -->
    <div class="ws-corner" role="columnheader"></div>
    <div
      v-for="(iso, idx) in weekDates"
      :key="`h-${iso}`"
      class="ws-header"
      :data-today="isTodayIso(iso) || undefined"
      role="columnheader"
    >
      <span class="ws-header__weekday">{{ WEEKDAY_LABELS[idx] }}</span>
      <span class="ws-header__date">{{ new Date(iso + 'T00:00:00').getDate() }}</span>
    </div>

    <!-- 每个时段一行 -->
    <template v-for="slot in activeSlots" :key="`row-${slot}`">
      <!-- 时段标签列 -->
      <div class="ws-slot" role="rowheader">
        <span class="ws-slot__label">{{ slot }}</span>
        <span class="ws-slot__time">{{ slotTimeHint(slot) }}</span>
      </div>

      <!-- 7 天的格子 -->
      <div
        v-for="iso in weekDates"
        :key="`${slot}-${iso}`"
        class="ws-col"
      >
        <template v-if="getSchedule(iso, slot)">
          <button
            type="button"
            class="ws-cell"
            :data-today="isTodayIso(iso) || undefined"
            :data-selected="selectedScheduleId === getSchedule(iso, slot)!.id || undefined"
            :style="{
              '--type-dot': resolveScheduleTypeColor(getSchedule(iso, slot)!.registLevelName).dot,
              '--type-text': resolveScheduleTypeColor(getSchedule(iso, slot)!.registLevelName).text,
            } as Record<string, string>"
            @click="emit('select', getSchedule(iso, slot)!)"
          >
            <span class="ws-cell__type"></span>
            <div class="ws-cell__body">
              <div class="ws-cell__dept">
                {{ getSchedule(iso, slot)!.departmentName || '门诊' }}
              </div>
              <div class="ws-cell__level">
                {{ getSchedule(iso, slot)!.registLevelName || '普通号' }}
              </div>
              <div class="ws-cell__quota">
                <span
                  class="ws-cell__used"
                  :data-tight="quotaRatio(getSchedule(iso, slot)!) > 0.8 || undefined"
                >{{ getSchedule(iso, slot)!.usedQuota }}</span>
                <span class="ws-cell__sep">/</span>
                <span class="ws-cell__total">{{ getSchedule(iso, slot)!.totalQuota }}</span>
              </div>
            </div>
            <span
              class="ws-cell__status"
              :style="{ color: resolveScheduleStatus({ ...getSchedule(iso, slot)!, isToday: isTodayIso(iso) }).color }"
              :title="resolveScheduleStatus({ ...getSchedule(iso, slot)!, isToday: isTodayIso(iso) }).label"
            >
              {{ resolveScheduleStatus({ ...getSchedule(iso, slot)!, isToday: isTodayIso(iso) }).symbol }}
            </span>
          </button>
        </template>
        <div
          v-else
          class="ws-cell ws-cell--empty"
          :data-today="isTodayIso(iso) || undefined"
        >
          <span class="ws-cell__rest">休</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ============================================================
   外层框：整张排班表包一圈细描边，gap 归零用 padding 替代留白
   ============================================================ */
.ws-grid {
  display: grid;
  grid-template-columns: 72px repeat(7, minmax(0, 1fr));
  gap: 0;
  border: 1px solid var(--sched-grid-line);
  border-radius: 12px;
  overflow: hidden;
  background: transparent; /* 外层透明，让底层 GlassCard 主背景透过来；内部表头/格子用玻璃白浮起 */
}

.ws-corner {
  background: transparent;
  border-right: 1px solid var(--sched-grid-line);
  border-bottom: 1px solid var(--sched-grid-line);
}

/* 表头 */
.ws-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 12px 4px 14px;
  font-family: var(--sched-font-body);
  position: relative;
  background: var(--sched-surface);
  border-bottom: 1px solid var(--sched-grid-line);
  border-right: 1px solid var(--sched-grid-line);
}

.ws-header:last-child {
  border-right: none;
}

.ws-header__weekday {
  font-size: 12px;
  font-weight: 500;
  color: var(--sched-ink-mute);
  letter-spacing: 0.04em;
}

.ws-header__date {
  font-family: var(--sched-font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1.2;
}

.ws-header[data-today] .ws-header__weekday,
.ws-header[data-today] .ws-header__date {
  color: var(--sched-today);
}

.ws-header[data-today]::after {
  content: '';
  position: absolute;
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 2px;
  background: var(--sched-today);
  border-radius: 1px;
}

/* 时段列：右侧分隔线，与格子区划分 */
.ws-slot {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  padding: 14px 12px;
  gap: 2px;
  border-right: 1px solid var(--sched-grid-line);
  border-bottom: 1px solid var(--sched-grid-line);
  background: var(--sched-surface);
}

.ws-slot__label {
  font-family: var(--sched-font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1.2;
}

.ws-slot__time {
  font-family: var(--sched-font-mono);
  font-size: 10px;
  color: var(--sched-ink-mute);
  letter-spacing: 0.02em;
}

/* 列容器：每列右侧分隔线，行底部已有 ws-slot 兜底 */
.ws-col {
  min-width: 0;
  border-right: 1px solid var(--sched-grid-line);
  border-bottom: 1px solid var(--sched-grid-line);
  background: transparent;
}

.ws-col:last-child {
  border-right: none;
}

/* 格子：无边框，靠底色和位置体现，hover/today/selected 用 inset 边线高亮 */
.ws-cell {
  position: relative;
  width: 100%;
  min-height: 110px;
  padding: 12px 12px 12px 18px;
  border: none;
  border-radius: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  box-shadow: none;
  transition:
    background 140ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 140ms cubic-bezier(0.2, 0, 0, 1);
  font-family: var(--sched-font-body);
  color: var(--sched-ink);
}

.ws-cell:hover {
  background: var(--sched-surface-alt);
  box-shadow: inset 0 0 0 1px var(--sched-grid-line);
}

.ws-cell:focus-visible {
  outline: 2px solid var(--sched-primary);
  outline-offset: -2px;
}

.ws-cell[data-selected] {
  background: var(--sched-primary-soft);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.55);
}

.ws-cell[data-today] {
  background: var(--sched-today-soft);
  box-shadow: inset 0 0 0 1px rgba(194, 113, 10, 0.55);
}

.ws-cell[data-today]:hover {
  background: var(--sched-today-soft);
  box-shadow: inset 0 0 0 1px rgba(194, 113, 10, 0.55);
}

/* 班次类型色带（左侧 3px 竖条） */
.ws-cell__type {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--type-dot, #3B82C4);
}

.ws-cell__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ws-cell__dept {
  font-size: 13px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-cell__level {
  font-size: 11px;
  color: var(--type-text, #1E4F8B);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.ws-cell__quota {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-family: var(--sched-font-mono);
}

.ws-cell__used {
  font-size: 16px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1;
}

.ws-cell__used[data-tight] {
  color: var(--sched-danger);
}

.ws-cell__sep {
  font-size: 12px;
  color: var(--sched-ink-mute);
  margin: 0 1px;
}

.ws-cell__total {
  font-size: 12px;
  color: var(--sched-ink-soft);
  font-weight: 500;
}

/* 状态符号（右上角） */
.ws-cell__status {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  line-height: 1;
  font-weight: 700;
}

/* 空格子（休息）：透明，无边线 */
.ws-cell--empty {
  background: transparent;
  cursor: default;
  box-shadow: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ws-cell--empty:hover {
  background: transparent;
  box-shadow: none;
}

.ws-cell--empty[data-today] {
  background: var(--sched-today-soft);
  box-shadow: inset 0 0 0 1px rgba(194, 113, 10, 0.55);
}

.ws-cell__rest {
  font-family: var(--sched-font-display);
  font-size: 13px;
  color: var(--sched-line-strong);
  font-weight: 500;
  letter-spacing: 0.1em;
}

/* 响应式：gap 已为 0，仅调列宽和高度 */
@media (max-width: 1080px) {
  .ws-grid {
    grid-template-columns: 60px repeat(7, minmax(0, 1fr));
  }
  .ws-cell {
    min-height: 96px;
    padding: 10px 10px 10px 14px;
  }
}

@media (max-width: 720px) {
  .ws-grid {
    grid-template-columns: 48px repeat(7, minmax(0, 1fr));
  }
  .ws-cell {
    min-height: 80px;
    padding: 8px 6px 8px 12px;
  }
  .ws-cell__dept {
    font-size: 12px;
  }
  .ws-slot__time {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ws-cell {
    transition: none;
  }
}
</style>
