<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import FollowUpAllPatientsPool from '@/modules/medtech/follow-up/components/FollowUpAllPatientsPool.vue'
import FollowUpDoctorSidebar from '@/modules/medtech/follow-up/components/FollowUpDoctorSidebar.vue'
import FollowUpMonthCalendar from '@/modules/medtech/follow-up/components/FollowUpMonthCalendar.vue'
import FollowUpPatientCard from '@/modules/medtech/follow-up/components/FollowUpPatientCard.vue'
import FollowUpScheduleDialog from '@/modules/medtech/follow-up/components/FollowUpScheduleDialog.vue'
import FollowUpShiftDayDialog from '@/modules/medtech/follow-up/components/FollowUpShiftDayDialog.vue'
import FollowUpShiftChangeDialog from '@/modules/medtech/follow-up/components/FollowUpShiftChangeDialog.vue'
import FollowUpMonitoringTransferDialog from '@/modules/medtech/follow-up/components/FollowUpMonitoringTransferDialog.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { monthRangeYmd } from '@/modules/medtech/follow-up/constants/followUpPriority'
import { beijingTodayYmd, formatYmdWeekday } from '@/shared/utils/beijingDate'
import type {
  FollowUpDashboardContext,
  FollowUpDashboardPatient,
  FollowUpDayScheduleItem,
  FollowUpMonitoredRosterItem,
  FollowUpStaffShift,
} from '@/shared/types/medtechFollowUp'

const router = useRouter()
const loading = ref(false)
const context = ref<FollowUpDashboardContext | null>(null)
const patients = ref<FollowUpDashboardPatient[]>([])
const myMonitoredPatients = ref<FollowUpDashboardPatient[]>([])
const schedules = ref<FollowUpDayScheduleItem[]>([])
const myShifts = ref<FollowUpStaffShift[]>([])
const communicationUnread = ref(0)
let unreadTimer: ReturnType<typeof setInterval> | undefined

const todayYmd = beijingTodayYmd()
const todayLabel = formatYmdWeekday(todayYmd)

const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)

const scheduleDialogVisible = ref(false)
const scheduleDialogDate = ref(todayYmd)
const schedulingRegisterId = ref<number | null>(null)

const shiftDayDialogVisible = ref(false)
const shiftDayDialogDate = ref(todayYmd)
const shiftDayDialogTasks = ref<FollowUpStaffShift['contactTasks']>([])
const shiftDayDialogMonitoredPatients = ref<FollowUpMonitoredRosterItem[]>([])

const shiftChangeVisible = ref(false)
const shiftChangeTarget = ref<FollowUpStaffShift | null>(null)

const transferVisible = ref(false)
const transferTarget = ref<FollowUpDashboardPatient | null>(null)

const enrolledPatients = computed(() => patients.value.filter((p) => p.enrolled))

const myPatients = computed(() => myMonitoredPatients.value)

const unassignedEnrolled = computed(() =>
  enrolledPatients.value.filter((p) => !p.monitoringEmployeeId),
)

const contactDueToday = computed(() =>
  myPatients.value.filter(
    (p) => p.contactStatus !== 'contacted_today' && p.contactStatus !== 'within_limit',
  ),
)

const todayShift = computed(() => myShifts.value.find((s) => s.workDate === todayYmd))

const contactStats = computed(() => ({
  myMonitoring: myPatients.value.length,
  contacted: myPatients.value.filter((p) => p.contactStatus === 'contacted_today').length,
  due: myPatients.value.filter((p) => p.contactStatus === 'due').length,
  overdue: myPatients.value.filter((p) => p.contactStatus === 'overdue').length,
}))

async function loadSchedules() {
  const { from, to } = monthRangeYmd(calendarYear.value, calendarMonth.value)
  const [daySchedules, shifts] = await Promise.all([
    medtechFollowUpApi.listDaySchedules({ from, to }),
    medtechFollowUpApi.listMyShifts(from, to),
  ])
  schedules.value = daySchedules
  myShifts.value = shifts
}

