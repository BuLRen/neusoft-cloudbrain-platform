<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElBadge,
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import AdminFollowUpShiftCalendar from '@/modules/admin/components/AdminFollowUpShiftCalendar.vue'
import AdminFollowUpShiftDayDialog from '@/modules/admin/components/AdminFollowUpShiftDayDialog.vue'
import { followUpShiftAdminApi } from '@/shared/api/modules/followUpShiftAdmin'
import { adminFollowUpApi } from '@/shared/api/modules/adminFollowUp'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { registrationApi } from '@/shared/api/modules/registration'
import { beijingTodayYmd } from '@/shared/utils/beijingDate'
import type { DepartmentOption } from '@/shared/types/registration'
import type {
  FollowUpDashboardPatient,
  FollowUpMonitoringTransferRequest,
  FollowUpShiftChangeRequest,
  FollowUpStaffShift,
} from '@/shared/types/medtechFollowUp'
import type { FollowUpAdminRecord } from '@/shared/api/modules/adminFollowUp'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function formatMonth(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

function monthRange(month: string) {
  const [y, m] = month.split('-').map(Number)
  const last = new Date(y!, m!, 0).getDate()
  return { from: `${month}-01`, to: `${month}-${pad2(last)}` }
}

const loading = ref(false)
const generating = ref(false)
const departments = ref<DepartmentOption[]>([])
const shifts = ref<FollowUpStaffShift[]>([])
const plan = ref<Record<string, unknown> | null>(null)
const pendingRequests = ref<FollowUpShiftChangeRequest[]>([])
const pendingTransfers = ref<FollowUpMonitoringTransferRequest[]>([])
const departmentPatients = ref<FollowUpDashboardPatient[]>([])
const followUpNurses = ref<FollowUpAdminRecord[]>([])
const assignSelections = ref<Record<number, number | undefined>>({})
const assigningId = ref<number | null>(null)
const randomAssigning = ref(false)
const syncingEnrollment = ref(false)
const monitoringLoad = ref<{
  autoAssignEnabled?: boolean
  unassignedCount?: number
  doctors?: Array<{ employeeId: number; name?: string; patientCount: number }>
} | null>(null)
const aiTask = ref<{
  status: string
  message?: string
  percent?: number
  result?: { source?: string; shiftCount?: number; taskCount?: number; patientCount?: number; scheduleNote?: string }
} | null>(null)

const filter = reactive({
  departmentId: undefined as number | undefined,
  month: formatMonth(new Date()),
})

const reviewDialogVisible = ref(false)
const reviewTarget = ref<FollowUpShiftChangeRequest | null>(null)
const reviewNote = ref('')
const reviewApprove = ref(true)

const transferReviewVisible = ref(false)
const transferReviewTarget = ref<FollowUpMonitoringTransferRequest | null>(null)
const transferReviewNote = ref('')
const transferReviewApprove = ref(true)

const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)
const shiftDayDialogVisible = ref(false)
const shiftDayDialogDate = ref('')
const shiftDayDialogShifts = ref<FollowUpStaffShift[]>([])

function syncCalendarFromFilterMonth() {
  const [y, m] = filter.month.split('-').map(Number)
  if (y && m) {
    calendarYear.value = y
    calendarMonth.value = m
  }
}

function openShiftDay(date: string, dayShifts: FollowUpStaffShift[]) {
  shiftDayDialogDate.value = date
  shiftDayDialogShifts.value = dayShifts
  shiftDayDialogVisible.value = true
}

const pendingCount = computed(() => pendingRequests.value.length)
const pendingTransferCount = computed(() => pendingTransfers.value.length)
const enrolledPatients = computed(() => departmentPatients.value.filter((p) => Boolean(p.enrolled)))
const unassignedPatients = computed(() => enrolledPatients.value.filter((p) => !p.monitoringEmployeeId))
const eligibleUnenrolledCount = computed(() => departmentPatients.value.filter((p) => !p.enrolled).length)

