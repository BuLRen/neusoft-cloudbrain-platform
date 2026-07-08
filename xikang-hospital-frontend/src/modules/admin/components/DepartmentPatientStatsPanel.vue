<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDrawer,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElMessage,
} from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import { useDepartmentPatientStats } from '@/modules/admin/composables/useDepartmentPatientStats'
import type { DepartmentPatientStat } from '@/modules/admin/composables/useDepartmentPatientStats'
import { useClientPagination } from '@/modules/admin/composables/useClientPagination'
import { maskName } from '@/modules/registration/composables/useCallingBoard'
import type { CallItem } from '@/shared/types/calling'

const { embedded = false } = defineProps<{ embedded?: boolean }>()

const router = useRouter()
const { loading, departments, summary, lastUpdatedAt, refresh } = useDepartmentPatientStats()

const {
  page,
  size,
  total,
  totalPages,
  pagedRecords,
  onPageChange,
} = useClientPagination(departments, 5)

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const selectedDept = ref<DepartmentPatientStat | null>(null)
const drawerCalling = ref<CallItem[]>([])
const drawerWaiting = ref<CallItem[]>([])

function callStatusLabel(status?: number) {
  if (status === 1) return '叫号中'
  if (status === 2) return '已应答'
  if (status === 3) return '过号'
  return '候诊'
}

function callStatusTone(status?: number): 'primary' | 'success' | 'warning' | 'danger' {
  if (status === 1) return 'primary'
  if (status === 2) return 'success'
  if (status === 3) return 'warning'
  return 'warning'
}

async function openDepartment(row: DepartmentPatientStat) {
  selectedDept.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  drawerCalling.value = []
  drawerWaiting.value = []
  try {
    const detail = await registrationApi.callingBoardDepartment(row.departmentId)
    drawerCalling.value = detail.calling
    drawerWaiting.value = detail.waiting
  } catch {
    ElMessage.error('加载科室详情失败')
  } finally {
    drawerLoading.value = false
  }
}

function openTriage() {
  router.push('/admin/triage')
}

function openCallingBoard() {
  if (!selectedDept.value) return
  const route = router.resolve(`/calling-board/${selectedDept.value.departmentId}`)
  window.open(route.href, '_blank')
}
</script>

