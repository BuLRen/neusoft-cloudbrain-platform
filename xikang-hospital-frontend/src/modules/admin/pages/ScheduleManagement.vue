<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElBadge,
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { scheduleApi } from '@/shared/api/modules/schedule'
import type {
  SchedulePlan,
  DoctorSchedule,
  ScheduleAdjustRequest,
  CalendarDay,
  AiGenerateTaskView,
} from '@/shared/types/schedule'
import { registrationApi } from '@/shared/api/modules/registration'
import type { DepartmentOption } from '@/shared/types/registration'
import { useAuthStore } from '@/app/stores/auth'
import type { DoctorInfo } from '@/shared/api/modules/registration'

function pad2(value: number) {
  return String(value).padStart(2, '0')
}

function formatLocalDate(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function formatLocalMonth(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

// 状态
const loading = ref(false)
const generating = ref(false)
const authStore = useAuthStore()
const departments = ref<DepartmentOption[]>([])
// 当前操作的医生下拉（按选中科室动态拉取）
const scheduleDoctors = ref<DoctorInfo[]>([])
// 当前登录用户作为操作人 ID
const currentOperatorId = computed(() => Number(authStore.userId) || 1)

//筛选条件
const filter = reactive({
  departmentId: undefined as number | undefined,
  month: formatLocalMonth(new Date()),
})

const detailFilter = reactive({
  keyword: '',
  workDate: '',
  timeSlot: '' as '' | DoctorSchedule['timeSlot'],
  status: '' as '' | DoctorSchedule['status'],
})

// 排班计划
const currentPlan = ref<SchedulePlan | null>(null)
const planSchedules = ref<DoctorSchedule[]>([])

// 日历数据
const calendarData = ref<CalendarDay[]>([])

// 待确认调整
const pendingAdjusts = ref<ScheduleAdjustRequest[]>([])
const selectedAdjust = ref<ScheduleAdjustRequest | null>(null)

// 弹窗状态
const editScheduleDialogVisible = ref(false)
const adjustConfirmDialogVisible = ref(false)
const editingScheduleId = ref<number | null>(null)
const editScheduleDepartmentId = ref<number | undefined>()
const editScheduleForm = reactive({
  physicianId: undefined as number | undefined,
  physicianName: '',
  workDate: '',
  timeSlot: '上午' as '上午' | '下午' | '晚上',
  totalQuota: 30,
  status: '正常' as '正常' | '停诊' | '满诊' | '替班',
  remark: '',
})
const editScheduleDialogTitle = computed(() => (editingScheduleId.value ? '编辑排班' : '新增排班'))

// 调整确认表单
const adjustConfirmForm = reactive({
  confirmedBy: 1,
  remark: '',
})

// 同步当前操作人 ID（保存调整时使用）
function syncCurrentOperator() {
  adjustConfirmForm.confirmedBy = currentOperatorId.value
}

//统计数据
const statistics = computed(() => ({
  totalSchedules: planSchedules.value.length,
  totalQuota: planSchedules.value.reduce((sum, s) => sum + s.totalQuota, 0),
  usedQuota: planSchedules.value.reduce((sum, s) => sum + s.usedQuota, 0),
  pendingAdjusts: pendingAdjusts.value.length,
}))

const filteredPlanSchedules = computed(() => {
  const keyword = detailFilter.keyword.trim().toLowerCase()

  return planSchedules.value.filter((schedule) => {
    const matchesKeyword = !keyword || [
      schedule.physicianName,
      schedule.physicianTitle,
      schedule.departmentName,
      schedule.aiSuggestion,
      schedule.modifyRemark,
    ].some((value) => (value || '').toLowerCase().includes(keyword))

    const matchesDate = !detailFilter.workDate || schedule.workDate === detailFilter.workDate
    const matchesTimeSlot = !detailFilter.timeSlot || schedule.timeSlot === detailFilter.timeSlot
    const matchesStatus = !detailFilter.status || schedule.status === detailFilter.status

    return matchesKeyword && matchesDate && matchesTimeSlot && matchesStatus
  })
})

function resetDetailFilter() {
  detailFilter.keyword = ''
  detailFilter.workDate = ''
  detailFilter.timeSlot = ''
  detailFilter.status = ''
}

// 生成日历日期
const calendarDates = computed(() => {
  const [year, month] = filter.month.split('-').map(Number)
  const lastDay = new Date(year, month, 0)
  const dates: { date: Date; day: number; weekday: string }[] = []

  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(year, month - 1, d)
    const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
    dates.push({ date, day: d, weekday })
  }

  return dates
})

// 获取日历某天的数据
function getDayData(date: Date): CalendarDay | undefined {
  const dateStr = formatLocalDate(date)
  return calendarData.value.find((d) => d.date === dateStr)
}

// 加载科室数据
async function loadDepartments() {
  departments.value = await registrationApi.departments()
}

// 加载科室下医生列表
async function loadScheduleDoctors(departmentId?: number) {
  if (!departmentId) {
    scheduleDoctors.value = []
    return
  }
  try {
    scheduleDoctors.value = await registrationApi.getDoctorsByDepartment(departmentId)
  } catch {
    scheduleDoctors.value = []
  }
}

// 加载排班计划
async function loadPlan() {
  if (!filter.departmentId || !filter.month) {
    currentPlan.value = null
    planSchedules.value = []
    return
  }

  const plans = await scheduleApi.plans({
    departmentId: filter.departmentId,
    month: filter.month,
  })

  if (plans.length > 0) {
    currentPlan.value = plans[0]
    planSchedules.value = await scheduleApi.planSchedules(plans[0].id)
    calendarData.value = await scheduleApi.calendar(filter.departmentId, filter.month)
  } else {
    currentPlan.value = null
    planSchedules.value = []
    calendarData.value = []
  }
}

// 加载待确认调整
async function loadPendingAdjusts() {
  pendingAdjusts.value = await scheduleApi.pendingAdjusts()
}

// 发布计划
async function publishPlan() {
  if (!currentPlan.value) {
    return
  }

  await scheduleApi.publishPlan(currentPlan.value.id, currentOperatorId.value)
  ElMessage.success('排班已发布')
  await loadPlan()
}

// AI 异步任务：横幅 + 轮询
const aiTask = ref<AiGenerateTaskView | null>(null)
let aiTaskPollingTimer: ReturnType<typeof setInterval> | null = null

function stopAiTaskPolling() {
  if (aiTaskPollingTimer) {
    clearInterval(aiTaskPollingTimer)
    aiTaskPollingTimer = null
  }
}

function isAiTaskTerminal(task: AiGenerateTaskView | null): boolean {
  if (!task) return true
  return task.status === 'success' || task.status === 'failed' || task.status === 'cancelled'
}

async function refreshAiTaskOnce() {
  if (!filter.departmentId || !filter.month) {
    return null
  }
  try {
    const view = await scheduleApi.getActiveAiTask({
      operatorId: currentOperatorId.value,
      departmentId: filter.departmentId,
      month: filter.month,
    })
    aiTask.value = view
    return view
  } catch (error) {
    // 轮询失败不打扰用户
    return null
  }
}

async function startAiTaskPolling() {
  stopAiTaskPolling()
  // 先立刻拉一次，再 2s 轮询
  await refreshAiTaskOnce()
  aiTaskPollingTimer = setInterval(async () => {
    const view = await refreshAiTaskOnce()
    if (isAiTaskTerminal(view)) {
      stopAiTaskPolling()
      if (view?.status === 'success') {
        ElMessage.success(view.message || 'AI 排班已生成')
        await loadPlan()
        await loadPendingAdjusts()
      } else if (view?.status === 'failed') {
        ElMessage.error(view.message || 'AI 排班生成失败')
      } else if (view?.status === 'cancelled') {
        ElMessage.info('已取消 AI 排班生成')
      }
    }
  }, 2000)
}

async function cancelAiTask() {
  if (!filter.departmentId || !filter.month) return
  try {
    await scheduleApi.cancelActiveAiTask({
      operatorId: currentOperatorId.value,
      departmentId: filter.departmentId,
      month: filter.month,
    })
  } catch (error) {
    // 忽略，后端可能已经结束
  }
  await refreshAiTaskOnce()
  stopAiTaskPolling()
}

function dismissAiTaskBanner() {
  aiTask.value = null
}

// AI生成排班（提交即返回，后台跑）
async function generateByAI() {
  if (!filter.departmentId || !filter.month) {
    ElMessage.warning('请先选择科室和月份')
    return
  }

  try {
    generating.value = true
    const view = await scheduleApi.generatePlanByAI({
      departmentId: filter.departmentId,
      month: filter.month,
      operatorId: currentOperatorId.value,
    })
    aiTask.value = view
    ElMessage.info('AI 排班已提交，正在后台生成')
    await startAiTaskPolling()
  } catch (error) {
    // 已经在拦截器里 toast 过
  } finally {
    generating.value = false
  }
}

//打开排班编辑
async function openEditSchedule(schedule?: DoctorSchedule) {
  if (schedule) {
    editingScheduleId.value = schedule.id
    editScheduleDepartmentId.value = schedule.departmentId
    editScheduleForm.physicianId = schedule.physicianId
    editScheduleForm.physicianName = schedule.physicianName || ''
    editScheduleForm.workDate = schedule.workDate || ''
    editScheduleForm.timeSlot = schedule.timeSlot
    editScheduleForm.totalQuota = schedule.totalQuota
    editScheduleForm.status = schedule.status
    editScheduleForm.remark = schedule.modifyRemark || schedule.aiSuggestion || ''
  } else {
    editingScheduleId.value = null
    editScheduleDepartmentId.value = filter.departmentId
    editScheduleForm.physicianId = undefined
    editScheduleForm.physicianName = ''
    editScheduleForm.workDate = `${filter.month}-01`
    editScheduleForm.timeSlot = '上午'
    editScheduleForm.totalQuota = 30
    editScheduleForm.status = '正常'
    editScheduleForm.remark = ''
  }

  await loadScheduleDoctors(editScheduleDepartmentId.value)
  editScheduleDialogVisible.value = true
}

// 保存排班
async function saveSchedule() {
  if (!editScheduleDepartmentId.value || !editScheduleForm.physicianId || !editScheduleForm.workDate) {
    ElMessage.warning('请填写完整信息')
    return
  }

  if (!editingScheduleId.value && !currentPlan.value) {
    ElMessage.warning('请先生成或选择排班计划')
    return
  }

  // 兜底：若姓名未填则从下拉项中反查
  if (!editScheduleForm.physicianName) {
    const doc = scheduleDoctors.value.find((d) => d.id === editScheduleForm.physicianId)
    editScheduleForm.physicianName = doc?.realname || ''
  }

  if (editingScheduleId.value) {
    await scheduleApi.updateSchedule(editingScheduleId.value, {
      physicianId: editScheduleForm.physicianId,
      workDate: editScheduleForm.workDate,
      timeSlot: editScheduleForm.timeSlot,
      totalQuota: editScheduleForm.totalQuota,
      status: editScheduleForm.status,
      aiSuggestion: editScheduleForm.remark,
      operatorId: currentOperatorId.value,
      remark: editScheduleForm.remark,
    })

    ElMessage.success('排班已更新')
  } else {
    await scheduleApi.createSchedule({
      planId: currentPlan.value!.id,
      physicianId: editScheduleForm.physicianId,
      departmentId: editScheduleDepartmentId.value,
      workDate: editScheduleForm.workDate,
      timeSlot: editScheduleForm.timeSlot,
      totalQuota: editScheduleForm.totalQuota,
      status: editScheduleForm.status,
      aiSuggestion: editScheduleForm.remark,
    })

    ElMessage.success('排班已保存')
  }

  editScheduleDialogVisible.value = false
  editingScheduleId.value = null
  await loadPlan()
}

// 停诊
async function stopSchedule(scheduleId: number) {
  await scheduleApi.stopSchedule(scheduleId, currentOperatorId.value, '管理员停诊')
  ElMessage.success('已停诊')
  await loadPlan()
}

// 恢复出诊
async function resumeSchedule(scheduleId: number) {
  await scheduleApi.resumeSchedule(scheduleId, currentOperatorId.value)
  ElMessage.success('已恢复')
  await loadPlan()
}

// 打开调整确认弹窗
function openAdjustConfirm(adjust: ScheduleAdjustRequest) {
  selectedAdjust.value = adjust
  syncCurrentOperator()
  adjustConfirmForm.remark = ''
  adjustConfirmDialogVisible.value = true
}

// 确认调整
async function confirmAdjust() {
  if (!selectedAdjust.value) return

  await scheduleApi.confirmAdjust(selectedAdjust.value.id, adjustConfirmForm.confirmedBy, adjustConfirmForm.remark)
  ElMessage.success('调整已确认')
  adjustConfirmDialogVisible.value = false
  await loadPendingAdjusts()
  await loadPlan()
}

// 驳回调整
async function rejectAdjust() {
  if (!selectedAdjust.value) return

  await scheduleApi.rejectAdjust(selectedAdjust.value.id, adjustConfirmForm.confirmedBy, adjustConfirmForm.remark)
  ElMessage.success('调整已驳回')
  adjustConfirmDialogVisible.value = false
  await loadPendingAdjusts()
}

// 获取状态颜色
function getStatusTone(status: string): 'success' | 'warning' | 'danger' | 'primary' {
  switch (status) {
    case '正常':
      return 'success'
    case '停诊':
      return 'danger'
    case '满诊':
      return 'warning'
    case '替班':
      return 'primary'
    default:
      return 'primary'
  }
}

// 获取调整类型标签
function getAdjustTypeTag(type: string): { text: string; tone: 'primary' | 'warning' | 'danger' } {
  switch (type) {
    case 'leave_ai':
      return { text: 'AI请假调整', tone: 'primary' }
    case 'admin_urgent':
      return { text: '管理员调整', tone: 'warning' }
    case 'system':
      return { text: '系统调整', tone: 'danger' }
    default:
      return { text: type, tone: 'primary' }
  }
}

// 监听筛选条件变化
watch(
  () => [filter.departmentId, filter.month],
  () => loadPlan(),
  { immediate: true }
)

// 初始化
onMounted(async () => {
  loading.value = true
  syncCurrentOperator()
  await Promise.all([loadDepartments(), loadPendingAdjusts()])
  loading.value = false
  // 进入页面时尝试拉一次后台任务,让离开又回来的用户能看到横幅
  const view = await refreshAiTaskOnce()
  if (view && !isAiTaskTerminal(view)) {
    await startAiTaskPolling()
  }
})
</script>

<template>
  <div class="schedule-management u-page-grid">
    <PageHeader
      title="智能排班管理"
      description="智能排班系统管理员后台，支持AI生成排班、请假处理、号源管理。所有AI生成的方案需管理员确认后生效。"
      eyebrow="Role B / Admin"
    >
      <template #actions>
        <ElButton type="primary" :loading="generating" @click="generateByAI">AI生成排班</ElButton>
      </template>
    </PageHeader>

    <!-- AI 排班后台任务横幅 -->
    <div v-if="aiTask" class="ai-task-banner" :class="`ai-task-banner--${aiTask.status}`">
      <div class="ai-task-banner__main">
        <div class="ai-task-banner__icon" aria-hidden="true">
          <span v-if="aiTask.status === 'running'">⏳</span>
          <span v-else-if="aiTask.status === 'success'">✓</span>
          <span v-else-if="aiTask.status === 'failed'">✕</span>
          <span v-else>⊘</span>
        </div>
        <div class="ai-task-banner__body">
          <div class="ai-task-banner__title">
            <template v-if="aiTask.status === 'running'">AI 排班生成中</template>
            <template v-else-if="aiTask.status === 'success'">AI 排班已生成</template>
            <template v-else-if="aiTask.status === 'failed'">AI 排班生成失败</template>
            <template v-else>AI 排班已取消</template>
            <span class="ai-task-banner__month">{{ filter.month }}</span>
          </div>
          <div class="ai-task-banner__message">{{ aiTask.message }}</div>
          <div v-if="aiTask.status === 'running'" class="ai-task-banner__progress">
            <div class="ai-task-banner__bar">
              <div
                class="ai-task-banner__bar-fill"
                :style="{ width: Math.min(100, Math.max(0, aiTask.percent || 0)) + '%' }"
              />
            </div>
            <span class="ai-task-banner__percent">{{ aiTask.percent || 0 }}%</span>
          </div>
        </div>
        <div class="ai-task-banner__actions">
          <ElButton
            v-if="aiTask.status === 'running'"
            link
            type="primary"
            @click="cancelAiTask"
          >
            取消
          </ElButton>
          <ElButton
            v-else
            link
            type="primary"
            @click="dismissAiTaskBanner"
          >
            关闭
          </ElButton>
        </div>
      </div>
    </div>

    <!-- 筛选和统计 -->
    <GlassCard class="filter-card">
      <div class="filter-row">
        <ElSelect
          v-model="filter.departmentId"
          filterable
          placeholder="选择科室"
          class="dept-select"
        >
          <ElOption
            v-for="item in departments"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </ElSelect>

        <ElInput v-model="filter.month" type="month" class="month-input" />

        <div class="statistics">
          <div class="stat-item">
            <span class="stat-value">{{ statistics.totalSchedules }}</span>
            <span class="stat-label">总排班</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ statistics.totalQuota }}</span>
            <span class="stat-label">总号源</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ statistics.usedQuota }}</span>
            <span class="stat-label">已用号源</span>
          </div>
          <div class="stat-item stat-highlight">
            <ElBadge :value="statistics.pendingAdjusts" :max="99" :hidden="statistics.pendingAdjusts === 0">
              <span class="stat-value">{{ statistics.pendingAdjusts }}</span>
            </ElBadge>
            <span class="stat-label">待确认调整</span>
          </div>
        </div>

        <div class="plan-actions">
          <ElButton v-if="!currentPlan" type="primary" :loading="generating" @click="generateByAI">
            AI生成排班
          </ElButton>
          <ElButton v-else-if="currentPlan.status === '草稿'" type="primary" @click="publishPlan">
            发布排班
          </ElButton>
          <StatusTag v-else :tone="currentPlan.status === '已发布' ? 'success' : 'warning'">
            {{ currentPlan.status }}
          </StatusTag>
        </div>
      </div>
    </GlassCard>

    <div class="main-content">
      <!-- 排班日历 -->
      <GlassCard class="calendar-card">
        <div class="section-header section-header--panel">
          <div>
            <h3>排班日历</h3>
            <p>按月份查看每日排班分布，上午/下午号源一目了然。</p>
          </div>
          <ElButton type="primary" plain @click="openEditSchedule()">添加排班</ElButton>
        </div>

        <div class="calendar-grid">
          <div class="weekday-header">
            <span v-for="day in ['周日', '周一', '周二', '周三', '周四', '周五', '周六']" :key="day">
              {{ day }}
            </span>
          </div>

          <div class="calendar-days">
            <!-- 填充空白 -->
            <div
              v-for="i in new Date(parseInt(filter.month.split('-')[0]), parseInt(filter.month.split('-')[1]) - 1, 1).getDay()"
              :key="'empty-' + i"
              class="calendar-day empty"
            />

            <div
              v-for="dayInfo in calendarDates"
              :key="dayInfo.day"
              class="calendar-day"
              :class="{ 'is-today': formatLocalDate(dayInfo.date) === formatLocalDate(new Date()) }"
            >
              <div class="day-header">
                <span class="day-number">{{ dayInfo.day }}</span>
                <span class="weekday">{{ dayInfo.weekday }}</span>
              </div>

              <div class="day-content">
                <template v-if="getDayData(dayInfo.date)">
                  <div class="slot-info morning">
                    <span>上午</span>
                    <span>{{ getDayData(dayInfo.date)?.morning || 0 }}医生</span>
                  </div>
                  <div class="slot-info afternoon">
                    <span>下午</span>
                    <span>{{ getDayData(dayInfo.date)?.afternoon || 0 }}医生</span>
                  </div>
                </template>
                <span v-else class="no-schedule">-</span>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>

      <!-- 待确认调整 -->
      <GlassCard class="adjust-card">
        <div class="section-header">
          <h3>
            <ElBadge :value="pendingAdjusts.length" :max="99">
              待确认调整
            </ElBadge>
          </h3>
        </div>

        <div class="adjust-list" v-if="pendingAdjusts.length > 0">
          <div
            v-for="adjust in pendingAdjusts"
            :key="adjust.id"
            class="adjust-item"
            @click="openAdjustConfirm(adjust)"
          >
            <div class="adjust-header">
              <ElTag :type="getAdjustTypeTag(adjust.adjustType).tone" size="small">
                {{ getAdjustTypeTag(adjust.adjustType).text }}
              </ElTag>
              <span class="adjust-time">{{ adjust.createTime }}</span>
            </div>
            <div class="adjust-content">
              <span v-if="adjust.adjustType === 'leave_ai'">
                {{ adjust.originalPhysicianName }} → {{ adjust.substitutePhysicianName }}
              </span>
              <span v-else>
                {{ adjust.reason || '管理员调整' }}
              </span>
            </div>
            <div class="adjust-meta">
              <span>{{ adjust.workDate }} {{ adjust.timeSlot }}</span>
              <span v-if="adjust.affectPatients > 0">影响{{ adjust.affectPatients }}名患者</span>
            </div>
          </div>
        </div>

        <ElEmpty v-else description="暂无待确认的调整" />
      </GlassCard>

      <!-- 排班列表 -->
      <GlassCard class="schedule-list-card">
        <div class="section-header section-header--panel">
          <div>
            <h3>排班明细</h3>
            <p>支持按日期、时段、状态和关键词筛选，并可直接编辑排班。</p>
          </div>
          <div class="section-actions">
            <ElTag type="info" effect="plain">{{ filteredPlanSchedules.length }} / {{ planSchedules.length }} 条</ElTag>
            <ElButton type="primary" @click="openEditSchedule()">新增排班</ElButton>
          </div>
        </div>

        <div v-if="planSchedules.length > 0" class="detail-filter-row">
          <ElInput
            v-model="detailFilter.keyword"
            clearable
            placeholder="搜索医生、职称、科室、建议或备注"
            class="detail-filter-keyword"
          />
          <ElDatePicker
            v-model="detailFilter.workDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="出诊日期"
            class="detail-filter-date"
          />
          <ElSelect v-model="detailFilter.timeSlot" clearable placeholder="时段" class="detail-filter-select">
            <ElOption label="上午" value="上午" />
            <ElOption label="下午" value="下午" />
            <ElOption label="晚上" value="晚上" />
          </ElSelect>
          <ElSelect v-model="detailFilter.status" clearable placeholder="状态" class="detail-filter-select">
            <ElOption label="正常" value="正常" />
            <ElOption label="停诊" value="停诊" />
            <ElOption label="满诊" value="满诊" />
            <ElOption label="替班" value="替班" />
          </ElSelect>
          <ElButton @click="resetDetailFilter">重置</ElButton>
        </div>

        <ElTable :data="filteredPlanSchedules" v-if="planSchedules.length > 0 && filteredPlanSchedules.length > 0">
          <ElTableColumn prop="physicianName" label="医生" min-width="120">
            <template #default="{ row }">
              <div>
                <div>{{ row.physicianName || '-' }}</div>
                <div class="text-muted text-sm">{{ row.physicianTitle }}</div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="workDate" label="日期" min-width="100" />
          <ElTableColumn prop="timeSlot" label="时段" min-width="80" />
          <ElTableColumn label="号源" min-width="120">
            <template #default="{ row }">
              <div>
                <span class="text-success">{{ row.availableQuota }}</span>
                <span class="text-muted"> / {{ row.totalQuota }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" min-width="80">
            <template #default="{ row }">
              <StatusTag :tone="getStatusTone(row.status)">{{ row.status }}</StatusTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="AI建议" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.aiSuggestion" class="ai-suggestion">
                <span class="ai-dot" />
                {{ row.aiSuggestion }}
              </span>
              <span v-else class="text-muted">-</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" min-width="150" fixed="right">
            <template #default="{ row }">
              <ElButton link type="primary" @click="openEditSchedule(row)">编辑</ElButton>
              <ElButton
                v-if="row.status === '正常'"
                link
                type="danger"
                @click="stopSchedule(row.id)"
              >
                停诊
              </ElButton>
              <ElButton
                v-else
                link
                type="success"
                @click="resumeSchedule(row.id)"
              >
                恢复
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <ElEmpty v-else-if="planSchedules.length > 0" description="暂无符合条件的排班" />
        <ElEmpty v-else description="暂无排班数据" />
      </GlassCard>
    </div>

    <!-- 排班编辑弹窗 -->
    <ElDialog v-model="editScheduleDialogVisible" :title="editScheduleDialogTitle" width="520px" class="schedule-dialog">
      <ElForm label-position="top">
        <ElFormItem label="医生">
          <ElSelect
            v-model="editScheduleForm.physicianId"
            filterable
            placeholder="请选择医生"
            class="full-width"
            no-data-text="当前科室暂无医生"
            @change="(val: number | undefined) => {
              const doc = scheduleDoctors.find((d) => d.id === val)
              editScheduleForm.physicianName = doc?.realname || ''
            }"
          >
            <ElOption
              v-for="d in scheduleDoctors"
              :key="d.id"
              :label="`${d.realname}${d.registName ? ' / ' + d.registName : ''}`"
              :value="d.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="出诊日期">
          <ElDatePicker
            v-model="editScheduleForm.workDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择出诊日期"
            class="full-width"
          />
        </ElFormItem>
        <ElFormItem label="时段">
          <ElSelect v-model="editScheduleForm.timeSlot" class="full-width">
            <ElOption label="上午" value="上午" />
            <ElOption label="下午" value="下午" />
            <ElOption label="晚上" value="晚上" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="总号源">
          <ElInputNumber v-model="editScheduleForm.totalQuota" :min="1" :controls="false" class="full-width" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="editScheduleForm.status" class="full-width">
            <ElOption label="正常" value="正常" />
            <ElOption label="停诊" value="停诊" />
            <ElOption label="满诊" value="满诊" />
            <ElOption label="替班" value="替班" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="editScheduleForm.remark" type="textarea" :rows="3" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editScheduleDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveSchedule">保存</ElButton>
      </template>
    </ElDialog>

    <!-- 调整确认弹窗 -->
    <ElDialog v-model="adjustConfirmDialogVisible" title="调整详情确认" width="600px" class="schedule-dialog">
      <template v-if="selectedAdjust">
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="调整类型">
            <ElTag :type="getAdjustTypeTag(selectedAdjust.adjustType).tone">
              {{ getAdjustTypeTag(selectedAdjust.adjustType).text }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="触发时间">{{ selectedAdjust.createTime }}</ElDescriptionsItem>
          <ElDescriptionsItem label="出诊信息">
            {{ selectedAdjust.workDate }} {{ selectedAdjust.timeSlot }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="原排班医生">{{ selectedAdjust.originalPhysicianName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="调整后医生">{{ selectedAdjust.substitutePhysicianName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="调整原因">{{ selectedAdjust.reason || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="影响患者">{{ selectedAdjust.affectPatients }}人</ElDescriptionsItem>
        </ElDescriptions>

        <div class="ai-suggestion-box" v-if="selectedAdjust.aiSuggestion">
          <h4>AI分析</h4>
          <p>{{ selectedAdjust.aiSuggestion }}</p>
        </div>

        <ElForm label-position="top" class="mt">
          <ElFormItem label="确认备注">
            <ElInput v-model="adjustConfirmForm.remark" type="textarea" :rows="2" />
          </ElFormItem>
        </ElForm>
      </template>

      <template #footer>
        <ElButton @click="adjustConfirmDialogVisible = false">取消</ElButton>
        <ElButton type="danger" plain @click="rejectAdjust">驳回</ElButton>
        <ElButton type="primary" @click="confirmAdjust">确认执行</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.schedule-management {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.filter-card {
  padding: var(--space-4) var(--space-5);
}

.calendar-card,
.adjust-card,
.schedule-list-card {
  padding: var(--space-5);
}

.calendar-card :deep(.el-table),
.schedule-list-card :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--color-table-header);
  --el-table-row-hover-bg-color: var(--color-primary-soft);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  min-width: 0;
}

.dept-select {
  width: min(220px, 100%);
}

.month-input {
  width: 160px;
}

.statistics {
  display: flex;
  gap: var(--space-3);
  margin-left: auto;
  flex-wrap: wrap;
  min-width: 0;
}

.stat-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 0;
  flex: 1 1 88px;
  max-width: 120px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-primary-soft);
  transition: transform var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.stat-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.stat-value {
  font-size: 1.6rem;
  font-weight: 700;
  line-height: 1.1;
  color: var(--color-primary);
  letter-spacing: -0.02em;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-label {
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-text-muted);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.stat-highlight {
  background: var(--gradient-ai);
  border-color: transparent;
  color: #ffffff;
}

.stat-highlight .stat-value,
.stat-highlight .stat-label {
  color: #ffffff;
}

.plan-actions {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 320px;
  grid-template-rows: auto auto;
  gap: var(--space-4);
}

.calendar-card {
  grid-column: 1;
  grid-row: 1;
}

.adjust-card {
  grid-column: 2;
  grid-row: 1 / 3;
}

.schedule-list-card {
  grid-column: 1;
  grid-row: 2;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  min-width: 0;
}

.section-header > div {
  min-width: 0;
  flex: 1;
}

.section-header p {
  margin: 6px 0 0;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--color-text-muted);
  overflow-wrap: anywhere;
}

.section-header--panel {
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.section-header h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.detail-filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
}

.detail-filter-keyword {
  width: min(320px, 100%);
}

.detail-filter-date,
.detail-filter-select {
  width: 150px;
}

/* 日历样式 */
.calendar-grid {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-surface-strong);
}

.weekday-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: var(--color-table-header);
  border-bottom: 1px solid var(--color-border);
}

.weekday-header span {
  padding: var(--space-3);
  text-align: center;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text-muted);
}

.calendar-days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.calendar-day {
  position: relative;
  min-height: 86px;
  min-width: 0;
  padding: var(--space-2) var(--space-2) var(--space-3);
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  transition: background var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
}

.calendar-day:hover:not(.empty) {
  background: var(--color-primary-soft);
  transform: translateY(-1px);
  z-index: 1;
}

.calendar-day.empty {
  background: rgba(70, 111, 160, 0.04);
}

.calendar-day.is-today {
  background: var(--color-primary-soft);
}

.calendar-day.is-today::before {
  content: '';
  position: absolute;
  inset-block-start: 0;
  inset-inline-start: 0;
  inline-size: 3px;
  block-size: 100%;
  background: var(--gradient-primary);
  border-radius: 0 2px 2px 0;
}

.calendar-day.is-today .day-number {
  color: var(--color-primary-strong);
  font-weight: 800;
}

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  margin-bottom: var(--space-2);
  min-width: 0;
}

.day-number {
  font-weight: 600;
  font-size: 0.95rem;
  flex-shrink: 0;
}

.weekday {
  font-size: 0.7rem;
  color: var(--color-text-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.slot-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
  font-size: 0.72rem;
  font-weight: 500;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-control);
  color: var(--color-text-muted);
  min-width: 0;
}

.slot-info span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.slot-info span:first-child {
  flex-shrink: 0;
}

.slot-info span:last-child {
  flex: 1;
  text-align: end;
}

.slot-info.morning {
  background: rgba(31, 140, 255, 0.1);
  color: var(--color-primary-strong);
  border-inline-start: 2px solid var(--color-primary);
}

.slot-info.afternoon {
  background: rgba(245, 159, 0, 0.12);
  color: #b86f00;
  border-inline-start: 2px solid var(--color-warning);
}

.no-schedule {
  color: var(--color-text-soft);
  text-align: center;
  font-size: 0.85rem;
}

/* 调整列表样式 */
.adjust-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-block-size: 540px;
  overflow-y: auto;
  padding-inline-end: 4px;
}

.adjust-item {
  position: relative;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
}

.adjust-item::before {
  content: '';
  position: absolute;
  inset-block-start: var(--space-2);
  inset-block-end: var(--space-2);
  inset-inline-start: 0;
  inline-size: 3px;
  border-radius: 0 2px 2px 0;
  background: transparent;
  transition: background var(--duration-fast) var(--ease-standard);
}

.adjust-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.adjust-item:hover::before {
  background: var(--gradient-primary);
}

.adjust-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
  gap: var(--space-2);
  min-width: 0;
}