async function loadDepartments() {
  departments.value = await registrationApi.departments('临床科室')
  if (!filter.departmentId && departments.value.length) {
    filter.departmentId = departments.value[0]!.id
  }
}

async function loadData() {
  if (!filter.departmentId) return
  loading.value = true
  try {
    const { from, to } = monthRange(filter.month)
    const dept = departments.value.find((d) => d.id === filter.departmentId)
    const [planRes, shiftRes, pendingRes, transferRes, taskRes, patientsRes, nursesPage, loadSummaryRes] = await Promise.all([
      followUpShiftAdminApi.getPlan(filter.departmentId, filter.month),
      followUpShiftAdminApi.listShifts(filter.departmentId, from, to),
      followUpShiftAdminApi.pendingChangeRequests(filter.departmentId),
      followUpShiftAdminApi.pendingTransferRequests(filter.departmentId),
      followUpShiftAdminApi.aiGenerateActive(filter.departmentId, filter.month),
      medtechFollowUpApi.listDashboardPatients({
        date: beijingTodayYmd(),
        departmentId: filter.departmentId,
      }),
      adminFollowUpApi.list({ departmentId: filter.departmentId, includeDisabled: false, size: 100 }),
      followUpShiftAdminApi.getMonitoringLoadSummary(filter.departmentId),
    ])
    plan.value = planRes
    shifts.value = shiftRes
    pendingRequests.value = pendingRes
    pendingTransfers.value = transferRes
    aiTask.value = taskRes
    followUpNurses.value = nursesPage.records
    departmentPatients.value = patientsRes
    monitoringLoad.value = loadSummaryRes
  } finally {
    loading.value = false
  }
}

async function startAiGenerate() {
  if (!filter.departmentId) return
  generating.value = true
  try {
    const dept = departments.value.find((d) => d.id === filter.departmentId)
    await followUpShiftAdminApi.aiGenerate({
      departmentId: filter.departmentId,
      month: filter.month,
      departmentName: dept?.name,
    })
    ElMessage.success('AI 排班任务已提交')
    pollAiTask()
  } finally {
    generating.value = false
  }
}

function pollAiTask() {
  const timer = window.setInterval(async () => {
    if (!filter.departmentId) return
    const task = await followUpShiftAdminApi.aiGenerateActive(filter.departmentId, filter.month)
    aiTask.value = task
    if (task.status === 'success' || task.status === 'failed' || task.status === 'idle') {
      window.clearInterval(timer)
      if (task.status === 'success') {
        const source = task.result?.source
        const detail =
          source === 'dify'
            ? '（Dify 工作流）'
            : source === 'rule_based'
              ? '（规则降级）'
              : ''
        ElMessage.success(`${task.message ?? 'AI 排班生成完成'}${detail}`)
        await loadData()
      } else if (task.status === 'failed') {
        ElMessage.error(task.message || 'AI 排班生成失败')
      }
    }
  }, 2000)
}

async function publishPlan() {
  const planId = plan.value?.id as number | undefined
  if (!planId) {
    ElMessage.warning('请先生成排班计划')
    return
  }
  await followUpShiftAdminApi.publish(planId)
  ElMessage.success('排班已发布')
  await loadData()
}

function openReview(row: FollowUpShiftChangeRequest, approve: boolean) {
  reviewTarget.value = row
  reviewApprove.value = approve
  reviewNote.value = ''
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (!reviewTarget.value) return
  if (reviewApprove.value) {
    await followUpShiftAdminApi.approveChangeRequest(reviewTarget.value.id, reviewNote.value || undefined)
    ElMessage.success('已同意调班')
  } else {
    await followUpShiftAdminApi.rejectChangeRequest(reviewTarget.value.id, reviewNote.value || undefined)
    ElMessage.success('已驳回调班')
  }
  reviewDialogVisible.value = false
  await loadData()
}

function openTransferReview(row: FollowUpMonitoringTransferRequest, approve: boolean) {
  transferReviewTarget.value = row
  transferReviewApprove.value = approve
  transferReviewNote.value = ''
  transferReviewVisible.value = true
}

