<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
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
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import type { DepartmentOption, RegistLevelOption, SchedulingOption, SettleCategoryOption, TriageDeskRecord } from '@/shared/types/registration'

const authStore = useAuthStore()
const activeTab = ref('schedule')

const departments = ref<DepartmentOption[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const settleCategories = ref<SettleCategoryOption[]>([])
const schedules = ref<SchedulingOption[]>([])
const triagePending = ref<TriageDeskRecord[]>([])
const selectedTriageId = ref<number | undefined>()
const selectedTriage = ref<TriageDeskRecord | null>(null)
const editingScheduleId = ref<number | undefined>()
const editingScheduleStatus = ref<number>(1)

const scheduleFilter = reactive({
  departmentId: undefined as number | undefined,
  date: new Date().toISOString().slice(0, 10),
})

const scheduleForm = reactive({
  physicianId: undefined as number | undefined,
  physicianName: '',
  departmentId: undefined as number | undefined,
  workDate: new Date().toISOString().slice(0, 10),
  timeSlot: 'morning',
  totalQuota: 20,
  remark: '',
})

const triageConfirmForm = reactive({
  departmentId: undefined as number | undefined,
  departmentName: '',
  physicianId: undefined as number | undefined,
  physicianName: '',
  remark: '',
})

const isEditingSchedule = computed(() => editingScheduleId.value !== undefined)

function statusTone(status?: number) {
  if (status === 1 || status === 2) return 'success'
  if (status === 0) return 'warning'
  return 'primary'
}

async function loadBaseData() {
  const [departmentList, levelList, settleList] = await Promise.all([
    registrationApi.departments(),
    registrationApi.registLevels(),
    registrationApi.settleCategories(),
  ])
  departments.value = departmentList
  registLevels.value = levelList
  settleCategories.value = settleList
}

function updateTriageDepartmentName(departmentId?: number) {
  const department = departments.value.find((item) => item.id === departmentId)
  triageConfirmForm.departmentName = department?.name || triageConfirmForm.departmentName
}

async function loadSchedules() {
  if (scheduleFilter.departmentId && scheduleFilter.date) {
    schedules.value = await registrationApi.schedulingOptions(scheduleFilter.departmentId, scheduleFilter.date)
    return
  }
  if (scheduleFilter.date) {
    schedules.value = await registrationApi.schedulingByDate(scheduleFilter.date)
    return
  }
  schedules.value = []
}

function resetScheduleForm() {
  editingScheduleId.value = undefined
  editingScheduleStatus.value = 1
  scheduleForm.physicianId = undefined
  scheduleForm.physicianName = ''
  scheduleForm.departmentId = undefined
  scheduleForm.workDate = new Date().toISOString().slice(0, 10)
  scheduleForm.timeSlot = 'morning'
  scheduleForm.totalQuota = 20
  scheduleForm.remark = ''
}

function startEditingSchedule(schedule: SchedulingOption) {
  editingScheduleId.value = schedule.id
  editingScheduleStatus.value = schedule.status ?? 1
  scheduleForm.physicianId = schedule.physicianId
  scheduleForm.physicianName = schedule.physicianName || ''
  scheduleForm.departmentId = schedule.departmentId
  scheduleForm.workDate = schedule.workDate || new Date().toISOString().slice(0, 10)
  scheduleForm.timeSlot = schedule.timeSlot || 'morning'
  scheduleForm.totalQuota = schedule.totalQuota || 20
  scheduleForm.remark = schedule.remark || ''
}

async function submitSchedule() {
  if (!scheduleForm.physicianId || !scheduleForm.physicianName.trim() || !scheduleForm.departmentId) {
    ElMessage.warning('请先填写医生、科室和日期')
    return
  }

  if (isEditingSchedule.value && editingScheduleId.value) {
    await registrationApi.updateScheduling(editingScheduleId.value, {
      totalQuota: scheduleForm.totalQuota,
      remark: scheduleForm.remark || undefined,
      status: editingScheduleStatus.value,
    })
    ElMessage.success('排班已更新')
  } else {
    const department = departments.value.find((item) => item.id === scheduleForm.departmentId)
    await registrationApi.createScheduling({
      physicianId: scheduleForm.physicianId,
      physicianName: scheduleForm.physicianName,
      departmentId: scheduleForm.departmentId,
      departmentName: department?.name || '',
      workDate: scheduleForm.workDate,
      timeSlot: scheduleForm.timeSlot,
      totalQuota: scheduleForm.totalQuota,
      remark: scheduleForm.remark || undefined,
    })
    ElMessage.success('排班创建成功')
  }

  await loadSchedules()
  resetScheduleForm()
}

async function updateScheduleStatus(id: number, status: number) {
  await registrationApi.updateScheduling(id, { status })
  if (editingScheduleId.value === id) {
    editingScheduleStatus.value = status
  }
  ElMessage.success('排班状态已更新')
  await loadSchedules()
}

async function deleteSchedule(id: number) {
  await registrationApi.deleteScheduling(id)
  if (editingScheduleId.value === id) {
    resetScheduleForm()
  }
  ElMessage.success('排班已删除')
  await loadSchedules()
}

async function loadTriagePending() {
  triagePending.value = await registrationApi.triagePending()
  if (triagePending.value.length > 0) {
    const currentId = selectedTriageId.value && triagePending.value.some((item) => item.id === selectedTriageId.value)
      ? selectedTriageId.value
      : triagePending.value[0].id
    selectedTriageId.value = currentId
    await loadTriageDetail(currentId)
  } else {
    selectedTriageId.value = undefined
    selectedTriage.value = null
  }
}

async function loadTriageDetail(id?: number) {
  if (!id) {
    selectedTriage.value = null
    return
  }
  selectedTriage.value = await registrationApi.triageDetail(id)
  triageConfirmForm.departmentId = selectedTriage.value.recommendedDepartmentId
  triageConfirmForm.departmentName = selectedTriage.value.recommendedDepartment || ''
  triageConfirmForm.physicianId = selectedTriage.value.recommendedPhysicianId
  triageConfirmForm.physicianName = selectedTriage.value.recommendedPhysicianName || ''
}

async function confirmTriage() {
  if (!selectedTriageId.value || !triageConfirmForm.departmentId || !triageConfirmForm.departmentName.trim()) {
    ElMessage.warning('请先选择分诊记录并确认目标科室')
    return
  }
  await registrationApi.confirmTriage(selectedTriageId.value, {
    departmentId: triageConfirmForm.departmentId,
    departmentName: triageConfirmForm.departmentName,
    physicianId: triageConfirmForm.physicianId,
    physicianName: triageConfirmForm.physicianName || undefined,
    operatorName: authStore.role,
    remark: triageConfirmForm.remark || undefined,
  })
  ElMessage.success('分诊台确认成功')
  await loadTriagePending()
}

async function cancelTriage() {
  if (!selectedTriageId.value) {
    ElMessage.warning('请先选择一条分诊记录')
    return
  }
  await registrationApi.cancelTriage(selectedTriageId.value, triageConfirmForm.remark || '管理员取消分诊')
  ElMessage.success('分诊记录已取消')
  await loadTriagePending()
}

onMounted(async () => {
  await Promise.all([loadBaseData(), loadSchedules(), loadTriagePending()])
})
</script>

<template>
  <div class="admin-workspace u-page-grid">
    <PageHeader
      title="管理员支撑工作台"
      description="当前范围仅覆盖后端已提供的能力：医生排班管理、AI 分诊台，以及基础数据查看。由于没有独立医生目录接口，新增排班时保留医生 ID / 姓名手输。"
      eyebrow="Role B / Admin"
    >
      <template #actions>
        <ElButton @click="loadTriagePending">刷新分诊台</ElButton>
        <ElButton type="primary" @click="loadBaseData">刷新基础数据</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="flow-card">
      <ElTabs v-model="activeTab">
        <ElTabPane label="医生排班" name="schedule">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>{{ isEditingSchedule ? `编辑排班 #${editingScheduleId}` : '新增排班' }}</h3>
                <StatusTag :tone="isEditingSchedule ? 'warning' : 'primary'">{{ isEditingSchedule ? '编辑模式' : '新建模式' }}</StatusTag>
              </div>
              <p class="schedule-note">
                {{ isEditingSchedule ? '当前后端更新接口只会保存号源、备注和状态；医生、科室、日期、时段仅作为上下文展示。' : '新增排班时需要手工输入医生 ID 和姓名。' }}
              </p>
              <ElForm label-position="top" class="form-grid">
                <ElFormItem label="医生 ID">
                  <ElInputNumber v-model="scheduleForm.physicianId" :min="1" :controls="false" class="field" :disabled="isEditingSchedule" />
                </ElFormItem>
                <ElFormItem label="医生姓名">
                  <ElInput v-model="scheduleForm.physicianName" :disabled="isEditingSchedule" />
                </ElFormItem>
                <ElFormItem label="科室">
                  <ElSelect v-model="scheduleForm.departmentId" filterable placeholder="选择科室" :disabled="isEditingSchedule">
                    <ElOption v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="日期">
                  <ElInput v-model="scheduleForm.workDate" type="date" :disabled="isEditingSchedule" />
                </ElFormItem>
                <ElFormItem label="时段">
                  <ElSelect v-model="scheduleForm.timeSlot" :disabled="isEditingSchedule">
                    <ElOption label="上午" value="morning" />
                    <ElOption label="下午" value="afternoon" />
                    <ElOption label="晚上" value="evening" />
                    <ElOption label="全天" value="all_day" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="总号源">
                  <ElInputNumber v-model="scheduleForm.totalQuota" :min="1" class="field" />
                </ElFormItem>
                <ElFormItem v-if="isEditingSchedule" label="状态">
                  <ElSelect v-model="editingScheduleStatus">
                    <ElOption label="停用" :value="0" />
                    <ElOption label="启用" :value="1" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="备注" class="full-width">
                  <ElInput v-model="scheduleForm.remark" type="textarea" :rows="3" />
                </ElFormItem>
              </ElForm>
              <div class="actions">
                <ElButton type="primary" @click="submitSchedule">{{ isEditingSchedule ? '保存排班' : '新增排班' }}</ElButton>
                <ElButton v-if="isEditingSchedule" @click="resetScheduleForm">返回新建模式</ElButton>
              </div>
            </section>

            <section>
              <div class="section-title">
                <h3>排班列表</h3>
                <div class="actions actions--inline actions--compact">
                  <ElSelect v-model="scheduleFilter.departmentId" clearable placeholder="筛选科室">
                    <ElOption v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
                  </ElSelect>
                  <ElInput v-model="scheduleFilter.date" type="date" />
                  <ElButton @click="loadSchedules">查询</ElButton>
                </div>
              </div>
              <ElTable :data="schedules">
                <ElTableColumn prop="physicianName" label="医生" min-width="120" />
                <ElTableColumn prop="departmentName" label="科室" min-width="120" />
                <ElTableColumn prop="workDate" label="日期" min-width="120" />
                <ElTableColumn prop="timeSlotName" label="时段" min-width="120" />
                <ElTableColumn label="状态" min-width="120">
                  <template #default="{ row }">
                    <StatusTag :tone="statusTone(row.status)">{{ row.statusName || '-' }}</StatusTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" min-width="260" fixed="right">
                  <template #default="{ row }">
                    <ElButton link type="primary" @click="startEditingSchedule(row)">编辑</ElButton>
                    <ElButton link type="primary" @click="updateScheduleStatus(row.id, 1)">启用</ElButton>
                    <ElButton link @click="updateScheduleStatus(row.id, 0)">停用</ElButton>
                    <ElButton link type="danger" @click="deleteSchedule(row.id)">删除</ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </section>
          </div>
        </ElTabPane>

        <ElTabPane label="AI 分诊台" name="triage">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>待确认分诊记录</h3>
                <StatusTag tone="warning">{{ triagePending.length }} 条</StatusTag>
              </div>
              <div class="triage-list">
                <button
                  v-for="item in triagePending"
                  :key="item.id"
                  class="triage-item"
                  :class="{ 'is-active': item.id === selectedTriageId }"
                  type="button"
                  @click="selectedTriageId = item.id; loadTriageDetail(item.id)"
                >
                  <strong>#{{ item.id }} · {{ item.patientName || '-' }}</strong>
                  <span>{{ item.symptoms || '-' }}</span>
                  <div class="item-meta">
                    <StatusTag :tone="item.riskLevel === 'high' ? 'danger' : item.riskLevel === 'medium' ? 'warning' : 'success'">
                      {{ item.riskLevelName || item.riskLevel || '-' }}
                    </StatusTag>
                    <span>{{ item.recommendedDepartment || '-' }}</span>
                  </div>
                </button>
                <ElEmpty v-if="triagePending.length === 0" description="暂无待处理分诊记录" />
              </div>
            </section>

            <section>
              <h3>分诊确认</h3>
              <ElEmpty v-if="!selectedTriage" description="请选择一条分诊记录" />
              <template v-else>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="患者">{{ selectedTriage.patientName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="症状">{{ selectedTriage.symptoms || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="推荐科室">{{ selectedTriage.recommendedDepartment || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="推荐医生">{{ selectedTriage.recommendedPhysicianName || '-' }}</ElDescriptionsItem>
                </ElDescriptions>
                <ElForm label-position="top" class="mt">
                  <ElFormItem label="确认科室">
                    <ElSelect v-model="triageConfirmForm.departmentId" filterable placeholder="选择科室" @change="updateTriageDepartmentName">
                    <ElOption
                      v-for="item in departments"
                      :key="item.id"
                      :label="item.name"
                      :value="item.id"
                    />
                  </ElSelect>
                  </ElFormItem>
                  <ElFormItem label="确认科室名称">
                    <ElInput v-model="triageConfirmForm.departmentName" />
                  </ElFormItem>
                  <ElFormItem label="确认医生 ID">
                    <ElInputNumber v-model="triageConfirmForm.physicianId" :min="1" :controls="false" class="field" />
                  </ElFormItem>
                  <ElFormItem label="确认医生姓名">
                    <ElInput v-model="triageConfirmForm.physicianName" />
                  </ElFormItem>
                  <ElFormItem label="备注">
                    <ElInput v-model="triageConfirmForm.remark" type="textarea" :rows="3" />
                  </ElFormItem>
                </ElForm>
                <div class="actions">
                  <ElButton type="primary" @click="confirmTriage">确认分诊</ElButton>
                  <ElButton type="danger" plain @click="cancelTriage">取消分诊</ElButton>
                </div>
              </template>
            </section>
          </div>
        </ElTabPane>

        <ElTabPane label="基础数据" name="base">
          <div class="base-grid">
            <GlassCard class="inner-card">
              <div class="section-title">
                <h3>科室</h3>
                <StatusTag tone="primary">{{ departments.length }} 条</StatusTag>
              </div>
              <ElTable :data="departments">
                <ElTableColumn prop="name" label="名称" min-width="160" />
                <ElTableColumn prop="code" label="编码" min-width="120" />
                <ElTableColumn prop="type" label="类型" min-width="120" />
              </ElTable>
            </GlassCard>

            <GlassCard class="inner-card">
              <div class="section-title">
                <h3>挂号级别</h3>
                <StatusTag tone="primary">{{ registLevels.length }} 条</StatusTag>
              </div>
              <ElTable :data="registLevels">
                <ElTableColumn prop="name" label="名称" min-width="140" />
                <ElTableColumn prop="price" label="价格" min-width="100" />
                <ElTableColumn prop="description" label="说明" min-width="180" />
              </ElTable>
            </GlassCard>

            <GlassCard class="inner-card">
              <div class="section-title">
                <h3>结算类别</h3>
                <StatusTag tone="primary">{{ settleCategories.length }} 条</StatusTag>
              </div>
              <ElTable :data="settleCategories">
                <ElTableColumn prop="name" label="名称" min-width="140" />
                <ElTableColumn prop="code" label="编码" min-width="120" />
                <ElTableColumn prop="description" label="说明" min-width="180" />
              </ElTable>
            </GlassCard>
          </div>
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.flow-card,
.inner-card {
  padding: var(--space-5);
}

.split-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.85fr) minmax(0, 1fr);
  gap: var(--space-5);
}

.base-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
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

.schedule-note {
  margin-block: var(--space-3) var(--space-4);
  color: var(--color-text-muted);
  line-height: 1.7;
}

.section-title,
.item-meta,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.actions {
  flex-wrap: wrap;
  justify-content: flex-start;
  margin-block-start: var(--space-4);
}

.actions--inline {
  justify-content: flex-end;
}

.actions--compact {
  margin-block-start: 0;
}

.triage-list {
  display: grid;
  gap: var(--space-3);
}

.triage-item {
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

.triage-item span {
  color: var(--color-text-muted);
}

.triage-item.is-active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.mt {
  margin-block-start: var(--space-4);
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

@media (max-width: 1200px) {
  .base-grid,
  .form-grid,
  .split-grid {
    grid-template-columns: 1fr;
  }
}
</style>
