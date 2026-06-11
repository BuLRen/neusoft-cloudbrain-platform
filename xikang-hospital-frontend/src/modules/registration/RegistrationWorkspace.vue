<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi, scheduleApi } from '@/shared/api/modules/registration'
import type {
  DepartmentOption,
  ExpenseRecord,
  ExpenseRecordSortBy,
  ExpenseRecordSortDir,
  PendingChargeItem,
  RegistrationCreateResult,
  RegistLevelOption,
  SchedulingOption,
  SettleCategoryOption,
} from '@/shared/types/registration'
import type { TriageAnalysisResult } from '@/shared/types/ai'

const TRIAGE_STORAGE_KEY = 'xikang-patient-triage-draft'

interface StoredTriageDraft {
  patientId?: number
  patientName?: string
  symptoms?: string
  result?: TriageAnalysisResult | null
}

const authStore = useAuthStore()
const activeTab = ref('register')
const loading = ref(false)
const scheduleLoading = ref(false)
const chargeLoading = ref(false)

const departments = ref<DepartmentOption[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const settleCategories = ref<SettleCategoryOption[]>([])
const schedulingOptions = ref<SchedulingOption[]>([])
const selectedSchedule = ref<SchedulingOption | null>(null)
const registrationResult = ref<RegistrationCreateResult | null>(null)
const triageDraft = ref<StoredTriageDraft | null>(null)
const pendingCharges = ref<PendingChargeItem[]>([])
const selectedChargeItemIds = ref<number[]>([])
const expenseRecords = ref<ExpenseRecord[]>([])

const registrationForm = reactive({
  patientId: undefined as number | undefined,
  patientName: '',
  patientPhone: '',
  idCard: '',
  departmentId: undefined as number | undefined,
  schedulingId: undefined as number | undefined,
  physicianId: undefined as number | undefined,
  physicianName: '',
  visitDate: new Date().toISOString().slice(0, 10),
  visitTime: '上午',
  complaint: '',
  registLevelId: undefined as number | undefined,
  settleCategoryId: undefined as number | undefined,
})

const chargeRegisterId = ref<number | undefined>()
const chargePatientId = ref<number | undefined>()
const recordStatus = ref<number | undefined>()
const visitDateRange = ref<[string, string] | null>(null)
const sortBy = ref<ExpenseRecordSortBy>('payTime')
const sortDir = ref<ExpenseRecordSortDir>('desc')

const inferredOperatorId = computed(() => {
  const parsed = Number(authStore.userId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})

const selectedRegistLevel = computed(() => registLevels.value.find((item) => item.id === registrationForm.registLevelId) || null)
const selectedSettleCategory = computed(() => settleCategories.value.find((item) => item.id === registrationForm.settleCategoryId) || null)

const chargeTotal = computed(() => {
  const selected = pendingCharges.value.filter((item) => selectedChargeItemIds.value.includes(item.id))
  return selected.reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
})

// 状态优先级：待缴费 0 → 已缴费 1 → 已退费/作废 2、3 → 末位
function expenseStatusRank(status?: number) {
  if (status === 0) return 0
  if (status === 1) return 1
  return 2
}

function parseExpenseTime(value?: string | null): number {
  if (!value) return Number.POSITIVE_INFINITY
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : Number.POSITIVE_INFINITY
}

const sortedExpenseRecords = computed(() => {
  const list = expenseRecords.value.slice()
  const dir = sortDir.value === 'asc' ? 1 : -1
  const key = sortBy.value
  return list.sort((a, b) => {
    const rankDiff = expenseStatusRank(a.status) - expenseStatusRank(b.status)
    if (rankDiff !== 0) return rankDiff
    const timeDiff = (parseExpenseTime(a[key]) - parseExpenseTime(b[key])) * dir
    if (timeDiff !== 0) return timeDiff
    return a.id - b.id
  })
})

function levelTone(name?: string) {
  return name?.includes('专家') ? 'warning' : 'primary'
}

function loadTriageDraft() {
  const raw = window.sessionStorage.getItem(TRIAGE_STORAGE_KEY)
  if (!raw) {
    triageDraft.value = null
    return
  }
  try {
    triageDraft.value = JSON.parse(raw) as StoredTriageDraft
  } catch {
    triageDraft.value = null
    window.sessionStorage.removeItem(TRIAGE_STORAGE_KEY)
  }
}

function importTriageDraft() {
  if (!triageDraft.value?.result) {
    ElMessage.warning('当前没有可导入的导诊结果')
    return
  }
  registrationForm.patientId = triageDraft.value.patientId || registrationForm.patientId
  registrationForm.patientName = triageDraft.value.patientName || registrationForm.patientName
  registrationForm.complaint = triageDraft.value.symptoms || registrationForm.complaint
  const departmentId = triageDraft.value.result.recommendedDepartmentId
  if (departmentId && departments.value.some((item) => item.id === departmentId)) {
    registrationForm.departmentId = departmentId
  } else if (triageDraft.value.result.recommendedDepartment) {
    const matched = departments.value.find((item) => item.name === triageDraft.value?.result?.recommendedDepartment)
    registrationForm.departmentId = matched?.id || registrationForm.departmentId
  }
  ElMessage.success('已导入导诊建议，可继续选择排班并挂号')
}

async function loadOptions() {
  const [departmentList, levelList, settleList] = await Promise.all([
    registrationApi.departments(),
    registrationApi.registLevels(),
    registrationApi.settleCategories(),
  ])
  departments.value = departmentList
  registLevels.value = levelList
  settleCategories.value = settleList
}

async function loadScheduling() {
  if (!registrationForm.departmentId || !registrationForm.visitDate) {
    schedulingOptions.value = []
    selectedSchedule.value = null
    return
  }
  scheduleLoading.value = true
  try {
    schedulingOptions.value = await scheduleApi.schedulingOptions(registrationForm.departmentId, registrationForm.visitDate)
    if (registrationForm.schedulingId) {
      const matched = schedulingOptions.value.find((item) => item.id === registrationForm.schedulingId)
      selectedSchedule.value = matched || null
    }
  } finally {
    scheduleLoading.value = false
  }
}

async function applyScheduling(id?: number) {
  if (!id) {
    selectedSchedule.value = null
    registrationForm.physicianId = undefined
    registrationForm.physicianName = ''
    registrationForm.registLevelId = undefined
    return
  }
  selectedSchedule.value = await scheduleApi.schedulingDetail(id)
  registrationForm.physicianId = selectedSchedule.value.physicianId
  registrationForm.physicianName = selectedSchedule.value.physicianName || ''
  registrationForm.visitTime = selectedSchedule.value.timeSlotName || selectedSchedule.value.timeSlot || registrationForm.visitTime
  registrationForm.registLevelId = selectedSchedule.value.registLevelId || registrationForm.registLevelId
}

async function submitRegistration() {
  if (
    !registrationForm.patientId
    || !registrationForm.patientName.trim()
    || !registrationForm.departmentId
    || !registrationForm.schedulingId
    || !registrationForm.visitDate
    || !registrationForm.registLevelId
    || !registrationForm.settleCategoryId
  ) {
    ElMessage.warning('请先完善患者信息、科室、可用排班、挂号级别和结算类别')
    return
  }
  loading.value = true
  try {
    registrationResult.value = await registrationApi.createRegistration({
      patientId: registrationForm.patientId,
      patientName: registrationForm.patientName,
      patientPhone: registrationForm.patientPhone || undefined,
      idCard: registrationForm.idCard || undefined,
      departmentId: registrationForm.departmentId,
      physicianId: registrationForm.physicianId,
      physicianName: registrationForm.physicianName || undefined,
      schedulingId: registrationForm.schedulingId,
      visitDate: registrationForm.visitDate,
      visitTime: registrationForm.visitTime || undefined,
      complaint: registrationForm.complaint || undefined,
      registerType: selectedRegistLevel.value?.name?.includes('专家') ? 1 : 0,
      registLevelId: registrationForm.registLevelId,
      settleCategoryId: registrationForm.settleCategoryId,
      operatorId: inferredOperatorId.value,
      operatorName: authStore.role,
      aiTriageResult: triageDraft.value?.result || null,
    })
    chargeRegisterId.value = registrationResult.value.id
    chargePatientId.value = registrationForm.patientId
    ElMessage.success('挂号成功，已切换到收费区')
    activeTab.value = 'charge'
    await Promise.all([loadPendingCharges(), loadExpenseRecords()])
  } finally {
    loading.value = false
  }
}

async function loadPendingCharges() {
  if (chargeRegisterId.value) {
    pendingCharges.value = await registrationApi.pendingChargesByRegister(chargeRegisterId.value)
    return
  }
  if (chargePatientId.value) {
    pendingCharges.value = await registrationApi.pendingChargesByPatient(chargePatientId.value)
    return
  }
  pendingCharges.value = []
}

function onChargeSelectionChange(rows: PendingChargeItem[]) {
  selectedChargeItemIds.value = rows.map((item) => item.id)
}

async function submitCharge() {
  if (!chargeRegisterId.value) {
    ElMessage.warning('请先填写或完成挂号，拿到 registerId')
    return
  }
  chargeLoading.value = true
  try {
    await registrationApi.charge({
      registerId: chargeRegisterId.value,
      itemIds: selectedChargeItemIds.value.length > 0 ? selectedChargeItemIds.value : undefined,
      operatorId: inferredOperatorId.value,
      operatorName: authStore.role,
    })
    ElMessage.success('收费成功')
    selectedChargeItemIds.value = []
    await Promise.all([loadPendingCharges(), loadExpenseRecords()])
  } finally {
    chargeLoading.value = false
  }
}

async function loadExpenseRecords() {
  const [startDate, endDate] = visitDateRange.value || []
  expenseRecords.value = await registrationApi.expenseRecords({
    registerId: chargeRegisterId.value,
    patientId: chargePatientId.value,
    status: recordStatus.value,
    startDate,
    endDate,
  })
}

async function refundExpenseRecord(expenseRecordId: number) {
  await registrationApi.refund({ expenseRecordId, operatorId: inferredOperatorId.value, operatorName: authStore.role })
  ElMessage.success('退费成功')
  await Promise.all([loadPendingCharges(), loadExpenseRecords()])
}

async function refundCurrentRegister() {
  if (!chargeRegisterId.value) {
    ElMessage.warning('请先提供 registerId')
    return
  }
  await registrationApi.refundByRegister(chargeRegisterId.value, { operatorId: inferredOperatorId.value, operatorName: authStore.role })
  ElMessage.success('已发起按挂号退费')
  await Promise.all([loadPendingCharges(), loadExpenseRecords()])
}

async function cancelCurrentRegistration() {
  if (!chargeRegisterId.value) {
    ElMessage.warning('请先提供 registerId')
    return
  }
  await registrationApi.cancelRegistration(chargeRegisterId.value)
  ElMessage.success('退号成功')
  await loadExpenseRecords()
}

watch(
  () => [registrationForm.departmentId, registrationForm.visitDate],
  () => {
    registrationForm.schedulingId = undefined
    void loadScheduling()
  },
)

watch(
  () => registrationForm.schedulingId,
  (value) => {
    void applyScheduling(value)
  },
)

onMounted(async () => {
  loadTriageDraft()
  await loadOptions()
})
</script>

<template>
  <div class="registration-workspace u-page-grid">
    <PageHeader
      title="挂号收费工作台"
      description="围绕窗口挂号、待缴费项目、收费结算、费用记录、退费与退号形成入口闭环。导诊结果通过 sessionStorage 在患者端和挂号端之间传递。"
      eyebrow="Role B / Registration"
    >
      <template #actions>
        <ElButton @click="loadTriageDraft">读取导诊草稿</ElButton>
        <ElButton type="primary" @click="loadOptions">刷新基础数据</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="guide-card">
      <div class="guide-grid">
        <div>
          <div class="section-title">
            <h3>导诊结果复用</h3>
            <StatusTag :tone="triageDraft?.result ? 'success' : 'warning'">{{ triageDraft?.result ? '可导入' : '暂无导诊草稿' }}</StatusTag>
          </div>
          <p>患者端点击“带入挂号”后，当前会话会把推荐科室、风险等级和症状摘要写入 sessionStorage。挂号员可一键导入，避免重复录入。</p>
        </div>
        <div class="actions actions--top">
          <ElButton @click="importTriageDraft">导入导诊结果</ElButton>
          <ElButton @click="loadScheduling">刷新排班</ElButton>
        </div>
      </div>
      <ElDescriptions v-if="triageDraft?.result" :column="3" border class="mt">
        <ElDescriptionsItem label="推荐科室">{{ triageDraft.result.recommendedDepartment || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="风险等级">
          <StatusTag :tone="triageDraft.result.riskLevel === 'high' ? 'danger' : triageDraft.result.riskLevel === 'medium' ? 'warning' : 'success'">
            {{ triageDraft.result.riskLevel || '-' }}
          </StatusTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="推荐医生">{{ triageDraft.result.recommendedDoctors?.map((item) => item.name).join('、') || '-' }}</ElDescriptionsItem>
      </ElDescriptions>
    </GlassCard>

    <GlassCard class="flow-card">
      <ElTabs v-model="activeTab">
        <ElTabPane label="窗口挂号" name="register">
          <div class="split-grid">
            <section>
              <ElForm label-position="top" class="form-grid">
                <ElFormItem label="patientId">
                  <ElInputNumber v-model="registrationForm.patientId" :min="1" :controls="false" class="field" />
                </ElFormItem>
                <ElFormItem label="患者姓名">
                  <ElInput v-model="registrationForm.patientName" />
                </ElFormItem>
                <ElFormItem label="联系电话">
                  <ElInput v-model="registrationForm.patientPhone" />
                </ElFormItem>
                <ElFormItem label="身份证号">
                  <ElInput v-model="registrationForm.idCard" />
                </ElFormItem>
                <ElFormItem label="挂号科室">
                  <ElSelect v-model="registrationForm.departmentId" filterable placeholder="选择科室">
                    <ElOption v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="就诊日期">
                  <ElInput v-model="registrationForm.visitDate" type="date" />
                </ElFormItem>
                <ElFormItem label="挂号级别">
                  <ElSelect v-model="registrationForm.registLevelId" placeholder="选择排班后自动带出挂号级别" :disabled="!!selectedSchedule">
                    <ElOption v-for="item in registLevels" :key="item.id" :label="`${item.name}${item.price ? ` / ${item.price}元` : ''}`" :value="item.id" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="结算类别">
                  <ElSelect v-model="registrationForm.settleCategoryId" placeholder="选择结算类别">
                    <ElOption v-for="item in settleCategories" :key="item.id" :label="item.name" :value="item.id" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="主诉 / 导诊摘要" class="full-width">
                  <ElInput v-model="registrationForm.complaint" type="textarea" :rows="3" />
                </ElFormItem>
              </ElForm>
            </section>

            <section>
              <div class="section-title">
                <h3>可用排班</h3>
                <StatusTag :tone="scheduleLoading ? 'warning' : 'primary'">{{ schedulingOptions.length }} 个</StatusTag>
              </div>
              <ElAlert v-if="scheduleLoading" type="info" :closable="false" title="正在加载排班" />
              <div class="schedule-list">
                <button
                  v-for="item in schedulingOptions"
                  :key="item.id"
                  class="schedule-item"
                  :class="{ 'is-active': item.id === registrationForm.schedulingId }"
                  type="button"
                  @click="registrationForm.schedulingId = item.id"
                >
                  <strong>{{ item.physicianName || '待分配医生' }}</strong>
                  <span>{{ item.timeSlotName || item.timeSlot || '-' }}</span>
                  <StatusTag :tone="levelTone(item.registLevelName)">{{ item.registLevelName || '挂号' }} · 余号 {{ item.availableQuota ?? '-' }}</StatusTag>
                </button>
                <ElEmpty v-if="!scheduleLoading && schedulingOptions.length === 0" description="选择科室与日期后查看排班" />
              </div>
              <ElDescriptions v-if="selectedSchedule" :column="1" border class="mt">
                <ElDescriptionsItem label="科室">{{ selectedSchedule.departmentName || '-' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="医生">{{ selectedSchedule.physicianName || '-' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="时间段">{{ selectedSchedule.timeSlotName || selectedSchedule.timeSlot || '-' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="可挂号数">{{ selectedSchedule.availableQuota ?? '-' }}</ElDescriptionsItem>
              </ElDescriptions>
            </section>
          </div>
          <div class="actions">
            <ElButton type="primary" :loading="loading" @click="submitRegistration">确认挂号</ElButton>
          </div>
          <ElDescriptions v-if="registrationResult" :column="3" border class="mt">
            <ElDescriptionsItem label="挂号单号">{{ registrationResult.id }}</ElDescriptionsItem>
            <ElDescriptionsItem label="科室">{{ registrationResult.departmentName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="医生">{{ registrationResult.physicianName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊日期">{{ registrationResult.visitDate || registrationForm.visitDate || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊时段">{{ registrationResult.visitTime || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="挂号级别">{{ registrationResult.registLevelName || selectedRegistLevel?.name || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="结算类别">{{ registrationResult.settleCategoryName || selectedSettleCategory?.name || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="费用">{{ registrationResult.amount ?? '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="状态">{{ registrationResult.statusName || '-' }}</ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>

        <ElTabPane label="收费结算" name="charge">
          <div class="filter-grid">
            <ElForm label-position="top" class="filter-form">
              <ElFormItem label="registerId">
                <ElInputNumber v-model="chargeRegisterId" :min="1" :controls="false" class="field" />
              </ElFormItem>
              <ElFormItem label="patientId（备选）">
                <ElInputNumber v-model="chargePatientId" :min="1" :controls="false" class="field" />
              </ElFormItem>
            </ElForm>
            <div class="actions actions--top">
              <ElButton @click="loadPendingCharges">查询待缴费项</ElButton>
              <ElButton type="primary" :loading="chargeLoading" @click="submitCharge">提交收费</ElButton>
            </div>
          </div>
          <ElTable :data="pendingCharges" @selection-change="onChargeSelectionChange">
            <ElTableColumn type="selection" width="48" />
            <ElTableColumn prop="itemName" label="收费项目" min-width="180" />
            <ElTableColumn prop="categoryName" label="类别" min-width="120" />
            <ElTableColumn prop="quantity" label="数量" min-width="80" />
            <ElTableColumn prop="unitPrice" label="单价" min-width="100" />
            <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
          </ElTable>
          <div class="summary-bar">
            <StatusTag :tone="selectedChargeItemIds.length > 0 ? 'success' : 'warning'">已选 {{ selectedChargeItemIds.length }} 项</StatusTag>
            <strong>合计：{{ chargeTotal.toFixed(2) }} 元</strong>
          </div>
        </ElTabPane>

        <ElTabPane label="费用记录 / 退费 / 退号" name="records">
          <div class="filter-grid">
            <ElForm label-position="top" class="filter-form filter-form--four">
              <ElFormItem label="registerId">
                <ElInputNumber v-model="chargeRegisterId" :min="1" :controls="false" class="field" />
              </ElFormItem>
              <ElFormItem label="patientId">
                <ElInputNumber v-model="chargePatientId" :min="1" :controls="false" class="field" />
              </ElFormItem>
              <ElFormItem label="状态筛选">
                <ElSelect v-model="recordStatus" clearable placeholder="全部状态">
                  <ElOption label="待缴费" :value="0" />
                  <ElOption label="已收费" :value="1" />
                  <ElOption label="已退费" :value="2" />
                  <ElOption label="已作废" :value="3" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="时间范围（按收费时间）">
                <ElDatePicker
                  v-model="visitDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  clearable
                  class="field"
                />
              </ElFormItem>
              <ElFormItem label="排序字段">
                <ElSelect v-model="sortBy" class="field">
                  <ElOption label="收费时间" value="payTime" />
                  <ElOption label="开单时间" value="createTime" />
                  <ElOption label="退费时间" value="refundTime" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="排序方向">
                <ElRadioGroup v-model="sortDir">
                  <ElRadio value="desc">降序</ElRadio>
                  <ElRadio value="asc">升序</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElForm>
            <div class="actions actions--top">
              <ElButton @click="loadExpenseRecords">查询费用记录</ElButton>
              <ElButton @click="refundCurrentRegister">按挂号退费</ElButton>
              <ElButton type="danger" plain @click="cancelCurrentRegistration">退号</ElButton>
            </div>
          </div>
          <ElTable :data="sortedExpenseRecords">
            <ElTableColumn prop="itemName" label="项目" min-width="180" />
            <ElTableColumn prop="categoryName" label="类别" min-width="120" />
            <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
            <ElTableColumn prop="statusName" label="状态" min-width="120" />
            <ElTableColumn prop="operatorName" label="操作人" min-width="120" />
            <ElTableColumn prop="createTime" label="开单时间" min-width="160" />
            <ElTableColumn prop="payTime" label="收费时间" min-width="160" />
            <ElTableColumn prop="refundTime" label="退费时间" min-width="160" />
            <ElTableColumn label="操作" min-width="140" fixed="right">
              <template #default="{ row }">
                <ElButton link type="danger" :disabled="row.status !== 1" @click="refundExpenseRecord(row.id)">退费</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.guide-card,
.flow-card {
  padding: var(--space-5);
}

.guide-grid,
.split-grid,
.filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
}

.split-grid {
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-4);
}

.full-width {
  grid-column: 1 / -1;
}

.field {
  width: 100%;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.schedule-list {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.schedule-item {
  display: grid;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.schedule-item span,
.guide-card p {
  color: var(--color-text-muted);
}

.schedule-item .status-tag {
  width: fit-content;
}

.schedule-item.is-active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-4);
}

.filter-form--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.filter-form--four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.actions--top {
  align-content: start;
  margin-block-start: 0;
}

.mt {
  margin-block-start: var(--space-4);
}

.summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .guide-grid,
  .split-grid,
  .filter-grid,
  .form-grid,
  .filter-form,
  .filter-form--three,
  .filter-form--four {
    grid-template-columns: 1fr;
  }
}
</style>
