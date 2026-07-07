<script setup lang="ts">
import { computed } from 'vue'
import type { DoctorSchedule } from '@/shared/types/schedule'
import {
  WEEKDAY_LABELS,
  monthGridCells,
  resolveScheduleTypeColor,
  resolveScheduleStatus,
  resolveSlotColor,
  isTodayIso,
} from '../../constants/scheduleStyle'

const props = defineProps<{
  /** 当前月份的年份，如 2026 */
  year: number
  /** 当前月份 0-11 */
  month: number
  /** 当月及跨月的排班数据 */
  schedules: DoctorSchedule[]
  /** 选中某天时触发 */
}>()

const emit = defineEmits<{
  (e: 'select-date', iso: string): void
}>()

// 42 个格子（6 行 × 7 列）
const cells = computed(() => monthGridCells(props.year, props.month))

// 按 workDate 分桶
const schedulesByDate = computed(() => {
  const map = new Map<string, DoctorSchedule[]>()
  for (const s of props.schedules) {
    if (!map.has(s.workDate)) map.set(s.workDate, [])
    map.get(s.workDate)!.push(s)
  }
  return map
})

function getSchedules(iso: string): DoctorSchedule[] {
  return schedulesByDate.value.get(iso) ?? []
}

function dayNumber(iso: string): number {
  return new Date(iso + 'T00:00:00').getDate()
}

// 号源紧张判断（>80% 视为紧张，红色高亮）
function isQuotaTight(s: DoctorSchedule): boolean {
  return s.totalQuota > 0 && s.usedQuota / s.totalQuota > 0.8
}

// 停诊/满诊需要单独显示状态符号
function isStopped(s: DoctorSchedule): boolean {
  return s.status === '停诊'
}
</script>

<template>
  <div class="ms-calendar">
    <!-- 星期表头 -->
    <div class="ms-weekdays" role="row">
      <div
        v-for="label in WEEKDAY_LABELS"
        :key="label"
        class="ms-weekday"
      >
        {{ label }}
      </div>
    </div>

    <!-- 6 行 × 7 列 -->
    <div class="ms-body" role="rowgroup">
      <div
        v-for="(_, rowIdx) in 6"
        :key="`r-${rowIdx}`"
        class="ms-row"
        :class="{ 'ms-row--divided': rowIdx > 0 }"
        role="row"
      >
        <button
          v-for="cell in cells.slice(rowIdx * 7, rowIdx * 7 + 7)"
          :key="cell.iso"
          type="button"
          class="ms-cell"
          :class="{
            'ms-cell--out': !cell.inMonth,
          }"
          :data-today="isTodayIso(cell.iso) || undefined"
          :disabled="!cell.inMonth"
          @click="cell.inMonth && emit('select-date', cell.iso)"
        >
          <span class="ms-cell__date">
            <span class="ms-cell__date-num">{{ dayNumber(cell.iso) }}</span>
          </span>

          <div v-if="cell.inMonth && getSchedules(cell.iso).length" class="ms-cell__items">
            <div
              v-for="sched in getSchedules(cell.iso).slice(0, 3)"
              :key="sched.id"
              class="ms-cell__item"
              :data-stopped="isStopped(sched) || undefined"
            >
              <!-- 左侧 3px 时段色条 -->
              <span
                class="ms-cell__bar"
                :style="{ background: resolveSlotColor(sched.timeSlot).bar }"
              ></span>
              <!-- 中间：科室名（停诊则显示「停诊」） -->
              <span class="ms-cell__name">
                {{ isStopped(sched) ? '停诊' : (sched.departmentName || sched.registLevelName || '门诊') }}
              </span>
              <!-- 右侧：号源或状态符号 -->
              <span
                v-if="isStopped(sched)"
                class="ms-cell__sym"
              >✕</span>
              <span
                v-else
                class="ms-cell__quota"
                :data-tight="isQuotaTight(sched) || undefined"
              >{{ sched.usedQuota }}/{{ sched.totalQuota }}</span>
            </div>
            <div
              v-if="getSchedules(cell.iso).length > 3"
              class="ms-cell__more"
            >
              +{{ getSchedules(cell.iso).length - 3 }} 场
            </div>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ============================================================
   外层框：整张月历包一圈细描边，与周视图同款深墨蓝
   ============================================================ */
.ms-calendar {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--sched-grid-line);
  border-radius: 12px;
  overflow: hidden;
  background: transparent; /* 外层透明，让底层 GlassCard 主背景透过来 */
}

/* 星期表头 */
.ms-weekdays {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 0;
  background: var(--sched-surface);
  border-bottom: 1px solid var(--sched-grid-line);
}

.ms-weekday {
  text-align: center;
  font-size: 12px;
  font-weight: 500;
  color: var(--sched-ink-mute);
  letter-spacing: 0.04em;
  padding: 10px 0;
  border-right: 1px solid var(--sched-grid-line);
}

.ms-weekday:last-child {
  border-right: none;
}

/* 行：gap=0，用 border 做分隔 */
.ms-row {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 0;
}