async function loadDashboard() {
  loading.value = true
  try {
    const [contextRes, patientsRes, myMonitoredRes] = await Promise.all([
      medtechFollowUpApi.getDashboardContext({ date: todayYmd }),
      medtechFollowUpApi.listDashboardPatients({ date: todayYmd }),
      medtechFollowUpApi.listMyMonitoredPatients({ date: todayYmd }),
    ])
    myMonitoredPatients.value = myMonitoredRes
    context.value = {
      ...contextRes,
      stats: {
        ...contextRes.stats,
        enrolledPatients:
          contextRes.stats?.enrolledPatients ??
          patientsRes.filter((p) => p.enrolled).length,
        myMonitoringCount: myMonitoredRes.length,
        todayContactDue: myMonitoredRes.filter(
          (p) => p.contactStatus !== 'contacted_today',
        ).length,
        todayContacted: myMonitoredRes.filter(
          (p) => p.contactStatus === 'contacted_today',
        ).length,
        contactOverdue: myMonitoredRes.filter((p) => p.contactStatus === 'overdue').length,
      },
    }
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

function openShiftDay(date: string) {
  const shift = myShifts.value.find((s) => s.workDate === date)
  shiftDayDialogDate.value = date
  shiftDayDialogTasks.value = shift?.contactTasks ?? []
  shiftDayDialogMonitoredPatients.value = shift?.monitoredPatients ?? myShifts.value[0]?.monitoredPatients ?? []
  shiftDayDialogVisible.value = true
}

function openShiftChange(shift: FollowUpStaffShift) {
  shiftChangeTarget.value = shift
  shiftChangeVisible.value = true
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
    // unified error
  } finally {
    schedulingRegisterId.value = null
  }
}

async function openTransferRequest(patient: FollowUpDashboardPatient) {
  transferTarget.value = patient
  transferVisible.value = true
}

async function onTransferSubmitted() {
  transferVisible.value = false
  ElMessage.success('调换申请已提交，等待管理员审批')
  await loadDashboard()
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
    // unified error
  }
}

async function onShiftChangeSubmitted() {
  shiftChangeVisible.value = false
  ElMessage.success('调班申请已提交，等待管理员审批')
  await loadDashboard()
}

async function loadCommunicationUnread() {
  try {
    const summary = await medtechFollowUpApi.getDoctorCommunicationUnreadSummary()
    communicationUnread.value = summary.totalUnread ?? 0
  } catch {
    communicationUnread.value = 0
  }
}

watch([calendarYear, calendarMonth], () => {
  void loadSchedules()
})

onMounted(() => {
  void loadDashboard()
  void loadCommunicationUnread()
  unreadTimer = setInterval(() => void loadCommunicationUnread(), 30_000)
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})

onActivated(() => {
  void loadDashboard()
})
</script>

