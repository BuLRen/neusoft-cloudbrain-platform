<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElInput, ElTable, ElTableColumn } from 'element-plus'
import { medtechApi, type CheckApplication } from '@/shared/api/modules/medtech'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const router = useRouter()

const loading = ref(false)
const keyword = ref('')
const applications = ref<CheckApplication[]>([])
const selected = ref<CheckApplication | null>(null)

async function loadApplications() {
  loading.value = true
  try {
    // 后端当前按 registrationId/status 过滤；这里保留 keyword 仅做前端过滤（避免扩展接口）
    const rows = await medtechApi.checkApplications()
    const kw = keyword.value.trim()
    applications.value = kw
      ? rows.filter((item) => String(item.caseNumber || '').includes(kw) || String(item.patientName || '').includes(kw))
      : rows
    if (!selected.value && applications.value.length) {
      selected.value = applications.value[0]
    }
  } finally {
    loading.value = false
  }
}

function goNext() {
  if (!selected.value?.id) return
  router.push({ path: '/medtech/check-start', query: { id: String(selected.value.id) } })
}

onMounted(() => {
  void loadApplications()
})
</script>

<template>
  <MedtechStepLayout
    :step="1"
    :total-steps="3"
    title="检查申请"
    description="第一步：查看待检查申请，选择一条记录进入「开始检查」。"
    next-path="/medtech/check-start"
  >
    <div class="toolbar">
      <ElInput v-model="keyword" placeholder="搜索病历号或姓名" @keyup.enter="loadApplications" />
      <ElButton :loading="loading" @click="loadApplications">查询</ElButton>
      <ElButton type="primary" :disabled="!selected" @click="goNext">进入开始检查</ElButton>
    </div>

    <ElEmpty v-if="!loading && !applications.length" description="暂无检查申请" />
    <ElTable
      v-else
      v-loading="loading"
      :data="applications"
      highlight-current-row
      @current-change="(row) => (selected = row)"
    >
      <ElTableColumn prop="id" label="ID" width="90" />
      <ElTableColumn prop="caseNumber" label="病历号" />
      <ElTableColumn prop="patientName" label="患者" />
      <ElTableColumn prop="techName" label="项目" />
      <ElTableColumn prop="statusText" label="状态" />
    </ElTable>
  </MedtechStepLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-block-end: var(--space-4);
}
</style>

