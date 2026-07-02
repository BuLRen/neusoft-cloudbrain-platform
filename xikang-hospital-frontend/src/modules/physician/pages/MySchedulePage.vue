<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { ElButton, ElEmpty, ElIcon, ElMessage } from 'element-plus'
import {
  Aim,
  ArrowLeft,
  ArrowRight,
  Calendar as CalendarIcon,
  Clock,
  Refresh,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import type { DoctorSchedule, LeaveRequest } from '@/shared/types/schedule'
import { scheduleApi } from '@/shared/api/modules/schedule'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import {
  formatDateIso,
  isTodayIso,
  resolveScheduleTypeColor,
  startOfWeek,
  weekRange,
} from '../constants/scheduleStyle'
import ScheduleWeekGrid from '../components/schedule/ScheduleWeekGrid.vue'
import ScheduleMonthCalendar from '../components/schedule/ScheduleMonthCalendar.vue'
import ScheduleDetailDrawer from '../components/schedule/ScheduleDetailDrawer.vue'
import LeaveApplyDialog from '../components/schedule/LeaveApplyDialog.vue'

const authStore = useAuthStore()

type ViewMode = 'week' | 'month'

const viewMode = ref<ViewMode>('week')
const today = new Date()
const weekStart = ref(startOfWeek(today)) // 当前周的周一
const monthCursor = ref<{ year: number; month: number }>({
  year: today.getFullYear(),
  month: today.getMonth(),
})

const schedules = shallowRef<DoctorSchedule[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// 详情抽屉
const drawerOpen = ref(false)
const selectedSchedule = ref<DoctorSchedule | null>(null)

// 请假对话框
const leaveDialogOpen = ref(false)
const leaveTargetSchedule = ref<DoctorSchedule | null>(null)

// 角色：管理员看全院（暂用日历占位），医生/医技等看本人
const physicianId = computed(() => authStore.employeeId)
const isPatient = computed(() => authStore.role === 'patient')
const canViewOwnSchedule = computed(() => physicianId.value != null && !isPatient.value)

// 周视图当前周的 7 天 ISO
const currentWeekDates = computed(() => weekRange(weekStart.value))

// 月视图的查询范围（含跨月补白，覆盖 42 格）
const monthQueryRange = computed(() => {
  const first = new Date(monthCursor.value.year, monthCursor.value.month, 1)
  const start = startOfWeek(first)
  const end = new Date(start)
  end.setDate(start.getDate() + 41)
  return { startDate: formatDateIso(start), endDate: formatDateIso(end) }
})

// 周视图的查询范围（多取一天缓冲，避免边界遗漏）
const weekQueryRange = computed(() => ({
  startDate: currentWeekDates.value[0],
  endDate: currentWeekDates.value[6],
}))

// 当前查询范围（根据视图）
const activeQueryRange = computed(() =>
  viewMode.value === 'week' ? weekQueryRange.value : monthQueryRange.value,
)

// 周视图表头显示
const weekRangeLabel = computed(() => {
  const start = currentWeekDates.value[0]
  const end = currentWeekDates.value[6]
  return `${start} – ${end}`
})

// 月视图表头
const monthLabel = computed(() => {
  return `${monthCursor.value.year}年 ${monthCursor.value.month + 1}月`
})

// 摘要统计
const summary = computed(() => {
  const list = schedules.value
  const total = list.length
  const byLevel = new Map<string, number>()
  let totalQuota = 0
  let totalUsed = 0
  for (const s of list) {
    const level = s.registLevelName || '普通号'
    byLevel.set(level, (byLevel.get(level) ?? 0) + 1)
    totalQuota += s.totalQuota ?? 0
    totalUsed += s.usedQuota ?? 0
  }
  return {
    total,
    byLevel: Array.from(byLevel.entries()),
    totalQuota,
    totalUsed,
    available: Math.max(0, totalQuota - totalUsed),
  }
})

// "下一个班次"——从现在起最近的一个未结束班次（焦点卡片用）
const nextSchedule = computed<DoctorSchedule | null>(() => {
  const now = formatDateIso(new Date())
  const today = new Date(now + 'T00:00:00').getTime()
  // 今天及以后的班次，按日期+时段排序，取第一个
  const upcoming = schedules.value
    .filter((s) => {
      const sTime = new Date(s.workDate + 'T00:00:00').getTime()
      return sTime >= today && s.status !== '停诊'
    })
    .sort((a, b) => {
      if (a.workDate !== b.workDate) return a.workDate < b.workDate ? -1 : 1
      // 时段排序：上午<下午<晚上
      const order = { '上午': 0, '下午': 1, '晚上': 2 } as Record<string, number>
      return (order[a.timeSlot] ?? 9) - (order[b.timeSlot] ?? 9)
    })
  return upcoming[0] ?? null
})

// 距离下一个班次还有多久（人类可读）
const nextScheduleCountdown = computed(() => {
  const s = nextSchedule.value
  if (!s) return ''
  const target = new Date(s.workDate + 'T00:00:00').getTime()
  const today = new Date(formatDateIso(new Date()) + 'T00:00:00').getTime()
  const days = Math.round((target - today) / 86400000)
  if (days === 0) return '今天 ' + s.timeSlot
  if (days === 1) return '明天 ' + s.timeSlot
  if (days < 7) return `${days} 天后`
  return s.workDate
})

// 本周工作日负荷（周一到周五各多少班次，用于负荷条）
const weekdayLoad = computed(() => {
  const counts = [0, 0, 0, 0, 0, 0, 0] // 周一到周日
  for (const s of schedules.value) {
    if (viewMode.value !== 'week') continue
    const idx = currentWeekDates.value.indexOf(s.workDate)
    if (idx >= 0) counts[idx]++
  }
  return counts
})

async function loadSchedules() {
  if (!canViewOwnSchedule.value) return
  if (!physicianId.value) return
  loading.value = true
  error.value = null
  try {
    const { startDate, endDate } = activeQueryRange.value
    schedules.value = await scheduleApi.byPhysician(physicianId.value, startDate, endDate)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载排班失败'
    schedules.value = []
  } finally {
    loading.value = false
  }
}

// 导航
function prevWeek() {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() - 7)
  weekStart.value = d
}

function nextWeek() {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + 7)
  weekStart.value = d
}

