<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDatePicker, ElEmpty, ElMessage } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import FollowUpDoctorSidebar from '@/modules/medtech/follow-up/components/FollowUpDoctorSidebar.vue'
import FollowUpMonthCalendar from '@/modules/medtech/follow-up/components/FollowUpMonthCalendar.vue'
import FollowUpPatientCard from '@/modules/medtech/follow-up/components/FollowUpPatientCard.vue'
import FollowUpScheduleDialog from '@/modules/medtech/follow-up/components/FollowUpScheduleDialog.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { monthRangeYmd } from '@/modules/medtech/follow-up/constants/followUpPriority'
import { beijingTodayYmd } from '@/shared/utils/beijingDate'
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

const targetDate = ref(beijingTodayYmd())
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)

const scheduleDialogVisible = ref(false)
const scheduleDialogDate = ref(beijingTodayYmd())

const interviewPatients = computed(() =>
  patients.value.filter((item) => item.interviewDueToday || item.interviewScheduledToday),
)

const observationPatients = computed(() => patients.value)

async function loadSchedules() {
  const { from, to } = monthRangeYmd(calendarYear.value, calendarMonth.value)
  schedules.value = await medtechFollowUpApi.listDaySchedules({ from, to })
}

async function loadDashboard() {
  loading.value = true
  try {
    const [contextRes, patientsRes] = await Promise.all([
      medtechFollowUpApi.getDashboardContext({ date: targetDate.value }),
      medtechFollowUpApi.listDashboardPatients({ date: targetDate.value }),
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

async function handleDropPatient(registerId: number, scheduleDate: string) {
  try {
    await medtechFollowUpApi.createDaySchedule({
      registerId,
      scheduleDate,
      itemType: 'interview',
    })
    ElMessage.success('已安排访谈日程')
    await loadDashboard()
  } catch {
    // 统一错误提示
  }
}

function openScheduleDialog(date: string) {
  scheduleDialogDate.value = date
  scheduleDialogVisible.value = true
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
    scheduleDialogVisible.value = false
    targetDate.value = payload.scheduleDate
    await loadDashboard()
  } catch {
    // 统一错误提示
  }
}

watch([calendarYear, calendarMonth], () => {
  void loadSchedules()
})

watch(targetDate, () => {
  void loadDashboard()
})

onMounted(() => {
  void loadDashboard()
})
</script>

<template>
  <div class="follow-up-dashboard u-page-grid" v-loading="loading">
    <PageHeader
      title="随访工作台"
      description="按科室查看在管患者、安排日级访谈日程，并跟进今日待观察患者。"
      eyebrow="随访系统 / Dashboard"
    >
      <template #actions>
        <ElDatePicker
          v-model="targetDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
        />
        <ElButton @click="loadDashboard">刷新</ElButton>
      </template>
    </PageHeader>

    <div class="follow-up-dashboard__layout">
      <div class="follow-up-dashboard__main">
        <FollowUpMonthCalendar
          v-model:year="calendarYear"
          v-model:month="calendarMonth"
          :today-ymd="beijingTodayYmd()"
          :selected-date="targetDate"
          :schedules="schedules"
          @select-date="targetDate = $event"
          @drop-patient="handleDropPatient"
          @add-schedule="openScheduleDialog"
        />

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>今日待访谈</h3>
              <p>重点患者访谈周期更短，卡片颜色更深。</p>
            </div>
          </div>
          <div v-if="interviewPatients.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in interviewPatients"
              :key="`interview-${patient.registerId}`"
              :patient="patient"
              :observed="patient.observedToday"
              @click="openOutcome"
            />
          </div>
          <ElEmpty v-else description="今日暂无待访谈患者" />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>今日待观察</h3>
              <p>点击卡片进入疗效评估；确认「今日已观察」后卡片将变灰。</p>
            </div>
          </div>
          <div v-if="observationPatients.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in observationPatients"
              :key="`observation-${patient.registerId}`"
              :patient="patient"
              :observed="patient.observedToday"
              @click="openOutcome"
            />
          </div>
          <ElEmpty v-else description="本科室暂无随访患者" />
        </GlassCard>
      </div>

      <FollowUpDoctorSidebar :context="context" :target-date="targetDate" />
    </div>

    <FollowUpScheduleDialog
      v-model:visible="scheduleDialogVisible"
      :schedule-date="scheduleDialogDate"
      :patients="patients"
      @submit="handleScheduleSubmit"
    />
  </div>
</template>

<style scoped>
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
