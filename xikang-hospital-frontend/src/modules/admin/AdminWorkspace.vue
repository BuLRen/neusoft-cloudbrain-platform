<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useClientPagination } from '@/modules/admin/composables/useClientPagination'
import { registrationApi } from '@/shared/api/modules/registration'
import type { DepartmentOption, TriageDeskRecord } from '@/shared/types/registration'
import type { DoctorInfo } from '@/shared/api/modules/registration'

const authStore = useAuthStore()

const departments = ref<DepartmentOption[]>([])
const triagePending = ref<TriageDeskRecord[]>([])
const {
  page: triagePage,
  size: triagePageSize,
  total: triageTotal,
  totalPages: triageTotalPages,
  pagedRecords: triagePagedRecords,
  onPageChange: onTriagePageChange,
  onPageSizeChange: onTriagePageSizeChange,
  clampPageIfEmpty: clampTriagePage,
} = useClientPagination(triagePending, 10, { resetOnSourceChange: false })
const selectedTriageId = ref<number | undefined>()
const selectedTriage = ref<TriageDeskRecord | null>(null)
const triageDoctors = ref<DoctorInfo[]>([])

const triageConfirmForm = reactive({
  departmentId: undefined as number | undefined,
  departmentName: '',
  physicianId: undefined as number | undefined,
  physicianName: '',
  remark: '',
})

async function loadDepartments() {
  departments.value = await registrationApi.departments()
}

function updateTriageDepartmentName(departmentId?: number) {
  const department = departments.value.find((item) => item.id === departmentId)
  triageConfirmForm.departmentName = department?.name || triageConfirmForm.departmentName
}

async function loadTriageDoctors(departmentId?: number) {
  if (!departmentId) {
    triageDoctors.value = []
    return
  }
  try {
    triageDoctors.value = await registrationApi.getDoctorsByDepartment(departmentId)
  } catch {
    triageDoctors.value = []
  }
}

function updateTriagePhysicianName(physicianId?: number) {
  if (!physicianId) {
    triageConfirmForm.physicianName = ''
    return
  }
  const doctor = triageDoctors.value.find((d) => d.id === physicianId)
  if (doctor) {
    triageConfirmForm.physicianName = doctor.realname
  }
}

watch(
  () => triageConfirmForm.departmentId,
  (newId, oldId) => {
    if (newId === oldId) return
    if (triageConfirmForm.physicianId) {
      const stillExists = triageDoctors.value.some((d) => d.id === triageConfirmForm.physicianId)
      if (!stillExists) {
        triageConfirmForm.physicianId = undefined
        triageConfirmForm.physicianName = ''
      }
    }
    loadTriageDoctors(newId)
  },
)

async function loadTriagePending() {
  triagePending.value = await registrationApi.triagePending()
  clampTriagePage()
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
  await loadTriageDoctors(triageConfirmForm.departmentId)
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
  await Promise.all([loadDepartments(), loadTriagePending()])
})
</script>