function goThisWeek() {
  weekStart.value = startOfWeek(new Date())
}

function prevMonth() {
  const { year, month } = monthCursor.value
  if (month === 0) monthCursor.value = { year: year - 1, month: 11 }
  else monthCursor.value = { year, month: month - 1 }
}

function nextMonth() {
  const { year, month } = monthCursor.value
  if (month === 11) monthCursor.value = { year: year + 1, month: 0 }
  else monthCursor.value = { year, month: month + 1 }
}

function goThisMonth() {
  const now = new Date()
  monthCursor.value = { year: now.getFullYear(), month: now.getMonth() }
}

// 选中班次 → 打开抽屉
function openDetail(schedule: DoctorSchedule) {
  selectedSchedule.value = schedule
  drawerOpen.value = true
}

// 月视图点日期 → 找到当天第一个班次进抽屉；无班次则提示
function openDateSchedules(iso: string) {
  const list = schedules.value.filter((s) => s.workDate === iso)
  if (!list.length) {
    ElMessage.info('该日无排班')
    return
  }
  // 优先上午，否则取第一个
  const morning = list.find((s) => s.timeSlot === '上午') ?? list[0]
  openDetail(morning)
}

// 抽屉触发请假
function onApplyLeave(schedule: DoctorSchedule) {
  leaveTargetSchedule.value = schedule
  drawerOpen.value = false
  leaveDialogOpen.value = true
}

function onLeaveSubmitted(_leave: LeaveRequest) {
  // 端口预留：未来这里可以刷新"我的请假列表"或触发通知
  // 当前后端 approve 时会调用 Dify 工作流，前端无需额外处理
  void loadSchedules()
}

function switchView(mode: ViewMode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
}

// 视图切换或日期范围变化 → 重新加载
watch([viewMode, weekStart, monthCursor], () => {
  void loadSchedules()
}, { deep: true })

onMounted(() => {
  void loadSchedules()
})
</script>

