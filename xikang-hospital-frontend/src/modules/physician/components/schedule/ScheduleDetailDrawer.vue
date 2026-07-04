<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDrawer, ElIcon, ElTag } from 'element-plus'
import { Clock, Document, OfficeBuilding, User, WarnTriangleFilled } from '@element-plus/icons-vue'
import type { DoctorSchedule } from '@/shared/types/schedule'
import {
  resolveScheduleTypeColor,
  resolveScheduleStatus,
  resolveSlotColor,
  isTodayIso,
} from '../../constants/scheduleStyle'

const props = defineProps<{
  modelValue: boolean
  schedule: DoctorSchedule | null
  /** 当前登录医生姓名，用于 schedule.physicianName 缺失时兜底 */
  fallbackPhysicianName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'apply-leave', schedule: DoctorSchedule): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

function quotaRatio(s: DoctorSchedule): number {
  return s && s.totalQuota > 0 ? s.usedQuota / s.totalQuota : 0
}

// 三个时段的时间范围（用于时间轴展示）
const SLOT_TIMES: Record<string, { start: string; end: string; order: number }> = {
  '上午': { start: '08:00', end: '12:00', order: 0 },
  '下午': { start: '14:00', end: '17:30', order: 1 },
  '晚上': { start: '18:00', end: '21:00', order: 2 },
}

function slotTimeHint(slot?: string): string {
  const t = slot ? SLOT_TIMES[slot] : null
  return t ? `${t.start} – ${t.end}` : ''
}

// 一天的时间轴 6:00 – 22:00，共 16 小时 = 16 段
function slotAxisPercent(slot?: string): { left: number; width: number } {
  if (!slot || !SLOT_TIMES[slot]) return { left: 0, width: 0 }
  const dayStart = 6  // 6:00
  const dayEnd = 22   // 22:00
  const total = dayEnd - dayStart
  const toHours = (t: string) => {
    const [h, m] = t.split(':').map(Number)
    return h + (m || 0) / 60
  }
  const s = toHours(SLOT_TIMES[slot].start) - dayStart
  const e = toHours(SLOT_TIMES[slot].end) - dayStart
  return {
    left: (s / total) * 100,
    width: ((e - s) / total) * 100,
  }
}

// 医生姓名：优先 schedule 自带，缺失时用兜底
const physicianDisplay = computed(() => {
  if (!props.schedule) return '—'
  const name = props.schedule.physicianName?.trim()
  if (name) return name
  if (props.fallbackPhysicianName?.trim()) return props.fallbackPhysicianName
  return '—'
})

