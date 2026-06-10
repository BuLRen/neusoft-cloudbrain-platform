<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElInput, ElTabPane, ElTabs, ElTable, ElTableColumn } from 'element-plus'
import { medtechApi, type CheckApplication } from '@/shared/api/modules/medtech'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

type CheckQueueTab = '待检查' | '检查中'

const router = useRouter()

const loading = ref(false)
const keyword = ref('')
const errorMessage = ref('')
const activeTab = ref<CheckQueueTab>('待检查')
const applications = ref<CheckApplication[]>([])
const selected = ref<CheckApplication | null>(null)

const emptyDescription = computed(() =>
  activeTab.value === '待检查' ? '暂无待检查申请' : '暂无检查中记录',
)
const nextButtonLabel = computed(() =>
  activeTab.value === '待检查' ? '进入开始检查' : '继续检查',
)

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

async function loadApplications() {
  loading.value = true
  errorMessage.value = ''
  try {
    const rows = await medtechApi.checkApplications({ checkState: activeTab.value })
    const kw = keyword.value.trim()
    applications.value = kw
      ? rows.filter((item) => String(item.caseNumber || '').includes(kw) || String(item.patientName || '').includes(kw))
      : rows
    const stillSelected = applications.value.find((item) => item.id === selected.value?.id)
    selected.value = stillSelected ?? applications.value[0] ?? null
  } catch {
    applications.value = []
    selected.value = null
    errorMessage.value = '检查申请加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goNext() {
  if (!selected.value?.id) return
  router.push({ path: '/medtech/check-start', query: { id: String(selected.value.id) } })
}

function onTabChange() {
  selected.value = null
  void loadApplications()
}

onMounted(() => {
  void loadApplications()
})
</script>

<template>
  <MedtechStepLayout
    :step="1"
    :total-steps="2"
    title="检查申请"
    description="第一步：查看待检查或检查中的申请，选择一条记录进入「开始检查」。"
    next-path="/medtech/check-start"
  >
    <ElTabs v-model="activeTab" class="queue-tabs" @tab-change="onTabChange">
      <ElTabPane label="待检查" name="待检查" />
      <ElTabPane label="检查中" name="检查中" />
    </ElTabs>

    <div class="toolbar">
      <ElInput v-model="keyword" placeholder="搜索病历号或姓名" @keyup.enter="loadApplications" />
      <ElButton :loading="loading" @click="loadApplications">查询</ElButton>
      <ElButton type="primary" :disabled="!selected" @click="goNext">{{ nextButtonLabel }}</ElButton>
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
    <ElTable
      v-else-if="!errorMessage"
      v-loading="loading"
      :data="applications"
      highlight-current-row
      @current-change="(row) => (selected = row)"
    >
      <ElTableColumn prop="caseNumber" label="病历号" width="140" />
      <ElTableColumn prop="patientName" label="患者" width="100" />
      <ElTableColumn prop="techName" label="检查项目" min-width="120" />
      <ElTableColumn prop="position" label="检查部位" width="100" />
      <ElTableColumn prop="info" label="目的要求" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="开立时间" width="150">
        <template #default="{ row }">{{ formatTime(row.creationTime) }}</template>
      </ElTableColumn>
      <ElTableColumn prop="statusText" label="状态" width="90" />
    </ElTable>
  </MedtechStepLayout>
</template>

<style scoped>
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

.error-alert {
  margin-block-end: var(--space-4);
}
</style>