<template>
  <section
    class="dept-patient-stats"
    :class="{ 'dept-patient-stats--embedded': embedded }"
    v-loading="loading"
  >
    <div v-if="!embedded" class="panel__header">
      <div>
        <h3>各科室病人统计</h3>
        <p>实时汇总各科室候诊与叫号人数，合并今日挂号量；数据每 30 秒自动刷新。</p>
      </div>
      <div class="panel__actions">
        <span v-if="lastUpdatedAt" class="last-updated">更新于 {{ lastUpdatedAt }}</span>
        <ElButton @click="refresh">刷新</ElButton>
      </div>
    </div>

    <div v-else class="admin-section-header">
      <div class="admin-section-header__text">
        <h3>各科室病人统计</h3>
        <p>实时汇总各科室候诊与叫号人数，合并今日挂号量。</p>
      </div>
      <div class="panel__actions">
        <span v-if="lastUpdatedAt" class="last-updated">更新于 {{ lastUpdatedAt }}</span>
        <ElButton @click="refresh">刷新</ElButton>
      </div>
    </div>

    <div class="kpi-grid">
      <GlassCard class="kpi-card">
        <StatusTag tone="primary">全院在诊</StatusTag>
        <strong>{{ summary.totalActive }}</strong>
        <span class="kpi-hint">已报到、仍在叫号流程中</span>
      </GlassCard>
      <GlassCard class="kpi-card">
        <StatusTag tone="warning">全院候诊</StatusTag>
        <strong>{{ summary.totalWaiting }}</strong>
        <span class="kpi-hint">排队等待叫号</span>
      </GlassCard>
      <GlassCard class="kpi-card">
        <StatusTag tone="ai">全院叫号中</StatusTag>
        <strong>{{ summary.totalCalling }}</strong>
        <span class="kpi-hint">医生已叫、待进诊室</span>
      </GlassCard>
      <GlassCard class="kpi-card kpi-card--clickable" @click="openTriage">
        <StatusTag tone="danger">待确认分诊</StatusTag>
        <strong>{{ summary.triagePending }}</strong>
        <span class="kpi-hint">点击进入 AI 分诊台</span>
      </GlassCard>
    </div>

    <GlassCard class="panel panel--table">
      <ElTable
        :data="pagedRecords"
        class="admin-data-table dept-table"
        highlight-current-row
        @row-click="(row) => openDepartment(row as DepartmentPatientStat)"
      >
        <ElTableColumn prop="departmentName" label="科室" min-width="140" sortable />
        <ElTableColumn prop="activeCount" label="在诊人数" min-width="100" align="right" sortable />
        <ElTableColumn prop="waitingCount" label="候诊" min-width="80" align="right" sortable />
        <ElTableColumn prop="callingCount" label="叫号中" min-width="90" align="right" sortable />
        <ElTableColumn prop="todayRegistrations" label="今日挂号" min-width="100" align="right" sortable />
        <ElTableColumn label="操作" min-width="100" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" @click.stop="openDepartment(row as DepartmentPatientStat)">查看详情</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="total > 0" class="admin-pagination-bar">
        <p class="table-footer">
          共 {{ total }} 个科室
          <template v-if="totalPages > 0">，第 {{ page }} / {{ totalPages }} 页</template>
        </p>
        <ElPagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="onPageChange"
        />
      </div>
      <p v-if="departments.length === 0 && !loading" class="empty-tip">暂无科室数据</p>
    </GlassCard>

    <ElDrawer
      v-model="drawerVisible"
      :title="selectedDept ? `${selectedDept.departmentName} · 病人明细` : '科室详情'"
      size="480px"
      destroy-on-close
    >
      <div v-loading="drawerLoading" class="drawer-body">
        <template v-if="selectedDept">
          <div class="drawer-summary">
            <StatusTag tone="primary">在诊 {{ selectedDept.activeCount }} 人</StatusTag>
            <StatusTag tone="warning">候诊 {{ selectedDept.waitingCount }}</StatusTag>
            <StatusTag tone="ai">叫号中 {{ selectedDept.callingCount }}</StatusTag>
            <StatusTag tone="success">今日挂号 {{ selectedDept.todayRegistrations }}</StatusTag>
          </div>

          <section v-if="drawerCalling.length > 0" class="drawer-section">
            <h4>叫号中（{{ drawerCalling.length }}）</h4>
            <div v-for="item in drawerCalling" :key="item.registerId" class="patient-row">
              <div class="patient-row__main">
                <strong>{{ maskName(item.patientName) }}</strong>
                <span v-if="item.queueNumber != null">第 {{ item.queueNumber }} 号</span>
              </div>
              <div class="patient-row__meta">
                <StatusTag :tone="callStatusTone(item.callStatus)">{{ callStatusLabel(item.callStatus) }}</StatusTag>
                <span v-if="item.doctorName">{{ item.doctorName }}</span>
                <span v-if="item.clinicRoom">{{ item.clinicRoom }}</span>
              </div>
            </div>
          </section>

          <section v-if="drawerWaiting.length > 0" class="drawer-section">
            <h4>候诊队列（{{ drawerWaiting.length }}）</h4>
            <div v-for="item in drawerWaiting" :key="item.registerId" class="patient-row">
              <div class="patient-row__main">
                <strong>{{ maskName(item.patientName) }}</strong>
                <span v-if="item.queueNumber != null">第 {{ item.queueNumber }} 号</span>
              </div>
              <div class="patient-row__meta">
                <StatusTag :tone="callStatusTone(item.callStatus)">{{ callStatusLabel(item.callStatus) }}</StatusTag>
                <span v-if="item.doctorName">{{ item.doctorName }}</span>
              </div>
            </div>
          </section>

          <div
            v-if="!drawerLoading && drawerCalling.length === 0 && drawerWaiting.length === 0"
            class="empty-tip"
          >
            <StatusTag tone="success">当前无在诊病人</StatusTag>
            <p>该科室今日尚无已报到候诊或叫号中的患者。</p>
          </div>
        </template>
      </div>

      <template #footer>
        <ElButton @click="drawerVisible = false">关闭</ElButton>
        <ElButton type="primary" :disabled="!selectedDept" @click="openCallingBoard">打开叫号大屏</ElButton>
      </template>
    </ElDrawer>
  </section>
</template>

<style scoped>
.dept-patient-stats {
  display: grid;
  gap: var(--space-4);
}

.dept-patient-stats--embedded {
  gap: var(--space-3);
}

.panel__header,
.admin-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.panel__header h3,
.admin-section-header h3 {
  margin: 0;
  font-size: 1.05rem;
}

.panel__header p,
.admin-section-header p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.85rem;
  line-height: 1.7;
}

.panel__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.last-updated {
  color: var(--color-text-soft);
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.kpi-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
}

.kpi-card--clickable {
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.kpi-card--clickable:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.kpi-card strong {
  font-size: 28px;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  color: var(--color-text-soft);
  font-size: 0.78rem;
}

.panel--table {
  padding: var(--space-5);
}

.dept-table :deep(.el-table__row) {
  cursor: pointer;
}

.admin-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-start: var(--space-4);
}

.table-footer {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.empty-tip {
  display: grid;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  padding: var(--space-4);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  text-align: center;
}

.empty-tip p {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.drawer-body {
  min-height: 120px;
}

.drawer-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.drawer-section {
  margin-block-end: var(--space-5);
}

.drawer-section h4 {
  margin: 0 0 var(--space-3);
  font-size: 0.95rem;
}

.patient-row {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin-block-end: var(--space-2);
}

.patient-row__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.patient-row__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
