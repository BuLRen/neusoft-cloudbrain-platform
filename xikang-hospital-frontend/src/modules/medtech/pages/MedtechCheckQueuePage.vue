<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus'
import { medtechApi, type MedtechApplication, type MedtechProfile, type MedtechTechType } from '@/shared/api/modules/medtech'
import MedtechApplicationDetailDialog from '../components/MedtechApplicationDetailDialog.vue'
import MedtechArchiveDialog from '../components/MedtechArchiveDialog.vue'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

type StatusTab = 'pending' | 'inProgress' | 'finished'
type TypeFilter = 'all' | MedtechTechType

const TECH_TYPE_LABEL: Record<MedtechTechType, string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const PENDING_STATE: Record<MedtechTechType, string> = {
  check: '待检查',
  inspection: '待检验',
  disposal: '待处置',
}

const IN_PROGRESS_STATE: Record<MedtechTechType, string> = {
  check: '检查中',
  inspection: '检验中',
  disposal: '处置中',
}

const FINISHED_STATES = ['已完成', '已归档'] as const

const EXECUTE_PATH: Record<MedtechTechType, string> = {
  check: '/medtech/check-start',
  inspection: '/medtech/inspection-start',
  disposal: '/medtech/disposal-start',
}

const router = useRouter()

const loading = ref(false)
const keyword = ref('')
const errorMessage = ref('')
const statusTab = ref<StatusTab>('pending')
const typeFilter = ref<TypeFilter>('all')
const applications = ref<MedtechApplication[]>([])
const detailVisible = ref(false)
const detailEmphasizeResult = ref(false)
const detailApplication = ref<MedtechApplication | null>(null)
const archiveVisible = ref(false)
const archiveApplication = ref<MedtechApplication | null>(null)
const profile = ref<MedtechProfile | null>(null)

const scopeHint = computed(() => {
  if (!profile.value) return ''
  if (profile.value.adminAllAccess) {
    return '管理员视图：显示全部医技科室的申请。'
  }
  if (profile.value.departmentName) {
    return `当前科室：${profile.value.departmentName}。仅显示分配给本科室执行的检查/检验/处置申请。`
  }
  return '当前仅显示分配给本科室执行的申请。'
})

const emptyDescription = computed(() => {
  if (statusTab.value === 'pending') return '暂无待执行申请'
  if (statusTab.value === 'inProgress') return '暂无执行中记录'
  return '暂无已结束记录'
})