async function submitTransferReview() {
  if (!transferReviewTarget.value) return
  if (transferReviewApprove.value) {
    await followUpShiftAdminApi.approveTransferRequest(
      transferReviewTarget.value.id,
      transferReviewNote.value || undefined,
    )
    ElMessage.success('已同意调换')
  } else {
    await followUpShiftAdminApi.rejectTransferRequest(
      transferReviewTarget.value.id,
      transferReviewNote.value || undefined,
    )
    ElMessage.success('已驳回调换')
  }
  transferReviewVisible.value = false
  await loadData()
}

async function syncDepartmentEnrollment() {
  if (!filter.departmentId) return
  syncingEnrollment.value = true
  try {
    const result = await medtechFollowUpApi.backfillEnrollment({
      departmentId: filter.departmentId,
      batchSize: 200,
      maxBatches: 10,
    })
    const enrolled = Number(result.enrolled ?? 0)
    const remaining = Number(result.remainingEligible ?? 0)
    ElMessage.success(
      enrolled > 0
        ? `已同步 ${enrolled} 名看诊结束患者到随访池${remaining > 0 ? `，尚有 ${remaining} 人待同步` : ''}`
        : remaining > 0
          ? `暂无新患者可同步，尚有 ${remaining} 人待处理`
          : '本科室看诊结束患者均已纳入随访池',
    )
    await loadData()
  } finally {
    syncingEnrollment.value = false
  }
}

async function assignPatient(patient: FollowUpDashboardPatient) {
  const employeeId = assignSelections.value[patient.registerId]
  if (!employeeId || !filter.departmentId) {
    ElMessage.warning('请选择随访医生')
    return
  }
  assigningId.value = patient.registerId
  try {
    await followUpShiftAdminApi.assignMonitoring({
      registerId: patient.registerId,
      employeeId,
      departmentId: filter.departmentId,
    })
    ElMessage.success('已分配监视医生')
    await loadData()
  } finally {
    assigningId.value = null
  }
}

