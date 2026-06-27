<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import FollowUpAllPatientsPool from '@/modules/medtech/follow-up/components/FollowUpAllPatientsPool.vue'
import FollowUpDoctorSidebar from '@/modules/medtech/follow-up/components/FollowUpDoctorSidebar.vue'
import FollowUpMonthCalendar from '@/modules/medtech/follow-up/components/FollowUpMonthCalendar.vue'
import FollowUpPatientCard from '@/modules/medtech/follow-up/components/FollowUpPatientCard.vue'
import FollowUpScheduleDialog from '@/modules/medtech/follow-up/components/FollowUpScheduleDialog.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { monthRangeYmd } from '@/modules/medtech/follow-up/constants/followUpPriority'
import { beijingTodayYmd, formatYmdWeekday } from '@/shared/utils/beijingDate'
import type {
  FollowUpDashboardContext,
  FollowUpDashboardPatient,
  FollowUpDayScheduleItem,
} from '@/shared/types/medtechFollowUp'

const router = useRouter()
const loading = ref(false)
const context = ref<FollowUpDashboardContext | null>(null)
const patients = ref<FollowUpDashboardPatient[]>([])
const schedules = ref<FollowUpDayScheduleItem[]>([])

const todayYmd = beijingTodayYmd()
const todayLabel = formatYmdWeekday(todayYmd)

const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)

const scheduleDialogVisible = ref(false)
const scheduleDialogDate = ref(todayYmd)
const schedulingRegisterId = ref<number | null>(null)

/** 仅展示当日已排访谈的患者 */
const interviewPatients = computed(() =>
  patients.value.filter((item) => item.interviewScheduledToday),
)

/** 仅展示当日尚未完成观察的患者 */
const observationPatients = computed(() =>
  patients.value.filter((item) => item.observationDueToday),
)

async function loadSchedules() {
  const { from, to } = monthRangeYmd(calendarYear.value, calendarMonth.value)
  schedules.value = await medtechFollowUpApi.listDaySchedules({ from, to })
}

async function loadDashboard() {
  loading.value = true
  try {
    const [contextRes, patientsRes] = await Promise.all([
      medtechFollowUpApi.getDashboardContext({ date: todayYmd }),
      medtechFollowUpApi.listDashboardPatients({ date: todayYmd }),
    ])
    context.value = contextRes
    patients.value = patientsRes
    await loadSchedules()
  } catch {
    ElMessage.error('加载随访工作台失败')
  } finally {
    loading.value = false
  }
}

function openOutcome(patient: FollowUpDashboardPatient) {
  void router.push({
    name: 'FollowUpOutcome',
    query: { registerId: String(patient.registerId) },
  })
}

function openScheduleDialog(date: string) {
  scheduleDialogDate.value = date
  scheduleDialogVisible.value = true
}

async function scheduleTodayInterview(patient: FollowUpDashboardPatient) {
  schedulingRegisterId.value = patient.registerId
  try {
    await medtechFollowUpApi.createDaySchedule({
      registerId: patient.registerId,
      scheduleDate: todayYmd,
      itemType: 'interview',
    })
    ElMessage.success(`已将 ${patient.realName ?? '患者'} 排入今日访谈`)
    await loadDashboard()
  } catch {
    // 统一错误提示
  } finally {
    schedulingRegisterId.value = null
  }
}

async function handleScheduleSubmit(payload: {
  registerId?: number
  scheduleDate: string
  itemType: string
  title: string
}) {
  try {
    await medtechFollowUpApi.createDaySchedule({
      registerId: payload.registerId,
      scheduleDate: payload.scheduleDate,
      itemType: payload.itemType as 'interview' | 'custom',
      title: payload.title || undefined,
    })
    ElMessage.success('日程已保存')
    await loadDashboard()
  } catch {
    // 统一错误提示
  }
}

watch([calendarYear, calendarMonth], () => {
  void loadSchedules()
})

onMounted(() => {
  void loadDashboard()
})

onActivated(() => {
  void loadDashboard()
})
</script>

<template>
  <div class="follow-up-dashboard u-page-grid" v-loading="loading">
    <PageHeader
      title="随访工作台"
      description="今日任务池聚焦待办；全科在管池可检索全部患者并快速排访谈。"
      eyebrow="随访系统 / Dashboard"
    >
      <template #actions>
        <ElTag type="info" effect="plain" class="follow-up-dashboard__today-tag">{{ todayLabel }}</ElTag>
        <ElButton @click="loadDashboard">刷新</ElButton>
      </template>
    </PageHeader>

    <div class="follow-up-dashboard__layout">
      <div class="follow-up-dashboard__main">
        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>今日待访谈</h3>
              <p>仅显示今日已安排访谈的患者；未排入日程的患者不会出现在此列表。</p>
            </div>
          </div>
          <div v-if="interviewPatients.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in interviewPatients"
              :key="`interview-${patient.registerId}`"
              :patient="patient"
              :observed="patient.observedToday"
              :dim-observed="false"
              :draggable="false"
              @click="openOutcome"
            />
          </div>
          <ElEmpty v-else description="今日暂无已排访谈" />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>今日待观察</h3>
              <p>点击卡片进入疗效评估；确认「今日已观察」后卡片将变灰并移出列表。</p>
            </div>
          </div>
          <div v-if="observationPatients.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in observationPatients"
              :key="`observation-${patient.registerId}`"
              :patient="patient"
              :observed="patient.observedToday"
              :draggable="false"
              @click="openOutcome"
            />
          </div>
          <ElEmpty v-else description="今日观察任务已全部完成" />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>全科在管患者</h3>
              <p>本科室全部纳入随访池的患者；支持搜索筛选，可一键排入今日访谈或进入疗效评估。</p>
            </div>
          </div>
          <FollowUpAllPatientsPool
            :patients="patients"
            :scheduling-id="schedulingRegisterId"
            @open="openOutcome"
            @schedule-today="scheduleTodayInterview"
          />
        </GlassCard>

        <FollowUpMonthCalendar
          v-model:year="calendarYear"
          v-model:month="calendarMonth"
          :today-ymd="todayYmd"
          :schedules="schedules"
          @open-day="openScheduleDialog"
        />
      </div>

      <FollowUpDoctorSidebar :context="context" :target-date="todayYmd" />
    </div>

    <FollowUpScheduleDialog
      v-model:visible="scheduleDialogVisible"
      :schedule-date="scheduleDialogDate"
      :patients="patients"
      :schedules="schedules"
      @submit="handleScheduleSubmit"
    />
  </div>
</template>

<style scoped>
.follow-up-dashboard__today-tag {
  font-size: 13px;
  padding: 8px 12px;
}

.follow-up-dashboard__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: var(--space-4);
  align-items: start;
}

.follow-up-dashboard__main {
  display: grid;
  gap: var(--space-4);
}

.follow-up-dashboard__panel {
  padding: var(--space-4);
}

.follow-up-dashboard__panel-head h3 {
  margin: 0;
}

.follow-up-dashboard__panel-head p {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.follow-up-dashboard__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

@media (max-width: 1100px) {
  .follow-up-dashboard__layout {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.outcome-dialog-overlay {
  pointer-events: none;
}

.outcome-dialog-overlay .el-dialog {
  pointer-events: auto;
}
</style>