<template>
  <div class="admin-workspace u-page-grid">
    <PageHeader
      title="AI 分诊台"
      description="处理 AI 导诊推荐的分诊记录，人工确认目标科室与医生后进入挂号流程。"
      eyebrow="管理员"
    >
      <template #actions>
        <ElButton @click="loadTriagePending">刷新分诊台</ElButton>
      </template>
    </PageHeader>

    <div class="triage-grid">
      <GlassCard class="triage-panel">
        <div class="panel-header">
          <h3>待确认分诊记录</h3>
          <StatusTag tone="warning">{{ triageTotal }} 条</StatusTag>
        </div>
        <div class="triage-list">
          <button
            v-for="item in triagePagedRecords"
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
          <ElEmpty v-if="triageTotal === 0" description="暂无待处理分诊记录" />
        </div>
        <div v-if="triageTotal > 0" class="admin-pagination-bar triage-pagination-bar">
          <p class="table-footer">
            共 {{ triageTotal }} 条待确认
            <template v-if="triageTotalPages > 0">，第 {{ triagePage }} / {{ triageTotalPages }} 页</template>
          </p>
          <ElPagination
            v-model:current-page="triagePage"
            v-model:page-size="triagePageSize"
            :total="triageTotal"
            :page-sizes="[5, 10, 20]"
            layout="total, sizes, prev, pager, next"
            small
            background
            @current-change="onTriagePageChange"
            @size-change="onTriagePageSizeChange"
          />
        </div>
      </GlassCard>

      <GlassCard class="triage-panel">
        <div class="panel-header">
          <h3>分诊确认</h3>
          <StatusTag v-if="selectedTriage" tone="primary">#{{ selectedTriage.id }}</StatusTag>
        </div>
        <div class="triage-confirm-body">
          <ElEmpty v-if="!selectedTriage" description="请选择一条分诊记录" />
          <template v-else>
            <ElDescriptions :column="1" border class="triage-summary">
              <ElDescriptionsItem label="患者">{{ selectedTriage.patientName || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="症状">{{ selectedTriage.symptoms || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="AI 推荐科室">
                <span class="ai-chip">{{ selectedTriage.recommendedDepartment || '-' }}</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="AI 推荐医生">
                <span class="ai-chip">{{ selectedTriage.recommendedPhysicianName || '-' }}</span>
              </ElDescriptionsItem>
            </ElDescriptions>
            <ElForm label-position="top" class="mt">
              <ElFormItem label="确认科室">
                <ElSelect
                  v-model="triageConfirmForm.departmentId"
                  filterable
                  placeholder="选择科室"
                  class="full-width"
                  @change="updateTriageDepartmentName"
                >
                  <ElOption
                    v-for="item in departments"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="确认医生">
                <ElSelect
                  v-model="triageConfirmForm.physicianId"
                  filterable
                  clearable
                  placeholder="请选择医生（按当前科室加载）"
                  class="full-width"
                  no-data-text="请先选择科室"
                  @change="updateTriagePhysicianName"
                >
                  <ElOption
                    v-for="d in triageDoctors"
                    :key="d.id"
                    :label="`${d.realname}${d.registName ? ' / ' + d.registName : ''}`"
                    :value="d.id"
                  />
                </ElSelect>
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
        </div>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped>
.admin-workspace {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.triage-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: stretch;
}

.triage-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5);
  min-height: 520px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block-end: var(--space-4);
  border-bottom: 1px solid var(--color-border);
  min-height: 52px;
}

.panel-header h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.triage-confirm-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.full-width {
  width: 100%;
}

.item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.triage-confirm-body .actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: auto;
  padding-block-start: var(--space-4);
}

.triage-list {
  display: grid;
  gap: var(--space-3);
  flex: 1;
  min-height: 0;
  overflow-y: visible;
  padding-inline-end: 4px;
}

.triage-pagination-bar {
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
}

.triage-item {
  position: relative;
  display: grid;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
}

.triage-item::before {
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

.triage-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.triage-item:hover::before {
  background: var(--gradient-primary);
}

.triage-item span {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.triage-item strong {
  font-size: 0.95rem;
  color: var(--color-text);
}

.triage-item.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  box-shadow: var(--shadow-md);
}

.triage-item.is-active::before {
  background: var(--gradient-primary);
}

.triage-summary {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.triage-summary :deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--color-text);
  inline-size: 96px;
  background: var(--color-table-header);
}

.ai-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-ai);
  background: rgba(124, 92, 255, 0.1);
  border: 1px solid rgba(124, 92, 255, 0.18);
}

.ai-chip::before {
  content: '';
  inline-size: 6px;
  block-size: 6px;
  border-radius: 999px;
  background: var(--gradient-ai);
}

.mt {
  margin-block-start: var(--space-4);
}

@media (max-width: 1200px) {
  .triage-grid {
    grid-template-columns: 1fr;
  }

  .triage-panel {
    min-height: auto;
  }
}
</style>