.adjust-time {
  font-size: 0.72rem;
  color: var(--color-text-soft);
  flex-shrink: 0;
}

.adjust-content {
  margin-bottom: var(--space-2);
  font-size: 0.9rem;
  color: var(--color-text);
  line-height: 1.4;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.adjust-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: 0.72rem;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}

.adjust-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 通用样式 */
.full-width {
  width: 100%;
}

.text-sm {
  font-size: 0.75rem;
}

.text-muted {
  color: var(--color-text-muted);
}

.text-success {
  color: var(--color-success);
  font-weight: 600;
}

.ai-suggestion {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.ai-dot {
  display: inline-block;
  inline-size: 8px;
  block-size: 8px;
  border-radius: 999px;
  background: var(--gradient-ai);
  box-shadow: 0 0 0 3px rgba(124, 92, 255, 0.15);
  flex-shrink: 0;
}

.ai-suggestion-box {
  margin-top: var(--space-4);
  padding: var(--space-4);
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.08) 0%, rgba(31, 140, 255, 0.06) 100%);
  border-radius: var(--radius-lg);
  border-inline-start: 3px solid var(--color-ai);
}

.ai-suggestion-box h4 {
  margin: 0 0 var(--space-2);
  font-size: 0.9rem;
  color: var(--color-ai);
  font-weight: 600;
}

