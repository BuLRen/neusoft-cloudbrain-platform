<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElCard, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import {
  criticalValueApi,
  type CriticalItemHit,
  type CriticalValueAlert,
  type CriticalValueBoardStats,
} from '@/shared/api/modules/criticalValue'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'

const loading = ref(false)
const alerts = ref<CriticalValueAlert[]>([])
const stats = ref<CriticalValueBoardStats>({})
let es: EventSource | null = null

const kpiCards = computed(() => [
  { label: '待签收', value: stats.value.pendingCount ?? 0, tone: 'danger' },
  { label: '已升级', value: stats.value.escalatedCount ?? 0, tone: 'warning' },
  {
    label: '超时率',
    value: stats.value.overdueRate != null ? `${(stats.value.overdueRate * 100).toFixed(1)}%` : '-',
    tone: 'info',
  },
  {
    label: '平均签收(分)',
    value: stats.value.avgAckMinutes != null ? stats.value.avgAckMinutes.toFixed(1) : '-',
    tone: 'success',
  },
  {
    label: '平均处置(分)',
    value: stats.value.avgHandleMinutes != null ? stats.value.avgHandleMinutes.toFixed(1) : '-',
    tone: 'success',
  },
])

function parseItems(raw: CriticalValueAlert['criticalItems']): CriticalItemHit[] {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return []
    }
  }
  return []
}

function summarizeItems(alert: CriticalValueAlert) {
  return parseItems(alert.criticalItems)
    .map((item) => item.itemName || item.reason || '-')
    .join('、')
}

function statusTag(status: string) {
  if (status === 'PENDING') return 'danger'
  if (status === 'ESCALATED') return 'warning'
  if (status === 'ACKNOWLEDGED') return 'primary'
  if (status === 'HANDLED') return 'success'
  return 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待签收',
    ESCALATED: '已升级',
    ACKNOWLEDGED: '已签收',
    HANDLED: '已处置',
    CLOSED: '已关闭',
  }
  return map[status] || status
}

function isOverdue(alert: CriticalValueAlert) {
  if (!alert.ackDeadline) return false
  if (alert.status !== 'PENDING' && alert.status !== 'ESCALATED') return false
  return new Date(alert.ackDeadline).getTime() < Date.now()
}

async function loadBoard() {
  loading.value = true
  try {
    const data = await criticalValueApi.board()
    alerts.value = data.alerts || []
    stats.value = data.stats || {}
  } finally {
    loading.value = false
  }
}

function upsertAlert(incoming: CriticalValueAlert & { alertId?: number }) {
  const id = incoming.id ?? incoming.alertId
  if (!id) return
  const normalized: CriticalValueAlert = { ...incoming, id }
  const idx = alerts.value.findIndex((item) => item.id === id)
  if (idx >= 0) {
    alerts.value[idx] = normalized
  } else {
    alerts.value.unshift(normalized)
  }
}

function connectStream() {
  es?.close()
  es = new EventSource('/api/medtech/critical-value/stream/board')
  es.addEventListener('CRITICAL_NEW', (e) => {
    try {
      upsertAlert(JSON.parse((e as MessageEvent).data))
      void loadBoard()
    } catch {
      void loadBoard()
    }
  })
  es.addEventListener('CRITICAL_ESCALATED', (e) => {
    try {
      upsertAlert(JSON.parse((e as MessageEvent).data))
      void loadBoard()
    } catch {
      void loadBoard()
    }
  })
  es.addEventListener('CRITICAL_CLOSED', () => {
    void loadBoard()
  })
}

onMounted(() => {
  void loadBoard()
  connectStream()
})

onUnmounted(() => {
  es?.close()
  es = null
})
</script>

<template>
  <div class="cv-board u-page-grid">
    <header class="cv-board__header">
      <div>
        <h1 class="cv-board__title">危急值监控看板</h1>
        <p class="cv-board__desc">实时追踪危急值上报、签收与处置时效</p>
      </div>
      <ElButton :loading="loading" @click="loadBoard">刷新</ElButton>
    </header>

    <section class="cv-board__kpis">
      <ElCard v-for="card in kpiCards" :key="card.label" shadow="never" class="cv-board__kpi">
        <p class="cv-board__kpi-label">{{ card.label }}</p>
        <p class="cv-board__kpi-value" :class="`is-${card.tone}`">{{ card.value }}</p>
      </ElCard>
    </section>

    <ElCard v-loading="loading" shadow="never">
      <ElTable :data="alerts" stripe border empty-text="暂无危急值工单">
        <ElTableColumn prop="patientName" label="患者" min-width="100" />
        <ElTableColumn prop="caseNumber" label="病历号" min-width="110" />
        <ElTableColumn prop="techName" label="项目" min-width="120" />
        <ElTableColumn label="危急项" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ summarizeItems(row) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="doctorName" label="开单医生" min-width="100" />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="statusTag(row.status)" :effect="isOverdue(row) ? 'dark' : 'light'" size="small">
              {{ statusLabel(row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="上报时间" min-width="150">
          <template #default="{ row }">{{ formatBeijingDateTime(row.reportedTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="签收截止" min-width="150">
          <template #default="{ row }">{{ formatBeijingDateTime(row.ackDeadline) }}</template>
        </ElTableColumn>
        <ElTableColumn label="处置时间" min-width="150">
          <template #default="{ row }">{{ formatBeijingDateTime(row.handledTime) }}</template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-if="!loading && !alerts.length" description="暂无数据" />
    </ElCard>
  </div>
</template>

<style scoped>
.cv-board__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cv-board__title {
  margin: 0;
  font-size: 22px;
}

.cv-board__desc {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}

.cv-board__kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.cv-board__kpi-label {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.cv-board__kpi-value {
  margin: 8px 0 0;
  font-size: 24px;
  font-weight: 700;
}

.cv-board__kpi-value.is-danger {
  color: var(--el-color-danger);
}

.cv-board__kpi-value.is-warning {
  color: var(--el-color-warning);
}

.cv-board__kpi-value.is-success {
  color: var(--el-color-success);
}

.cv-board__kpi-value.is-info {
  color: var(--el-color-info);
}
</style>