function rowKey(row: MedtechApplication) {
  return `${row.techType}-${row.id}`
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function techTypeTagType(techType: MedtechTechType) {
  if (techType === 'check') return 'primary'
  if (techType === 'inspection') return 'success'
  return 'warning'
}

function isArchived(row: MedtechApplication) {
  return row.statusText === '已归档'
}

function executeLabel(row: MedtechApplication) {
  if (statusTab.value === 'finished') return '查看结果'
  if (statusTab.value === 'pending') return '执行'
  return `继续${TECH_TYPE_LABEL[row.techType]}`
}

function typesToLoad(): MedtechTechType[] {
  return typeFilter.value === 'all' ? ['check', 'inspection', 'disposal'] : [typeFilter.value]
}

async function fetchByType(techType: MedtechTechType): Promise<MedtechApplication[]> {
  if (statusTab.value === 'finished') {
    const batches = await Promise.all(
      FINISHED_STATES.map((state) => {
        if (techType === 'check') return medtechApi.checkApplications({ checkState: state })
        if (techType === 'inspection') return medtechApi.inspectionApplications({ inspectionState: state })
        return medtechApi.disposalApplications({ disposalState: state })
      }),
    )
    return batches.flat()
  }

  const state =
    statusTab.value === 'pending' ? PENDING_STATE[techType] : IN_PROGRESS_STATE[techType]
  if (techType === 'check') {
    return medtechApi.checkApplications({ checkState: state })
  }
  if (techType === 'inspection') {
    return medtechApi.inspectionApplications({ inspectionState: state })
  }
  return medtechApi.disposalApplications({ disposalState: state })
}

async function loadApplications() {
  loading.value = true
  errorMessage.value = ''
  try {
    const batches = await Promise.all(typesToLoad().map((techType) => fetchByType(techType)))
    const merged = batches.flat().sort((a, b) => {
      const timeA = a.creationTime ?? ''
      const timeB = b.creationTime ?? ''
      return statusTab.value === 'finished' ? timeB.localeCompare(timeA) : timeA.localeCompare(timeB)
    })
    const kw = keyword.value.trim()
    applications.value = kw
      ? merged.filter(
          (item) => String(item.caseNumber || '').includes(kw) || String(item.patientName || '').includes(kw),
        )
      : merged
  } catch {
    applications.value = []
    errorMessage.value = '医技申请加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function isUnpaid(row: MedtechApplication) {
  return row.paid === false
}

function canExecute(row: MedtechApplication) {
  if (statusTab.value === 'finished') return true
  if (statusTab.value === 'pending' && isUnpaid(row)) return false
  return true
}

function goExecute(row: MedtechApplication) {
  if (statusTab.value === 'finished') {
    if (isArchived(row)) {
      openDetail(row, false)
    } else {
      openDetail(row, true)
    }
    return
  }
  router.push({ path: EXECUTE_PATH[row.techType], query: { id: String(row.id) } })
}

function openDetail(row: MedtechApplication, emphasizeResult = false) {
  detailApplication.value = row
  detailEmphasizeResult.value = emphasizeResult
  detailVisible.value = true
}

function openArchive(row: MedtechApplication) {
  archiveApplication.value = row
  archiveVisible.value = true
}

function onStatusTabChange() {
  void loadApplications()
}

function onTypeFilterChange() {
  void loadApplications()
}

function onArchived() {
  void loadApplications()
}

onMounted(() => {
  void medtechApi.profile().then((data) => {
    profile.value = data
  }).catch(() => {
    profile.value = null
  })
  void loadApplications()
})
</script>

<template>
  <MedtechStepLayout
    :step="1"
    :total-steps="2"
    :show-steps="false"
    title="医技申请"
  >
    <ElAlert
      v-if="scopeHint"
      type="info"
      :title="scopeHint"
      show-icon
      :closable="false"
      class="scope-alert"
    />

    <ElTabs v-model="statusTab" class="queue-tabs" @tab-change="onStatusTabChange">
      <ElTabPane label="待执行" name="pending" />
      <ElTabPane label="执行中" name="inProgress" />
      <ElTabPane label="已结束" name="finished" />
    </ElTabs>

    <div class="toolbar">
      <ElSelect v-model="typeFilter" class="type-filter" @change="onTypeFilterChange">
        <ElOption label="全部类型" value="all" />
        <ElOption label="检查" value="check" />
        <ElOption label="检验" value="inspection" />
        <ElOption label="处置" value="disposal" />
      </ElSelect>
      <ElInput v-model="keyword" placeholder="搜索病历号或姓名" @keyup.enter="loadApplications" />
      <ElButton :loading="loading" @click="loadApplications">查询</ElButton>
    </div>

    <ElAlert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="error-alert"
    />

    <ElEmpty v-if="!loading && !errorMessage && !applications.length" :description="emptyDescription" />
    <div v-else-if="!errorMessage" class="queue-table-wrap">
      <ElTable
        v-loading="loading"
        class="queue-table"
        :data="applications"
        :row-key="rowKey"
      >
        <ElTableColumn label="类型" width="72" align="center">
          <template #default="{ row }">
            <ElTag :type="techTypeTagType(row.techType)" size="small">
              {{ TECH_TYPE_LABEL[row.techType as MedtechTechType] }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="caseNumber" label="病历号" min-width="128" show-overflow-tooltip />
        <ElTableColumn prop="patientName" label="患者" min-width="88" show-overflow-tooltip />
        <ElTableColumn prop="techName" label="项目名称" min-width="108" show-overflow-tooltip />
        <ElTableColumn prop="position" label="部位" min-width="72" show-overflow-tooltip />
        <ElTableColumn prop="info" label="目的要求" min-width="120" show-overflow-tooltip />
        <ElTableColumn label="开立时间" min-width="136" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.creationTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" min-width="108">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-cell__exec">{{ row.statusText || '—' }}</span>
              <ElTag v-if="row.paid === true" type="success" size="small">已缴费</ElTag>
              <ElTag v-else-if="row.paid === false" type="warning" size="small">未缴费</ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="168" fixed="right" align="center">
          <template #default="{ row }">
            <div class="ops-cell">
              <ElTooltip
                v-if="!canExecute(row)"
                content="请患者先完成缴费"
                placement="top"
              >
                <ElButton
                  v-if="!(statusTab === 'finished' && isArchived(row))"
                  link
                  type="primary"
                  disabled
                >
                  {{ executeLabel(row) }}
                </ElButton>
              </ElTooltip>
              <ElButton
                v-else-if="!(statusTab === 'finished' && isArchived(row))"
                link
                type="primary"
                @click="goExecute(row)"
              >
                {{ executeLabel(row) }}
              </ElButton>
              <ElButton link type="primary" @click="openDetail(row, statusTab === 'finished' && !isArchived(row))">
                详情
              </ElButton>
              <ElButton
                v-if="statusTab !== 'finished'"
                link
                type="warning"
                @click="openArchive(row)"
              >
                归档
              </ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <MedtechApplicationDetailDialog
      v-model:visible="detailVisible"
      :application="detailApplication"
      :emphasize-result="detailEmphasizeResult"
    />
    <MedtechArchiveDialog
      v-model:visible="archiveVisible"
      :application="archiveApplication"
      @archived="onArchived"
    />
  </MedtechStepLayout>
</template>

<style scoped>
.scope-alert {
  margin-block-end: var(--space-4);
}

.queue-tabs {
  margin-block-end: var(--space-4);
}

.queue-tabs :deep(.el-tabs__content) {
  display: none;
}

.queue-tabs :deep(.el-tabs__header) {
  margin-block-end: 0;
}

.toolbar {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-block-end: var(--space-4);
}

.type-filter {
  width: 128px;
  flex-shrink: 0;
}

.error-alert {
  margin-block-end: var(--space-4);
}

.queue-table-wrap {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.queue-table {
  width: 100%;
}

.queue-table :deep(.el-table__cell) {
  padding-block: 8px;
}

.status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.status-cell__exec {
  font-size: 13px;
  color: var(--color-text-secondary, #64748b);
  line-height: 1.3;
}

.ops-cell {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;
}

.ops-cell :deep(.el-button.is-link) {
  padding-inline: 4px;
}
</style>