<template>
  <div class="my-schedule-page">
    <!-- 系统统一页头 -->
    <PageHeader
      :title="`${authStore.realName || '医生'} · 我的排班`"
      :description="viewMode === 'week' ? `本周 ${weekRangeLabel}` : monthLabel"
      eyebrow="门诊排班"
    >
      <template #actions>
        <ElButton :icon="Refresh" :loading="loading" @click="loadSchedules">刷新</ElButton>
      </template>
    </PageHeader>

    <!-- 患者角色：无排班 -->
    <GlassCard v-if="isPatient" class="ms-empty-card">
      <ElEmpty description="患者账号无排班信息" />
    </GlassCard>

    <!-- 无 employeeId（兜底） -->
    <GlassCard v-else-if="!canViewOwnSchedule" class="ms-empty-card">
      <ElEmpty description="未识别到您的员工身份，无法查看排班" />
    </GlassCard>

    <!-- 主内容 -->
    <template v-else>
      <!-- 顶部双卡片：焦点（下一个班次）+ 统计 -->
      <section class="ms-top-row">
        <!-- 焦点卡片：下一个班次（永远存在的视觉重心） -->
        <GlassCard class="ms-focus">
          <div class="ms-focus__head">
            <span class="ms-focus__label">
              <ElIcon><Clock /></ElIcon>
              下一个班次
            </span>
            <span
              v-if="nextSchedule"
              class="ms-focus__countdown"
              :data-urgent="nextScheduleCountdown.includes('今天') || undefined"
            >
              {{ nextScheduleCountdown }}
            </span>
          </div>

          <div v-if="nextSchedule" class="ms-focus__body">
            <div class="ms-focus__main">
              <span
                class="ms-focus__type-tag"
                :style="{
                  background: resolveScheduleTypeColor(nextSchedule.registLevelName).bg,
                  color: resolveScheduleTypeColor(nextSchedule.registLevelName).text,
                }"
              >
                {{ nextSchedule.registLevelName || '普通号' }}
              </span>
              <h2 class="ms-focus__dept">{{ nextSchedule.departmentName || '门诊' }}</h2>
              <p class="ms-focus__time">
                {{ nextSchedule.workDate }} · {{ nextSchedule.timeSlot }}
              </p>
            </div>
            <div class="ms-focus__quota">
              <div class="ms-focus__quota-num">
                <strong>{{ nextSchedule.availableQuota }}</strong>
                <span>/ {{ nextSchedule.totalQuota }}</span>
              </div>
              <p class="ms-focus__quota-label">剩余号源</p>
            </div>
          </div>

          <div v-else class="ms-focus__empty">
            <ElIcon class="ms-focus__empty-icon"><CalendarIcon /></ElIcon>
            <p>当前时段暂无即将到来的班次</p>
          </div>
        </GlassCard>

        <!-- 统计卡片网格：4 个数据卡 -->
        <div class="ms-stats">
          <GlassCard class="ms-stat">
            <div class="ms-stat__icon ms-stat__icon--blue">
              <ElIcon><CalendarIcon /></ElIcon>
            </div>
            <div class="ms-stat__body">
              <span class="ms-stat__num">{{ summary.total }}</span>
              <span class="ms-stat__label">{{ viewMode === 'week' ? '本周班次' : '本月班次' }}</span>
            </div>
          </GlassCard>

          <GlassCard class="ms-stat">
            <div class="ms-stat__icon ms-stat__icon--green">
              <ElIcon><User /></ElIcon>
            </div>
            <div class="ms-stat__body">
              <span class="ms-stat__num">{{ summary.totalUsed }}</span>
              <span class="ms-stat__label">已挂号</span>
            </div>
          </GlassCard>

          <GlassCard class="ms-stat">
            <div class="ms-stat__icon ms-stat__icon--purple">
              <ElIcon><TrendCharts /></ElIcon>
            </div>
            <div class="ms-stat__body">
              <span class="ms-stat__num">{{ summary.totalQuota }}</span>
              <span class="ms-stat__label">总号源</span>
            </div>
          </GlassCard>

          <GlassCard class="ms-stat">
            <div class="ms-stat__icon ms-stat__icon--orange">
              <ElIcon><Aim /></ElIcon>
            </div>
            <div class="ms-stat__body">
              <span class="ms-stat__num">{{ summary.available }}</span>
              <span class="ms-stat__label">剩余号源</span>
            </div>
          </GlassCard>
        </div>
      </section>

      <!-- 排班主卡片：视图切换 + 日期导航 + 网格 全部装在一个 GlassCard 里 -->
      <GlassCard class="ms-main">
        <!-- 卡片头：标题 + 视图切换 + 日期导航 -->
        <header class="ms-main__head">
          <div class="ms-main__title-group">
            <h2 class="ms-main__title">排班表</h2>
            <div class="ms-main__view-switch" role="tablist" aria-label="视图切换">
              <button
                type="button"
                role="tab"
                class="ms-main__tab"
                :class="{ 'is-active': viewMode === 'week' }"
                :aria-selected="viewMode === 'week'"
                @click="switchView('week')"
              >周视图</button>
              <button
                type="button"
                role="tab"
                class="ms-main__tab"
                :class="{ 'is-active': viewMode === 'month' }"
                :aria-selected="viewMode === 'month'"
                @click="switchView('month')"
              >月视图</button>
            </div>
          </div>

          <div class="ms-main__nav">
            <button
              type="button"
              class="ms-main__nav-btn"
              :aria-label="viewMode === 'week' ? '上一周' : '上一月'"
              @click="viewMode === 'week' ? prevWeek() : prevMonth()"
            >
              <ElIcon><ArrowLeft /></ElIcon>
            </button>
            <span class="ms-main__nav-label">
              {{ viewMode === 'week' ? weekRangeLabel : monthLabel }}
            </span>
            <button
              type="button"
              class="ms-main__nav-btn"
              :aria-label="viewMode === 'week' ? '下一周' : '下一月'"
              @click="viewMode === 'week' ? nextWeek() : nextMonth()"
            >
              <ElIcon><ArrowRight /></ElIcon>
            </button>
            <button
              type="button"
              class="ms-main__today"
              @click="viewMode === 'week' ? goThisWeek() : goThisMonth()"
            >
              {{ viewMode === 'week' ? '本周' : '本月' }}
            </button>
          </div>
        </header>

        <!-- 卡片体 -->
        <div class="ms-main__body">
          <!-- 错误状态 -->
          <div v-if="error" class="ms-main__state">
            <ElEmpty :description="error">
              <ElButton @click="loadSchedules">重试</ElButton>
            </ElEmpty>
          </div>

          <!-- 加载骨架 -->
          <div v-else-if="loading && !schedules.length" class="ms-skel-grid">
            <div
              v-for="i in 14"
              :key="i"
              class="ms-skel-cell"
            ></div>
          </div>

          <!-- 周视图 -->
          <template v-else-if="viewMode === 'week'">
            <ScheduleWeekGrid
              :week-dates="currentWeekDates"
              :schedules="schedules"
              :selected-schedule-id="selectedSchedule?.id ?? null"
              @select="openDetail"
            />
            <p v-if="!schedules.length" class="ms-main__hint">
              <ElIcon><CalendarIcon /></ElIcon>
              本周暂无排班
            </p>
          </template>

          <!-- 月视图 -->
          <template v-else>
            <ScheduleMonthCalendar
              :year="monthCursor.year"
              :month="monthCursor.month"
              :schedules="schedules"
              @select-date="openDateSchedules"
            />
          </template>
        </div>
      </GlassCard>
    </template>

    <!-- 班次详情抽屉 -->
    <ScheduleDetailDrawer
      v-model="drawerOpen"
      :schedule="selectedSchedule"
      :fallback-physician-name="authStore.realName"
      @apply-leave="onApplyLeave"
    />

    <!-- 请假对话框 -->
    <LeaveApplyDialog
      v-model="leaveDialogOpen"
      :schedule="leaveTargetSchedule"
      :physician-id="physicianId"
      @submitted="onLeaveSubmitted"
    />
  </div>