async function randomAssignMonitoring() {
  if (!filter.departmentId) return
  if (!followUpNurses.value.length) {
    ElMessage.warning('当前科室暂无随访医生')
    return
  }
  if (!unassignedPatients.value.length) {
    ElMessage.info('暂无待分配患者')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将把 ${unassignedPatients.value.length} 名待分配患者随机分配给 ${followUpNurses.value.length} 名随访医生。完成后，新纳入患者将自动分配给监视患者最少的医生。`,
      '随机分布监视医生',
      { type: 'warning', confirmButtonText: '开始分配', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  randomAssigning.value = true
  try {
    const result = await followUpShiftAdminApi.randomAssignMonitoring({
      departmentId: filter.departmentId,
    })
    const assigned = Number(result.assigned ?? 0)
    ElMessage.success(assigned > 0 ? `已随机分配 ${assigned} 名患者` : '暂无待分配患者')
    await loadData()
  } finally {
    randomAssigning.value = false
  }
}

watch(
  () => filter.month,
  () => {
    syncCalendarFromFilterMonth()
  },
)

watch(
  () => [filter.departmentId, filter.month],
  () => {
    void loadData()
  },
)

watch(
  () => [calendarYear.value, calendarMonth.value],
  () => {
    const nextMonth = formatMonth(new Date(calendarYear.value, calendarMonth.value - 1, 1))
    if (nextMonth !== filter.month) {
      filter.month = nextMonth
    }
  },
)

onMounted(async () => {
  syncCalendarFromFilterMonth()
  await loadDepartments()
  await loadData()
})
</script>

<template>
  <div class="followup-shift-admin u-page-grid">
    <PageHeader
      title="随访工作排班"
      description="管理员分配患者监视医生、审批调换申请，并通过 AI 生成月度排班。"
      eyebrow="管理员"
    >
      <template #actions>
        <ElBadge :value="pendingCount" :hidden="!pendingCount">
          <ElTag type="warning" effect="plain">待审批调班 {{ pendingCount }}</ElTag>
        </ElBadge>
        <ElBadge :value="pendingTransferCount" :hidden="!pendingTransferCount">
          <ElTag type="danger" effect="plain">待审批调换 {{ pendingTransferCount }}</ElTag>
        </ElBadge>
      </template>
    </PageHeader>

    <GlassCard class="panel">
      <div class="toolbar">
        <ElSelect v-model="filter.departmentId" placeholder="临床科室" style="width: 180px">
          <ElOption v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
        </ElSelect>
        <ElInput v-model="filter.month" placeholder="yyyy-MM" style="width: 120px" />
        <ElButton type="primary" :loading="generating" @click="startAiGenerate">AI 生成排班</ElButton>
        <ElButton :loading="syncingEnrollment" @click="syncDepartmentEnrollment">同步看诊结束患者</ElButton>
        <ElButton :disabled="!plan?.id" @click="publishPlan">发布排班</ElButton>
        <ElButton @click="loadData">刷新</ElButton>
      </div>

      <div v-if="aiTask?.status === 'running'" class="ai-banner">
        <ElProgress :percentage="aiTask.percent ?? 30" :stroke-width="8" />
        <span>{{ aiTask.message ?? '正在调用 Dify 工作流…' }}</span>
      </div>

      <div v-else-if="aiTask?.status === 'failed'" class="ai-banner ai-banner--failed">
        <span>{{ aiTask.message ?? 'AI 排班生成失败' }}</span>
      </div>

      <p v-if="plan" class="plan-meta">
        计划状态：<ElTag>{{ plan.status }}</ElTag>
        <span v-if="plan.aiSummary"> · {{ plan.aiSummary }}</span>
      </p>

      <h4>排班明细</h4>
      <AdminFollowUpShiftCalendar
        v-loading="loading"
        :year="calendarYear"
        :month="calendarMonth"
        :shifts="shifts"
        :loading="loading"
        @update:year="calendarYear = $event"
        @update:month="calendarMonth = $event"
        @open-day="openShiftDay"
      />
      <ElEmpty v-if="!loading && !shifts.length" description="暂无排班，请点击 AI 生成" />
    </GlassCard>

    <GlassCard class="panel">
      <h3>患者监视分配</h3>
      <div class="monitoring-toolbar">
        <ElButton
          type="primary"
          plain
          :loading="randomAssigning"
          :disabled="!unassignedPatients.length || !followUpNurses.length"
          @click="randomAssignMonitoring"
        >
          随机分布
        </ElButton>
        <span v-if="monitoringLoad?.autoAssignEnabled" class="plan-meta">
          自动补位已开启：新纳入患者将分配给监视患者最少的医生
        </span>
      </div>
      <div v-if="monitoringLoad?.doctors?.length" class="monitoring-load-tags">
        <ElTag
          v-for="doctor in monitoringLoad.doctors"
          :key="doctor.employeeId"
          effect="plain"
          type="info"
        >
          {{ doctor.name ?? `员工 #${doctor.employeeId}` }}：{{ doctor.patientCount }} 人
        </ElTag>
      </div>
      <ElAlert
        v-if="unassignedPatients.length"
        type="warning"
        :closable="false"
        show-icon
        class="monitoring-alert"
        :title="`尚有 ${unassignedPatients.length} 名患者未分配监视医生`"
        description="请先点击「随机分布」完成首批分配；之后新纳入的患者将自动分配给监视患者最少的医生。分配后，对应随访医生才能在「我的监视患者」中看到并负责日常联系。"
      />
      <p class="plan-meta">
        科室随访可见患者共 {{ departmentPatients.length }} 人（已纳入 {{ enrolledPatients.length }} 人，待分配监视
        {{ unassignedPatients.length }} 人<template v-if="eligibleUnenrolledCount">，历史看诊结束未同步 {{ eligibleUnenrolledCount }} 人</template>）。
        看诊结束后系统会自动纳入随访；历史数据请点击「同步看诊结束患者」。排班使用已纳入且已分配监视医生的患者。
      </p>
      <ElTable :data="departmentPatients" stripe>
        <ElTableColumn prop="realName" label="患者" min-width="100" />
        <ElTableColumn prop="caseNumber" label="病历号" width="120" />
        <ElTableColumn label="随访状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.enrolled ? 'success' : 'info'" effect="plain">
              {{ row.enrolled ? '已纳入' : '待同步' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="监视医生" min-width="120">
          <template #default="{ row }">
            {{ row.monitoringEmployeeName ?? (row.monitoringEmployeeId ? `员工 #${row.monitoringEmployeeId}` : '未分配') }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="分配医生" min-width="180">
          <template #default="{ row }">
            <ElSelect
              v-if="row.enrolled && !row.monitoringEmployeeId"
              v-model="assignSelections[row.registerId]"
              placeholder="选择随访医生"
              style="width: 160px"
            >
              <ElOption
                v-for="nurse in followUpNurses"
                :key="nurse.id"
                :label="nurse.realname"
                :value="nurse.id"
              />
            </ElSelect>
            <span v-else class="plan-meta">已分配</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.enrolled && !row.monitoringEmployeeId"
              link
              type="primary"
              :loading="assigningId === row.registerId"
              @click="assignPatient(row)"
            >
              分配
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-if="!enrolledPatients.length" description="暂无已纳入随访患者，请先在随访工作台纳入" />
    </GlassCard>

    <GlassCard class="panel">
      <h3>监视调换申请</h3>
      <ElTable :data="pendingTransfers" stripe>
        <ElTableColumn prop="patientName" label="患者" width="100" />
        <ElTableColumn prop="fromEmployeeName" label="申请人" width="100" />
        <ElTableColumn prop="toEmployeeName" label="希望转至" width="100" />
        <ElTableColumn prop="reason" label="原因" min-width="180" />
        <ElTableColumn label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openTransferReview(row, true)">同意</ElButton>
            <ElButton link type="danger" @click="openTransferReview(row, false)">驳回</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-if="!pendingTransfers.length" description="暂无待审批调换" />
    </GlassCard>

    <GlassCard class="panel">
      <h3>调班申请通知</h3>
      <ElTable :data="pendingRequests" stripe>
        <ElTableColumn prop="employeeName" label="申请人" width="120" />
        <ElTableColumn prop="originalWorkDate" label="原日期" width="120" />
        <ElTableColumn prop="requestedWorkDate" label="申请调至" width="120" />
        <ElTableColumn prop="reason" label="原因" min-width="180" />
        <ElTableColumn label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openReview(row, true)">同意</ElButton>
            <ElButton link type="danger" @click="openReview(row, false)">驳回</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-if="!pendingRequests.length" description="暂无待审批调班" />
    </GlassCard>

    <AdminFollowUpShiftDayDialog
      v-model="shiftDayDialogVisible"
      :date="shiftDayDialogDate"
      :shifts="shiftDayDialogShifts"
    />

    <ElDialog
      v-model="transferReviewVisible"
      :title="transferReviewApprove ? '同意调换' : '驳回调换'"
      width="420px"
    >
      <ElForm label-width="80px">
        <ElFormItem label="备注">
          <ElInput v-model="transferReviewNote" type="textarea" :rows="2" placeholder="可选" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="transferReviewVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitTransferReview">确认</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="reviewDialogVisible" :title="reviewApprove ? '同意调班' : '驳回调班'" width="420px">
      <ElForm label-width="80px">
        <ElFormItem label="备注">
          <ElInput v-model="reviewNote" type="textarea" :rows="2" placeholder="可选" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="reviewDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitReview">确认</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  padding: var(--space-4);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.ai-banner {
  display: grid;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.ai-banner--failed {
  color: var(--el-color-danger);
  background: color-mix(in srgb, var(--el-color-danger) 8%, var(--color-bg-soft));
}

.plan-meta {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.panel h3,
.panel h4 {
  margin: 0 0 var(--space-3);
}

.monitoring-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-block-end: var(--space-3);
}

.monitoring-load-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-end: var(--space-3);
}

.monitoring-alert {
  margin-block-end: var(--space-3);
}
</style>
