<script setup lang="ts">

import { computed, onMounted, onUnmounted, ref } from 'vue'

import { useRouter } from 'vue-router'

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

import type { AdminMonitoringAlert } from '@/shared/types/admin'

import { useDepartmentPatientStats } from '@/modules/admin/composables/useDepartmentPatientStats'

import { registrationApi } from '@/shared/api/modules/registration'



const { embedded = false } = defineProps<{ embedded?: boolean }>()



const router = useRouter()

const { loading: statsLoading, summary, lastUpdatedAt, refresh: refreshStats } = useDepartmentPatientStats()



const keyword = ref('')

const selectedLevel = ref('')

const selectedStatus = ref('')

const detailDialogVisible = ref(false)

const selectedAlert = ref<AdminMonitoringAlert | null>(null)

const alerts = ref<AdminMonitoringAlert[]>([])

const alertsLoading = ref(false)



const monitoringMetrics = computed(() => [

  { title: '全院在诊', value: String(summary.value.totalActive), note: '已报到、仍在叫号流程中', tone: 'primary' as const },

  { title: '全院候诊', value: String(summary.value.totalWaiting), note: '排队等待叫号', tone: 'warning' as const },

  { title: '全院叫号中', value: String(summary.value.totalCalling), note: '医生已叫、待进诊室', tone: 'ai' as const },

  { title: '待确认分诊', value: String(summary.value.triagePending), note: '点击进入 AI 分诊台处理', tone: 'danger' as const },

])



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



async function loadAlerts() {

  alertsLoading.value = true

  try {

    alerts.value = await registrationApi.monitoringAlerts()

  } catch (err) {

    console.warn('[monitoring] 加载告警失败', err)

    alerts.value = []

  } finally {

    alertsLoading.value = false

  }

}



async function refreshAll() {

  await Promise.all([refreshStats(), loadAlerts()])

}



function openAlert(alert: AdminMonitoringAlert) {

  selectedAlert.value = alert

  detailDialogVisible.value = true

}



async function markProcessing(alert: AdminMonitoringAlert) {

  try {

    await registrationApi.dismissMonitoringAlert(alert.alertKey, 'processing')

    alert.status = 'processing'

    ElMessage.success('已转为处理中')

  } catch {

    ElMessage.error('操作失败，请稍后重试')

  }

}



async function markResolved(alert: AdminMonitoringAlert) {

  try {

    await registrationApi.dismissMonitoringAlert(alert.alertKey, 'resolved')

    alerts.value = alerts.value.filter((item) => item.alertKey !== alert.alertKey)

    ElMessage.success('已标记为解决')

  } catch {

    ElMessage.error('操作失败，请稍后重试')

  }

}



function openDashboard() {

  router.push('/dashboard')

}



function openTriage() {

  router.push('/admin/triage')

}



let alertsTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  void loadAlerts()
  alertsTimer = setInterval(() => {
    void loadAlerts()
  }, 30_000)
})

onUnmounted(() => {
  if (alertsTimer) clearInterval(alertsTimer)
})

</script>



<template>

  <div

    class="operations-monitoring-page"

    :class="{

      'u-page-grid': !embedded,

      'operations-monitoring-page--embedded': embedded,

      'admin-embedded-surface': embedded,

    }"

  >

    <PageHeader

      v-if="!embedded"

      title="运营监控"

      description="从分诊、排班、收费、药房等跨模块视角统一查看异常和预警，形成管理员治理闭环。"

      eyebrow="Role Admin / Monitoring"

    >

      <template #actions>

        <span v-if="lastUpdatedAt" class="last-updated">更新于 {{ lastUpdatedAt }}</span>

        <ElButton @click="refreshAll">刷新指标</ElButton>

        <ElButton link type="primary" @click="openDashboard">各科室详情 → 仪表盘</ElButton>

      </template>

    </PageHeader>



    <div v-if="embedded" class="admin-section-header">

      <div class="admin-section-header__text">

        <h3>运营监控</h3>

        <p>从分诊、排班、收费、药房等跨模块视角统一查看异常和预警。</p>

      </div>

      <div class="monitoring-toolbar">

        <span v-if="lastUpdatedAt" class="last-updated">更新于 {{ lastUpdatedAt }}</span>

        <ElButton @click="refreshAll">刷新指标</ElButton>

        <ElButton link type="primary" @click="openDashboard">各科室详情 → 仪表盘</ElButton>

      </div>

    </div>



    <section class="metric-grid" v-loading="statsLoading">

      <GlassCard

        v-for="item in monitoringMetrics"

        :key="item.title"

        class="metric-card"

        :class="{ 'metric-card--clickable': item.title === '待确认分诊' }"

        @click="item.title === '待确认分诊' ? openTriage() : undefined"

      >

        <StatusTag :tone="item.tone">{{ item.title }}</StatusTag>

        <strong>{{ item.value }}</strong>

        <p>{{ item.note }}</p>

      </GlassCard>

    </section>



    <GlassCard class="panel" v-loading="alertsLoading">

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



      <ElTable :data="filteredAlerts" class="admin-data-table" border row-key="alertKey">

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

        <ElTableColumn label="操作" min-width="220" align="center">

          <template #default="{ row }">

            <ElButton link type="primary" @click="openAlert(row as AdminMonitoringAlert)">查看详情</ElButton>
            <ElButton v-if="row.status === 'pending'" link type="warning" @click="markProcessing(row as AdminMonitoringAlert)">转处理中</ElButton>
            <ElButton v-if="row.status !== 'resolved'" link type="success" @click="markResolved(row as AdminMonitoringAlert)">标记解决</ElButton>

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

          <h3>异常说明</h3>

          <p>{{ selectedAlert.summary || '系统根据实时业务数据自动检测到的异常，请按职责跟进处理。' }}</p>

        </div>

      </template>

    </ElDialog>

  </div>

</template>



<style scoped>

.operations-monitoring-page--embedded .metric-grid {

  gap: var(--space-3);

}



.operations-monitoring-page--embedded .metric-card,

.operations-monitoring-page--embedded .panel {

  padding: var(--space-4);

}



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



.metric-card--clickable {

  cursor: pointer;

  transition: transform var(--duration-fast) var(--ease-standard),

    box-shadow var(--duration-fast) var(--ease-standard);

}



.metric-card--clickable:hover {

  transform: translateY(-2px);

  box-shadow: var(--shadow-md);

}



.monitoring-toolbar {

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