// 格式化更新时间
function formatUpdateTime(iso?: string): string {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

function onLeave() {
  if (props.schedule) {
    emit('apply-leave', props.schedule)
  }
}
</script>

<template>
  <ElDrawer
    v-model="open"
    direction="rtl"
    size="400px"
    :with-header="false"
    class="sched-drawer"
  >
    <div v-if="schedule" class="drawer-inner">
      <!-- ============== 头部：日期 + 医生 + 今天章 ============== -->
      <header class="drawer-head">
        <div class="drawer-head__row">
          <div class="drawer-head__date">
            <span class="drawer-head__day">{{ new Date(schedule.workDate + 'T00:00:00').getDate() }}</span>
            <span class="drawer-head__meta">
              <strong>{{ schedule.workDate }}</strong>
              <small>{{ schedule.timeSlot }} · {{ slotTimeHint(schedule.timeSlot) }}</small>
            </span>
          </div>
          <span
            v-if="isTodayIso(schedule.workDate)"
            class="drawer-head__today"
          >今天</span>
        </div>

        <!-- 医生姓名 + 职称（核心信息，从详情列表提到头部） -->
        <div class="drawer-head__doctor">
          <ElIcon class="drawer-head__doctor-icon"><User /></ElIcon>
          <span class="drawer-head__doctor-name">{{ physicianDisplay }}</span>
          <span v-if="schedule.physicianTitle" class="drawer-head__doctor-title">
            {{ schedule.physicianTitle }}
          </span>
        </div>
      </header>

      <!-- ============== 就诊信息（tags） ============== -->
      <section class="drawer-section">
        <div class="drawer-section__title">
          <ElIcon><OfficeBuilding /></ElIcon>
          <span>就诊信息</span>
        </div>
        <div class="drawer-tags">
          <ElTag
            size="default"
            effect="light"
            round
            :style="{
              background: resolveScheduleTypeColor(schedule.registLevelName).bg,
              color: resolveScheduleTypeColor(schedule.registLevelName).text,
              borderColor: resolveScheduleTypeColor(schedule.registLevelName).border,
            }"
          >
            {{ schedule.registLevelName || '普通号' }}
          </ElTag>
          <ElTag size="default" effect="plain" round type="info">
            {{ schedule.departmentName || '未指定科室' }}
          </ElTag>
          <ElTag
            v-if="schedule.status && schedule.status !== '正常'"
            size="default"
            effect="light"
            round
            type="warning"
          >
            {{ schedule.status }}
          </ElTag>
        </div>
      </section>

      <!-- ============== 出诊时间段（signature：时间轴 mini 可视化） ============== -->
      <section class="drawer-section">
        <div class="drawer-section__title">
          <ElIcon><Clock /></ElIcon>
          <span>出诊时段</span>
        </div>
        <div class="time-axis">
          <!-- 时间刻度 -->
          <div class="time-axis__ruler">
            <span>6</span><span>9</span><span>12</span><span>15</span><span>18</span><span>21</span>
          </div>
          <!-- 一天的轨道（6:00-22:00） -->
          <div class="time-axis__track">
            <!-- 非当前时段的灰色背景（上午/下午/晚上任选其他两个） -->
            <template v-for="slot in (['上午', '下午', '晚上'] as const)" :key="`bg-${slot}`">
              <div
                v-if="slot !== schedule.timeSlot"
                class="time-axis__bg"
                :style="{
                  left: slotAxisPercent(slot).left + '%',
                  width: slotAxisPercent(slot).width + '%',
                }"
              ></div>
            </template>
            <!-- 当前的班次：彩色高亮 -->
            <div
              class="time-axis__active"
              :style="{
                left: slotAxisPercent(schedule.timeSlot).left + '%',
                width: slotAxisPercent(schedule.timeSlot).width + '%',
                background: resolveSlotColor(schedule.timeSlot).bar,
              }"
            >
              <span class="time-axis__label">{{ schedule.timeSlot }}</span>
            </div>
          </div>
          <p class="time-axis__hint">
            {{ slotTimeHint(schedule.timeSlot) }} 共 3.5 小时
          </p>
        </div>
      </section>

      <!-- ============== 号源概览（大数字 + 进度条） ============== -->
      <section class="drawer-section">
        <div class="drawer-section__title">
          <ElIcon><User /></ElIcon>
          <span>号源</span>
        </div>
        <div class="quota-block">
          <div class="quota-block__numbers">
            <span
              class="quota-block__used"
              :data-tight="quotaRatio(schedule) > 0.8 || undefined"
            >{{ schedule.usedQuota }}</span>
            <span class="quota-block__sep">/</span>
            <span class="quota-block__total">{{ schedule.totalQuota }}</span>
          </div>
          <span class="quota-block__label">
            已挂 <strong>{{ schedule.usedQuota }}</strong> / 共 <strong>{{ schedule.totalQuota }}</strong> 个号
            · 余 <strong>{{ schedule.availableQuota }}</strong>
          </span>
          <div class="quota-block__bar" role="progressbar">
            <div
              class="quota-block__bar-fill"
              :style="{
                width: `${Math.min(quotaRatio(schedule) * 100, 100)}%`,
                background: quotaRatio(schedule) > 0.8 ? 'var(--sched-danger)' : 'var(--sched-success)',
              }"
            ></div>
          </div>
        </div>
      </section>

      <!-- ============== 详情描述（精简版，移除已展示的医生/时段） ============== -->
      <section class="drawer-section">
        <ElDescriptions :column="1" border size="small" class="drawer-desc">
          <ElDescriptionsItem label="挂号单价">
            ¥ {{ schedule.price ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="当前状态">
            <span
              class="drawer-status-dot"
              :style="{ color: resolveScheduleStatus({ ...schedule, isToday: isTodayIso(schedule.workDate) }).color }"
            >
              {{ resolveScheduleStatus({ ...schedule, isToday: isTodayIso(schedule.workDate) }).symbol }}
            </span>
            {{ resolveScheduleStatus({ ...schedule, isToday: isTodayIso(schedule.workDate) }).label }}
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="schedule.updateTime" label="最后更新">
            {{ formatUpdateTime(schedule.updateTime) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <!-- ============== 修改痕迹（如果有） ============== -->
      <section v-if="schedule.modified" class="drawer-section">
        <div class="drawer-section__title drawer-section__title--warn">
          <ElIcon><WarnTriangleFilled /></ElIcon>
          <span>排班调整记录</span>
        </div>
        <div class="drawer-modified">
          <ElIcon class="drawer-modified__icon"><Document /></ElIcon>
          <p class="drawer-modified__text">
            {{ schedule.modifyRemark || '此班次已经被系统或管理员调整过' }}
          </p>
        </div>
      </section>

      <!-- ============== AI 建议（下沉为说明性脚注） ============== -->
      <section v-if="schedule.aiSuggestion" class="drawer-section">
        <div class="drawer-section__title">
          <ElIcon><Document /></ElIcon>
          <span>AI 排班说明</span>
        </div>
        <p class="drawer-ai">{{ schedule.aiSuggestion }}</p>
      </section>

      <!-- ============== 操作区 ============== -->
      <footer class="drawer-foot">
        <ElButton
          type="primary"
          class="drawer-foot__btn"
          :disabled="schedule.status === '停诊'"
          @click="onLeave"
        >
          申请请假
        </ElButton>
        <p class="drawer-foot__hint">
          提交后由管理员审批，AI 将自动生成替班调整方案
        </p>
      </footer>
    </div>
  </ElDrawer>
</template>

<style scoped>
.drawer-inner {
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  font-family: var(--sched-font-body);
  color: var(--sched-ink);
  height: 100%;
  overflow-y: auto;
}

/* 头部 */
.drawer-head {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--sched-line);
}

.drawer-head__row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.drawer-head__date {
  display: flex;
  align-items: center;
  gap: 14px;
}

.drawer-head__day {
  font-family: var(--sched-font-display);
  font-size: 44px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1;
}

.drawer-head__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drawer-head__meta strong {
  font-family: var(--sched-font-mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--sched-ink-soft);
}

.drawer-head__meta small {
  font-size: 12px;
  color: var(--sched-ink-mute);
}

.drawer-head__today {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--sched-today);
  color: #FFFFFF;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

/* 医生姓名行：紧跟日期下方，核心信息前置 */
.drawer-head__doctor {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--sched-surface-alt);
  border-radius: 10px;
}

.drawer-head__doctor-icon {
  font-size: 18px;
  color: var(--sched-primary);
}

.drawer-head__doctor-name {
  font-family: var(--sched-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--sched-ink);
  letter-spacing: -0.01em;
}

.drawer-head__doctor-title {
  font-size: 11px;
  font-weight: 500;
  color: var(--sched-ink-mute);
  padding: 2px 8px;
  background: var(--sched-surface);
  border-radius: 4px;
  letter-spacing: 0.02em;
}

/* 通用 section */
.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drawer-section__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sched-ink-soft);
  letter-spacing: 0.04em;
}