.ai-suggestion-box p {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--color-text);
}

.mt {
  margin-top: var(--space-4);
}

.schedule-dialog :deep(.el-dialog__header) {
  padding: var(--space-5) var(--space-6) var(--space-3);
}

.schedule-dialog :deep(.el-dialog__title) {
  font-size: 1.05rem;
  font-weight: 600;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.schedule-dialog :deep(.el-dialog__body) {
  padding: var(--space-3) var(--space-6) var(--space-4);
}

@media (max-width: 1200px) {
  .main-content {
    grid-template-columns: 1fr;
  }

  .calendar-card {
    grid-column: 1;
    grid-row: 1;
  }

  .adjust-card {
    grid-column: 1;
    grid-row: 2;
  }

  .schedule-list-card {
    grid-column: 1;
    grid-row: 3;
  }

  .statistics {
    margin-left: 0;
    width: 100%;
    justify-content: space-around;
  }

  .calendar-day {
    min-height: 60px;
  }
}

.ai-task-banner {
  border-radius: 10px;
  padding: 14px 18px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.10), rgba(64, 158, 255, 0.04));
  border: 1px solid rgba(64, 158, 255, 0.35);
  color: #1f3a5f;
}

.ai-task-banner--success {
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.12), rgba(103, 194, 58, 0.04));
  border-color: rgba(103, 194, 58, 0.4);
  color: #225c1f;
}