<template>
  <div class="follow-up-dashboard u-page-grid" v-loading="loading">
    <PageHeader
      title="随访工作台"
      description="监视患者、今日联系任务、科室患者池与工作排班。"
      eyebrow="随访系统 / Dashboard"
    >
      <template #actions>
        <ElTag type="info" effect="plain" class="follow-up-dashboard__today-tag">{{ todayLabel }}</ElTag>
        <ElButton v-if="todayShift" @click="openShiftChange(todayShift)">申请调班</ElButton>
        <ElButton @click="loadDashboard">刷新</ElButton>
      </template>
    </PageHeader>

    <div class="follow-up-dashboard__layout">
      <div class="follow-up-dashboard__main">
        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>我的监视患者</h3>
              <p>您负责日常联系与随访的患者，点击卡片进入疗效评估。</p>
            </div>
            <ElTag effect="plain">共 {{ myPatients.length }} 人</ElTag>
          </div>
          <div v-if="myPatients.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in myPatients"
              :key="`mine-${patient.registerId}`"
              :patient="patient"
              show-contact-info
              show-status-row
              :draggable="false"
              @click="openOutcome"
            />
          </div>
          <ElEmpty
            v-else
            :description="
              unassignedEnrolled.length
                ? '本科室有在管患者尚未分配监视医生，请联系管理员'
                : '暂无监视患者，请联系管理员分配'
            "
          />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>今日需联系</h3>
              <p>6 个月随访期内尚未完成今日联系的患者（已联系 {{ contactStats.contacted }} / 待联系 {{ contactStats.due + contactStats.overdue }}）。</p>
            </div>
          </div>
          <div v-if="contactDueToday.length" class="follow-up-dashboard__cards">
            <FollowUpPatientCard
              v-for="patient in contactDueToday"
              :key="`contact-${patient.registerId}`"
              :patient="patient"
              show-contact-info
              :draggable="false"
              @click="openOutcome"
            />
          </div>
          <ElEmpty v-else description="今日联系任务已全部完成" />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>科室患者池</h3>
              <p>本科室全部在管患者；监视医生由管理员分配，您可对已分配给自己的患者申请调换。</p>
            </div>
          </div>
          <FollowUpAllPatientsPool
            :patients="patients"
            :scheduling-id="schedulingRegisterId"
            @open="openOutcome"
            @schedule-today="scheduleTodayInterview"
            @transfer="openTransferRequest"
          />
        </GlassCard>

        <GlassCard class="follow-up-dashboard__panel">
          <div class="follow-up-dashboard__panel-head">
            <div>
              <h3>我的工作排班</h3>
              <p>管理员发布的月度排班；点击日期查看当日需联系患者。</p>
            </div>
          </div>
          <div v-if="myShifts.length" class="follow-up-dashboard__shift-list">
            <button
              v-for="shift in myShifts"
              :key="shift.id"
              type="button"
              class="follow-up-dashboard__shift-chip"
              :class="{ 'follow-up-dashboard__shift-chip--today': shift.workDate === todayYmd }"
              @click="openShiftDay(shift.workDate)"
            >
              <strong>{{ shift.workDate }}</strong>
              <span>
                {{ shift.contactTasks?.length ?? 0 }} 位排班联系
                <template v-if="shift.monitoredPatients?.length">
                  · {{ shift.monitoredPatients.length }} 位监视
                </template>
              </span>
            </button>
          </div>
          <ElEmpty v-else description="本月暂无工作排班，请联系管理员生成" />
        </GlassCard>

        <FollowUpMonthCalendar
          v-model:year="calendarYear"
          v-model:month="calendarMonth"
          :today-ymd="todayYmd"
          :schedules="schedules"
          :shift-dates="myShifts.map((s) => s.workDate)"
          @open-day="openScheduleDialog"
          @open-shift-day="openShiftDay"
        />
      </div>

      <FollowUpDoctorSidebar
        :context="context"
        :target-date="todayYmd"
        :contact-stats="contactStats"
        :communication-unread="communicationUnread"
      />
    </div>

    <FollowUpScheduleDialog
      v-model:visible="scheduleDialogVisible"
      :schedule-date="scheduleDialogDate"
      :patients="patients"
      :schedules="schedules"
      @submit="handleScheduleSubmit"
    />

    <FollowUpShiftDayDialog
      v-model="shiftDayDialogVisible"
      :date="shiftDayDialogDate"
      :tasks="shiftDayDialogTasks"
      :monitored-patients="shiftDayDialogMonitoredPatients"
      @open-patient="openOutcome"
    />

    <FollowUpShiftChangeDialog
      v-model="shiftChangeVisible"
      :shift="shiftChangeTarget"
      @submitted="onShiftChangeSubmitted"
    />

    <FollowUpMonitoringTransferDialog
      v-model="transferVisible"
      :patient="transferTarget"
      @submitted="onTransferSubmitted"
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

.follow-up-dashboard__panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.follow-up-dashboard__panel-head h3 {
  margin: 0 0 var(--space-1);
  font-size: 17px;
}

.follow-up-dashboard__panel-head p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.follow-up-dashboard__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-3);
}

.follow-up-dashboard__shift-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.follow-up-dashboard__shift-chip {
  display: grid;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  cursor: pointer;
  font: inherit;
  text-align: start;
}

.follow-up-dashboard__shift-chip--today {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, var(--color-bg-soft));
}

.follow-up-dashboard__shift-chip span {
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .follow-up-dashboard__layout {
    grid-template-columns: 1fr;
  }
}
</style>