.drawer-section__title :deep(.el-icon) {
  font-size: 14px;
  color: var(--sched-ink-mute);
}

.drawer-section__title--warn {
  color: var(--sched-today);
}

.drawer-section__title--warn :deep(.el-icon) {
  color: var(--sched-today);
}

/* 标签 */
.drawer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ============================================================
   出诊时段：时间轴 mini 可视化
   一天 6:00-22:00 共 16h，把当前班次画在高亮色块上
   ============================================================ */
.time-axis {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.time-axis__ruler {
  display: flex;
  justify-content: space-between;
  font-family: var(--sched-font-mono);
  font-size: 10px;
  color: var(--sched-ink-mute);
  padding: 0 2px;
}

.time-axis__track {
  position: relative;
  height: 28px;
  background: var(--sched-surface-alt);
  border-radius: 6px;
  overflow: hidden;
}

.time-axis__bg {
  position: absolute;
  top: 0;
  bottom: 0;
  background: rgba(70, 111, 160, 0.08);
}

.time-axis__active {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: #FFFFFF;
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.2) inset;
}

.time-axis__label {
  letter-spacing: 0.04em;
}

.time-axis__hint {
  margin: 0;
  font-family: var(--sched-font-mono);
  font-size: 11px;
  color: var(--sched-ink-mute);
  text-align: center;
}

