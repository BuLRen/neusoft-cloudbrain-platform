<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElBadge,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
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
const unassignedPatients = ref<FollowUpDashboardPatient[]>([])
const followUpNurses = ref<FollowUpAdminRecord[]>([])
const assignSelections = ref<Record<number, number | undefined>>({})
const assigningId = ref<number | null>(null)
const aiTask = ref<{ status: string; message?: string; percent?: number } | null>(null)

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

const pendingCount = computed(() => pendingRequests.value.length)
const pendingTransferCount = computed(() => pendingTransfers.value.length)

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
    const [planRes, shiftRes, pendingRes, transferRes, taskRes, patientsRes, nursesPage] = await Promise.all([
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
    ])
    plan.value = planRes
    shifts.value = shiftRes
    pendingRequests.value = pendingRes
    pendingTransfers.value = transferRes
    aiTask.value = taskRes
    followUpNurses.value = nursesPage.records
    unassignedPatients.value = patientsRes.filter((p) => p.enrolled && !p.monitoringEmployeeId)
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
        ElMessage.success('AI 排班生成完成')
        await loadData()
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

watch(
  () => [filter.departmentId, filter.month],
  () => {
    void loadData()
  },
)

onMounted(async () => {
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
        <ElButton :disabled="!plan?.id" @click="publishPlan">发布排班</ElButton>
        <ElButton @click="loadData">刷新</ElButton>
      </div>

      <div v-if="aiTask?.status === 'running'" class="ai-banner">
        <ElProgress :percentage="aiTask.percent ?? 30" :stroke-width="8" />
        <span>{{ aiTask.message ?? '生成中…' }}</span>
      </div>

      <p v-if="plan" class="plan-meta">
        计划状态：<ElTag>{{ plan.status }}</ElTag>
        <span v-if="plan.aiSummary"> · {{ plan.aiSummary }}</span>
      </p>

      <h4>排班明细</h4>
      <ElTable v-loading="loading" :data="shifts" stripe>
        <ElTableColumn prop="workDate" label="日期" width="120" />
        <ElTableColumn prop="employeeName" label="随访医生" min-width="120" />
        <ElTableColumn prop="shiftType" label="班次" width="80" />
        <ElTableColumn label="联系任务" min-width="100">
          <template #default="{ row }">{{ row.contactTasks?.length ?? 0 }} 人</template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="100" />
      </ElTable>
      <ElEmpty v-if="!loading && !shifts.length" description="暂无排班，请点击 AI 生成" />
    </GlassCard>

    <GlassCard class="panel">
      <h3>患者监视分配</h3>
      <p class="plan-meta">已纳入随访但尚未指定监视医生的患者，请分配负责随访医生。</p>
      <ElTable :data="unassignedPatients" stripe>
        <ElTableColumn prop="realName" label="患者" min-width="100" />
        <ElTableColumn prop="caseNumber" label="病历号" width="120" />
        <ElTableColumn label="分配医生" min-width="180">
          <template #default="{ row }">
            <ElSelect
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
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <ElButton
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
      <ElEmpty v-if="!unassignedPatients.length" description="暂无待分配患者" />
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

.plan-meta {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.panel h3,
.panel h4 {
  margin: 0 0 var(--space-3);
}
</style>