.ai-task-banner--failed {
  background: linear-gradient(135deg, rgba(245, 108, 108, 0.12), rgba(245, 108, 108, 0.04));
  border-color: rgba(245, 108, 108, 0.4);
  color: #6b1d1d;
}

.ai-task-banner--cancelled {
  background: linear-gradient(135deg, rgba(144, 147, 153, 0.12), rgba(144, 147, 153, 0.04));
  border-color: rgba(144, 147, 153, 0.4);
  color: #444;
}

.ai-task-banner__main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-task-banner__icon {
  font-size: 22px;
  line-height: 1;
}

.ai-task-banner__body {
  flex: 1;
  min-width: 0;
}

.ai-task-banner__title {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.ai-task-banner__month {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid currentColor;
  opacity: 0.7;
  flex-shrink: 0;
}

.ai-task-banner__message {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ai-task-banner__progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.ai-task-banner__bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 3px;
  overflow: hidden;
}

.ai-task-banner__bar-fill {
  height: 100%;
  background: #409eff;
  border-radius: 3px;
  transition: width 0.4s ease;
}

.ai-task-banner--success .ai-task-banner__bar-fill {
  background: #67c23a;
}

.ai-task-banner__percent {
  font-size: 12px;
  min-width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.ai-task-banner__actions {
  flex-shrink: 0;
}
</style>