/* ============================================================
   排班调整记录：警示色调
   ============================================================ */
.drawer-modified {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  background: rgba(194, 113, 10, 0.06);
  border-left: 3px solid var(--sched-today);
  border-radius: 0 8px 8px 0;
}

.drawer-modified__icon {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--sched-today);
  margin-top: 1px;
}

.drawer-modified__text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sched-ink-soft);
}

/* 号源块 */
.quota-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quota-block__numbers {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-family: var(--sched-font-mono);
}

.quota-block__used {
  font-size: 36px;
  font-weight: 700;
  color: var(--sched-ink);
  line-height: 1;
}

.quota-block__used[data-tight] {
  color: var(--sched-danger);
}

.quota-block__sep {
  font-size: 20px;
  color: var(--sched-ink-mute);
  margin: 0 4px;
}

.quota-block__total {
  font-size: 20px;
  color: var(--sched-ink-soft);
  font-weight: 500;
}

.quota-block__label {
  font-size: 12px;
  color: var(--sched-ink-soft);
  line-height: 1.6;
}

.quota-block__label strong {
  color: var(--sched-ink);
  font-weight: 600;
}

.quota-block__bar {
  width: 100%;
  height: 6px;
  background: var(--sched-surface-alt);
  border-radius: 999px;
  overflow: hidden;
}

.quota-block__bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 280ms cubic-bezier(0.2, 0, 0, 1);
}

/* 详情描述 */
.drawer-desc {
  font-size: 13px;
}

.drawer-desc__icon {
  margin-right: 6px;
  color: var(--sched-ink-mute);
}

.drawer-status-dot {
  display: inline-block;
  margin-right: 6px;
  font-weight: 700;
}

/* AI 建议 */
.drawer-ai {
  margin: 0;
  padding: 12px 14px;
  background: var(--sched-surface-alt);
  border-left: 3px solid var(--sched-line-strong);
  border-radius: 0 8px 8px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--sched-ink-soft);
}

/* 操作区 */
.drawer-foot {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid var(--sched-line);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drawer-foot__btn {
  width: 100%;
  height: 42px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 10px;
  background: var(--sched-primary);
  border-color: var(--sched-primary);
}

.drawer-foot__btn:hover:not(:disabled) {
  background: var(--color-primary-strong, #006ce6);
  border-color: var(--color-primary-strong, #006ce6);
}

.drawer-foot__btn:disabled {
  background: var(--sched-line-strong);
  border-color: var(--sched-line-strong);
}

.drawer-foot__hint {
  margin: 0;
  text-align: center;
  font-size: 11px;
  color: var(--sched-ink-mute);
  line-height: 1.5;
}
</style>
