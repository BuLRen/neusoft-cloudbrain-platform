<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElMessage,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { monitoringAlerts, monitoringMetrics } from '@/shared/mock/admin'
import type { AdminMonitoringAlert } from '@/shared/types/admin'

const keyword = ref('')
const selectedLevel = ref('')
const selectedStatus = ref('')
const detailDialogVisible = ref(false)
const selectedAlert = ref<AdminMonitoringAlert | null>(null)
const alerts = ref(monitoringAlerts.map((item) => ({ ...item })))

const filteredAlerts = computed(() => alerts.value.filter((item) => {
  const matchesKeyword = !keyword.value || [item.module, item.title, item.owner].some((field) => field.toLowerCase().includes(keyword.value.toLowerCase()))
  const matchesLevel = !selectedLevel.value || item.level === selectedLevel.value
  const matchesStatus = !selectedStatus.value || item.status === selectedStatus.value
  return matchesKeyword && matchesLevel && matchesStatus
}))

function levelTone(level: AdminMonitoringAlert['level']) {
  if (level === 'critical') return 'danger'
  if (level === 'warning') return 'warning'
  return 'primary'
}

function statusTone(status: AdminMonitoringAlert['status']) {
  if (status === 'resolved') return 'success'
  if (status === 'processing') return 'primary'
  return 'warning'
}

function statusLabel(status: AdminMonitoringAlert['status']) {
  if (status === 'resolved') return '已解决'
  if (status === 'processing') return '处理中'
  return '待处理'
}

function openAlert(alert: AdminMonitoringAlert) {
  selectedAlert.value = alert
  detailDialogVisible.value = true
}

function markProcessing(alert: AdminMonitoringAlert) {
  alert.status = 'processing'
  ElMessage.success('已转为处理中')
}

function markResolved(alert: AdminMonitoringAlert) {
  alert.status = 'resolved'
  ElMessage.success('已标记为解决')
}
</script>

<template>
  <div class="operations-monitoring-page u-page-grid">
    <PageHeader
      title="运营监控"
      description="从分诊、排班、收费、药房等跨模块视角统一查看异常和预警，形成管理员治理闭环。"
      eyebrow="Role Admin / Monitoring"
    />

    <section class="metric-grid">
      <GlassCard v-for="item in monitoringMetrics" :key="item.title" class="metric-card">
        <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>
        <strong>{{ item.value }}</strong>
        <p>{{ item.note }}</p>
      </GlassCard>
    </section>

    <GlassCard class="panel">
      <div class="toolbar">
        <ElInput v-model="keyword" placeholder="搜索模块、告警标题或责任人" clearable class="field field--keyword" />
        <ElSelect v-model="selectedLevel" placeholder="全部等级" clearable class="field field--small">
          <ElOption label="严重" value="critical" />
          <ElOption label="预警" value="warning" />
          <ElOption label="提示" value="info" />
        </ElSelect>
        <ElSelect v-model="selectedStatus" placeholder="全部状态" clearable class="field field--small">
          <ElOption label="待处理" value="pending" />
          <ElOption label="处理中" value="processing" />
          <ElOption label="已解决" value="resolved" />
        </ElSelect>
        <StatusTag tone="warning">{{ filteredAlerts.length }} 条异常</StatusTag>
      </div>

      <ElTable :data="filteredAlerts">
        <ElTableColumn prop="module" label="模块" min-width="120" />
        <ElTableColumn prop="title" label="异常标题" min-width="220" />
        <ElTableColumn label="等级" min-width="100">
          <template #default="{ row }">
            <StatusTag :tone="levelTone(row.level)">{{ row.level === 'critical' ? '严重' : row.level === 'warning' ? '预警' : '提示' }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" min-width="100">
          <template #default="{ row }">
            <StatusTag :tone="statusTone(row.status)">{{ statusLabel(row.status) }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="owner" label="责任人" min-width="120" />
        <ElTableColumn prop="updatedAt" label="更新时间" min-width="160" />
        <ElTableColumn label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openAlert(row)">查看详情</ElButton>
            <ElButton v-if="row.status === 'pending'" link type="warning" @click="markProcessing(row)">转处理中</ElButton>
            <ElButton v-if="row.status !== 'resolved'" link type="success" @click="markResolved(row)">标记解决</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </GlassCard>

    <ElDialog v-model="detailDialogVisible" title="监控详情" width="680px">
      <template v-if="selectedAlert">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="模块">{{ selectedAlert.module }}</ElDescriptionsItem>
          <ElDescriptionsItem label="标题">{{ selectedAlert.title }}</ElDescriptionsItem>
          <ElDescriptionsItem label="等级">{{ selectedAlert.level }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{ statusLabel(selectedAlert.status) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="责任人">{{ selectedAlert.owner }}</ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">{{ selectedAlert.updatedAt }}</ElDescriptionsItem>
        </ElDescriptions>
        <div class="detail-note">
          <h3>处理建议</h3>
          <p>当前页面已提供前端监控闭环结构。后续接入后端后，可在这里补充异常详情、来源日志、关联业务单据和处理记录。</p>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.metric-card,
.panel {
  padding: var(--space-5);
}

.metric-card {
  display: grid;
  gap: var(--space-3);
}

.metric-card strong {
  font-size: 28px;
  letter-spacing: -0.04em;
}

.metric-card p,
.detail-note p {
  color: var(--color-text-muted);
  line-height: 1.7;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-end: var(--space-4);
}

.field--keyword {
  width: min(320px, 100%);
}

.field--small {
  width: 160px;
}

.detail-note {
  margin-block-start: var(--space-5);
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