</template>

<style scoped>
/* ============================================================
   页面级 token：派生自全局 tokens.css，融入蓝色玻璃系统风
   字体回归 var(--font-sans)，靠字重 / 字号 / 字距 / tnum 做层次
   ============================================================ */
.my-schedule-page {
  --sched-font-display: var(--font-sans);
  --sched-font-body: var(--font-sans);
  --sched-font-mono: "JetBrains Mono", "SF Mono", ui-monospace, Menlo, Consolas, monospace;

  --sched-surface: rgba(255, 255, 255, 0.72);
  --sched-surface-strong: rgba(255, 255, 255, 0.92);
  --sched-surface-alt: rgba(248, 251, 255, 0.55);
  --sched-ink: var(--color-text, #102033);
  --sched-ink-soft: var(--color-text-muted, #5f7288);
  --sched-ink-mute: var(--color-text-soft, #8ba0b6);
  --sched-line: var(--color-border, rgba(70, 111, 160, 0.16));
  --sched-line-strong: var(--color-border-strong, rgba(70, 111, 160, 0.28));
  /* 排班表专用网格线：实色蓝灰（不到纯黑，避免刺眼），保持系统蓝色调性 */
  --sched-grid-line: rgba(30, 58, 95, 0.42);
  --sched-grid-line-soft: rgba(30, 58, 95, 0.16);
  --sched-primary: var(--color-primary, #1f8cff);
  --sched-primary-soft: var(--color-primary-soft, rgba(31, 140, 255, 0.14));
  --sched-today: var(--color-warning-strong, #c2710a);
  --sched-today-soft: var(--color-warning-soft, rgba(245, 159, 0, 0.12));
  --sched-success: var(--color-success, #20b486);
  --sched-danger: var(--color-danger, #ef4d5a);

  /* 各班次类型 stat 卡的色板：低饱和半透明，避免和系统主蓝打架 */
  --stat-blue:   rgba(31, 140, 255, 0.14);
  --stat-blue-i: #1f8cff;
  --stat-green:  rgba(32, 180, 134, 0.14);
  --stat-green-i: #16a37a;
  --stat-purple: rgba(122, 92, 255, 0.14);
  --stat-purple-i: #6b52e0;
  --stat-orange: rgba(245, 159, 0, 0.16);
  --stat-orange-i: #c2710a;

  color: var(--sched-ink);
  font-family: var(--sched-font-body);
  padding: 8px 28px 36px;
  font-feature-settings: "tnum" 1, "cv11" 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ============================================================
   顶部双列：焦点卡片 + 4 个统计小卡
   signature：永远存在的「下一个班次」焦点位 = 页面视觉重心
   ============================================================ */
.ms-top-row {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

/* —— 焦点卡：左大色块、右数据柱 —— */
.ms-focus {
  padding: 22px 26px !important;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 168px;
}

.ms-focus::before {
  /* 顶部内嵌渐变光带：让焦点卡比 stat 卡"重一点" */
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, var(--sched-primary) 0%, var(--stat-purple-i) 60%, var(--sched-today) 100%);
  opacity: 0.85;
}

.ms-focus__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ms-focus__label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sched-ink-soft);
  letter-spacing: 0.06em;
  text-transform: none;
}

.ms-focus__label :deep(.el-icon) {
  font-size: 15px;
  color: var(--sched-primary);
}

.ms-focus__countdown {
  font-family: var(--sched-font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--sched-ink-soft);
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--sched-surface-alt);
  border: 1px solid var(--sched-line);
  letter-spacing: 0.02em;
}

.ms-focus__countdown[data-urgent] {
  color: var(--sched-today);
  background: var(--sched-today-soft);
  border-color: var(--sched-today-soft);
}

.ms-focus__body {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  flex: 1;
}

.ms-focus__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.ms-focus__type-tag {
  align-self: flex-start;
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  letter-spacing: 0.02em;
  margin-bottom: 4px;
}

.ms-focus__dept {
  margin: 0;
  font-family: var(--sched-font-display);
  font-size: 26px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1.2;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ms-focus__time {
  margin: 0;
  font-family: var(--sched-font-mono);
  font-size: 12px;
  color: var(--sched-ink-mute);
  letter-spacing: 0.02em;
}

.ms-focus__quota {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

.ms-focus__quota-num {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-family: var(--sched-font-mono);
  justify-content: flex-end;
}

.ms-focus__quota-num strong {
  font-size: 38px;
  font-weight: 700;
  color: var(--sched-primary);
  line-height: 1;
  letter-spacing: -0.02em;
}

.ms-focus__quota-num span {
  font-size: 14px;
  color: var(--sched-ink-mute);
}

.ms-focus__quota-label {
  margin: 0;
  font-size: 11px;
  color: var(--sched-ink-mute);
  letter-spacing: 0.04em;
}

.ms-focus__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--sched-ink-mute);
  font-size: 13px;
}

.ms-focus__empty-icon {
  font-size: 36px;
  opacity: 0.4;
}

/* —— 统计小卡：2×2 网格 —— */
.ms-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ms-stat {
  padding: 16px 18px !important;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: transform 180ms cubic-bezier(0.2, 0, 0, 1);
}

.ms-stat:hover {
  transform: translateY(-2px);
}

.ms-stat__icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 19px;
}

.ms-stat__icon--blue   { background: var(--stat-blue);   color: var(--stat-blue-i); }
.ms-stat__icon--green  { background: var(--stat-green);  color: var(--stat-green-i); }
.ms-stat__icon--purple { background: var(--stat-purple); color: var(--stat-purple-i); }
.ms-stat__icon--orange { background: var(--stat-orange); color: var(--stat-orange-i); }

.ms-stat__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ms-stat__num {
  font-family: var(--sched-font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--sched-ink);
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.ms-stat__label {
  font-size: 11px;
  color: var(--sched-ink-mute);
  letter-spacing: 0.04em;
}

/* ============================================================
   主排班卡：内嵌工具栏 + 视图区，避免「卡片外面浮一 toolbar」的破碎感
   ============================================================ */
.ms-main {
  padding: 20px 24px 24px !important;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ms-main__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.ms-main__title-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.ms-main__title {
  margin: 0;
  font-family: var(--sched-font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--sched-ink);
  letter-spacing: -0.01em;
}

.ms-main__view-switch {
  display: inline-flex;
  padding: 3px;
  background: var(--sched-surface-alt);
  border-radius: 9px;
  border: 1px solid var(--sched-line);
}

.ms-main__tab {
  padding: 5px 14px;
  border: none;
  background: transparent;
  color: var(--sched-ink-mute);
  font-family: var(--sched-font-body);
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 140ms cubic-bezier(0.2, 0, 0, 1);
}

.ms-main__tab:hover:not(.is-active) {
  color: var(--sched-ink);
}

.ms-main__tab.is-active {
  background: var(--sched-surface-strong);
  color: var(--sched-ink);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(70, 111, 160, 0.10);
}

.ms-main__nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ms-main__nav-btn {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--sched-line);
  background: transparent;
  color: var(--sched-ink-soft);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 140ms cubic-bezier(0.2, 0, 0, 1);
}

.ms-main__nav-btn:hover {
  border-color: var(--sched-line-strong);
  color: var(--sched-ink);
  background: var(--sched-surface);
}

.ms-main__nav-label {
  font-family: var(--sched-font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--sched-ink);
  padding: 0 10px;
  min-width: 88px;
  text-align: center;
  letter-spacing: 0.02em;
}

.ms-main__today {
  padding: 5px 12px;
  border: 1px solid var(--sched-line);
  border-radius: 8px;
  background: transparent;
  color: var(--sched-ink-soft);
  font-family: var(--sched-font-body);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  margin-left: 6px;
  transition: all 140ms cubic-bezier(0.2, 0, 0, 1);
}

.ms-main__today:hover {
  border-color: var(--sched-primary);
  color: var(--sched-primary);
  background: var(--sched-primary-soft);
}

.ms-main__body {
  min-height: 280px;
  display: flex;
  flex-direction: column;
}

.ms-main__state {
  padding: 36px 0;
  display: flex;
  justify-content: center;
}

.ms-main__hint {
  margin: 18px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sched-ink-mute);
}

.ms-main__hint :deep(.el-icon) {
  font-size: 14px;
}

/* ============================================================
   加载骨架（保持与周视图同结构）
   ============================================================ */
.ms-skel-grid {
  display: grid;
  grid-template-columns: 72px repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.ms-skel-cell {
  min-height: 108px;
  border-radius: 10px;
  background: linear-gradient(90deg, var(--sched-surface-alt) 0%, rgba(255, 255, 255, 0.85) 50%, var(--sched-surface-alt) 100%);
  background-size: 200% 100%;
  animation: ms-shimmer 1.4s ease-in-out infinite;
}

@keyframes ms-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ============================================================
   空态卡（患者 / 无 employeeId）
   ============================================================ */
.ms-empty-card {
  padding: 48px 24px !important;
  display: flex;
  justify-content: center;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1080px) {
  .ms-top-row {
    grid-template-columns: 1fr;
  }
  .ms-focus__dept {
    font-size: 22px;
  }
}

@media (max-width: 720px) {
  .my-schedule-page {
    padding: 8px 16px 28px;
  }
  .ms-focus {
    padding: 18px 18px !important;
  }
  .ms-focus__quota-num strong { font-size: 30px; }
  .ms-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
  .ms-stat { padding: 12px 14px !important; gap: 10px; }
  .ms-stat__num { font-size: 18px; }
  .ms-main { padding: 16px 16px 18px !important; }
  .ms-main__head {
    flex-direction: column;
    align-items: stretch;
  }
  .ms-main__nav {
    justify-content: center;
  }
  .ms-skel-grid {
    grid-template-columns: 48px repeat(7, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .ms-skel-cell { animation: none; }
  .ms-stat { transition: none; }
  .ms-stat:hover { transform: none; }
  .ms-main__tab,
  .ms-main__nav-btn,
  .ms-main__today { transition: none; }
}
</style>