.ms-row--divided {
  /* 不再需要 padding-top，靠上一行的 border-bottom 分隔 */
}

/* 格子：日历单元格风格，hover 时圆角高亮（保留日历的呼吸感） */
.ms-cell {
  position: relative;
  min-height: 96px;
  padding: 8px 10px;
  border: none;
  border-right: 1px solid var(--sched-grid-line-soft);
  border-bottom: 1px solid var(--sched-grid-line-soft);
  background: transparent;
  text-align: left;
  cursor: pointer;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-family: var(--sched-font-body);
  color: var(--sched-ink);
  transition: background 140ms cubic-bezier(0.2, 0, 0, 1),
              box-shadow 140ms cubic-bezier(0.2, 0, 0, 1);
}

.ms-cell:nth-child(7n) {
  border-right: none;
}

/* 用最后一行兜底，但因为 6 行 × 7 列 = 42 格固定，最后 7 格去掉 border-bottom */
.ms-row:last-child .ms-cell {
  border-bottom: none;
}

.ms-cell:hover:not(:disabled) {
  background: var(--sched-surface);
  box-shadow: inset 0 0 0 1px var(--sched-grid-line);
  z-index: 1;
}

.ms-cell:focus-visible {
  outline: 2px solid var(--sched-primary);
  outline-offset: -2px;
  z-index: 2;
}

.ms-cell:disabled {
  cursor: default;
}

/* 非本月日期：淡蓝灰底色，比本月格子更深、更暗，弱化存在感 */
.ms-cell--out {
  background: rgba(70, 111, 160, 0.05);
}

.ms-cell--out:hover {
  background: rgba(70, 111, 160, 0.05);
  box-shadow: none;
}

/* 日期数字（图钉式，右上角） */
.ms-cell__date {
  align-self: flex-end;
  display: flex;
  justify-content: flex-end;
}

.ms-cell__date-num {
  font-family: var(--sched-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1;
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* "今天"的印章 */
.ms-cell[data-today] .ms-cell__date-num {
  background: var(--sched-today);
  color: #FFFFFF;
  border-radius: 50%;
  width: 26px;
  height: 26px;
  min-width: 26px;
  font-size: 15px;
}

.ms-cell[data-today] {
  background: var(--sched-today-soft);
  box-shadow: inset 0 0 0 1px rgba(194, 113, 10, 0.55);
  z-index: 1;
}

.ms-cell[data-today]:hover {
  background: var(--sched-today-soft);
  box-shadow: inset 0 0 0 1px rgba(194, 113, 10, 0.55);
}

/* ============================================================
   班次条目：左侧 3px 时段色条 + 科室名 + 号源数字
   设计选择：色条=时段，号源数字=紧张度，符号=异常状态
   像真实日历应用那样，一眼看出节奏
   ============================================================ */
.ms-cell__items {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: auto;
}

.ms-cell__item {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 18px;
  padding-right: 2px;
  font-size: 11px;
  line-height: 1;
  color: var(--sched-ink-soft);
  overflow: hidden;
}

/* 左侧 3px 时段色条：圆角小竖条，色值来自 resolveSlotColor() */
.ms-cell__bar {
  flex-shrink: 0;
  width: 3px;
  height: 12px;
  border-radius: 2px;
}

/* 中间科室名 */
.ms-cell__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: var(--sched-ink);
}

/* 右侧号源数字：等宽字体，紧张时变红 */
.ms-cell__quota {
  flex-shrink: 0;
  font-family: var(--sched-font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--sched-ink-mute);
  letter-spacing: -0.01em;
}

.ms-cell__quota[data-tight] {
  color: var(--sched-danger);
}

/* 停诊/异常状态符号 */
.ms-cell__sym {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--sched-ink-mute);
}

.ms-cell__item[data-stopped] .ms-cell__name {
  color: var(--sched-ink-mute);
  text-decoration: line-through;
  text-decoration-color: var(--sched-ink-mute);
  text-decoration-thickness: 1px;
}

/* 「+N 场」更多提示 */
.ms-cell__more {
  font-size: 10px;
  color: var(--sched-ink-mute);
  font-weight: 500;
  padding-left: 9px;
  margin-top: 1px;
  letter-spacing: 0.02em;
}

/* 响应式：gap 已为 0，仅调高度和内边距 */
@media (max-width: 1080px) {
  .ms-cell {
    min-height: 84px;
    padding: 6px 8px;
  }
  .ms-cell__date-num {
    font-size: 14px;
  }
  .ms-cell__item {
    height: 16px;
    font-size: 10px;
  }
  .ms-cell__bar {
    height: 10px;
  }
}

@media (max-width: 720px) {
  .ms-cell {
    min-height: 68px;
    padding: 4px 6px;
  }
  /* 小屏只保留色条 + 数字，去掉科室名 */
  .ms-cell__name {
    display: none;
  }
  .ms-cell__items {
    gap: 4px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ms-cell {
    transition: none;
  }
}
</style>
