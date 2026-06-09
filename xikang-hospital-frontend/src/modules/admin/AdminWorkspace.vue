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
import type { DepartmentOption, RegistLevelOption, SettleCategoryOption, TriageDeskRecord } from '@/shared/types/registration'
import type { DoctorInfo } from '@/shared/api/modules/registration'

const authStore = useAuthStore()
const activeTab = ref('triage')

const departments = ref<DepartmentOption[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const settleCategories = ref<SettleCategoryOption[]>([])
const triagePending = ref<TriageDeskRecord[]>([])
const selectedTriageId = ref<number | undefined>()
const selectedTriage = ref<TriageDeskRecord | null>(null)
// 分诊确认使用的医生下拉（按所选科室动态拉取）
const triageDoctors = ref<DoctorInfo[]>([])

const triageConfirmForm = reactive({
  departmentId: undefined as number | undefined,
  departmentName: '',
  physicianId: undefined as number | undefined,
  physicianName: '',
  remark: '',
})

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

// 加载当前分诊确认科室下的医生列表
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

// 监听科室切换，重新拉医生下拉；同时切换医生时清空姓名
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
  // 加载该科室下的真实医生列表
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
  await Promise.all([loadBaseData(), loadTriagePending()])
})
</script>

<template>
  <div class="admin-workspace u-page-grid">
    <PageHeader
      title="管理员支撑工作台"
      description="当前范围覆盖管理员支撑能力：AI 分诊台处理，以及科室、挂号级别、结算类别等基础数据查看。"
      eyebrow="Role B / Admin"
    >
      <template #actions>
        <ElButton @click="loadTriagePending">刷新分诊台</ElButton>
        <ElButton type="primary" @click="loadBaseData">刷新基础数据</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="flow-card">
      <ElTabs v-model="activeTab">
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
                <ElTableColumn prop="price" label="价格" min-width="100" align="right">
                  <template #default="{ row }">
                    <span class="price-value">¥ {{ row.price }}</span>
                  </template>
                </ElTableColumn>
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
.admin-workspace {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.flow-card,
.inner-card {
  padding: var(--space-5);
}

.split-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.base-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.full-width {
  width: 100%;
}

.section-title,
.item-meta,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.section-title h3 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
  gap: var(--space-2);
}

.triage-list {
  display: grid;
  gap: var(--space-3);
  max-block-size: 480px;
  overflow-y: auto;
  padding-inline-end: 4px;
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

.item-meta {
  justify-content: space-between;
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

.price-value {
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

.flow-card :deep(.el-tabs__header) {
  margin-block-end: 0;
}

.flow-card :deep(.el-tabs__item) {
  font-weight: 500;
  font-size: 0.95rem;
}

.inner-card :deep(.el-table) {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.inner-card :deep(.el-table tr:hover > td) {
  background: var(--color-primary-soft) !important;
}

@media (max-width: 1200px) {
  .base-grid,
  .split-grid {
    grid-template-columns: 1fr;
  }
}
</style>
