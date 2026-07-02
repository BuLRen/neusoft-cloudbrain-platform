<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import {
  fetchCtImagingAuditLogs,
  type CtImagingAuditLog,
} from '@/shared/api/modules/ctViewer'

const loading = ref(false)
const logs = ref<CtImagingAuditLog[]>([])
const total = ref(0)

const filters = reactive({
  volumeId: '',
  userId: '',
  action: '',
  success: '' as '' | 'true' | 'false',
  page: 1,
  size: 20,
})

const actionOptions = [
  { label: '全部动作', value: '' },
  { label: '上传 DICOM', value: 'UPLOAD_DICOM' },
  { label: '上传 NRRD', value: 'UPLOAD_NRRD' },
  { label: '查看 NRRD', value: 'VIEW_NRRD' },
  { label: '查看元数据', value: 'VIEW_META' },
  { label: '滤波', value: 'FILTER' },
  { label: 'AI 分析', value: 'ANALYZE' },
  { label: '导出', value: 'EXPORT' },
  { label: '绑定检查单', value: 'BIND' },
  { label: '解绑检查单', value: 'UNBIND' },
  { label: '拒绝访问', value: 'ACCESS_DENIED' },
]

function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}

function successTone(success?: boolean) {
  if (success === false) return 'danger'
  if (success === true) return 'success'
  return 'neutral'
}

function successText(success?: boolean) {
  if (success === false) return '拒绝'
  if (success === true) return '成功'
  return '未知'
}

async function loadLogs() {
  loading.value = true
  try {
    const userId = filters.userId.trim() ? Number(filters.userId.trim()) : undefined
    const result = await fetchCtImagingAuditLogs({
      page: filters.page,
      size: filters.size,
      volumeId: filters.volumeId.trim() || undefined,
      userId: Number.isFinite(userId) ? userId : undefined,
      action: filters.action || undefined,
      success: filters.success === '' ? undefined : filters.success === 'true',
    })
    logs.value = result.items
    total.value = result.total
  } catch {
    logs.value = []
    total.value = 0
    ElMessage.error('审计日志加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  void loadLogs()
}

function handleReset() {
  filters.volumeId = ''
  filters.userId = ''
  filters.action = ''
  filters.success = ''
  filters.page = 1
  void loadLogs()
}

function handlePageChange(page: number) {
  filters.page = page
  void loadLogs()
}

function handleSizeChange(size: number) {
  filters.size = size
  filters.page = 1
  void loadLogs()
}

onMounted(() => {
  void loadLogs()
})
</script>

<template>
  <div class="ct-audit-page u-page-grid">
    <PageHeader
      title="CT 影像审计日志"
      description="记录 CT 影像上传、查看、滤波、分析与越权拒绝等操作，便于演示环境安全追溯。"
      eyebrow="管理员"
    />

    <GlassCard class="ct-audit-page__card admin-shell-card admin-panel-surface">
      <div class="ct-audit-page__filters">
        <ElInput
          v-model="filters.volumeId"
          clearable
          placeholder="volumeId"
          class="ct-audit-page__filter-item"
        />
        <ElInput
          v-model="filters.userId"
          clearable
          placeholder="用户 ID"
          class="ct-audit-page__filter-item"
        />
        <ElSelect
          v-model="filters.action"
          clearable
          placeholder="动作类型"
          class="ct-audit-page__filter-item"
        >
          <ElOption
            v-for="option in actionOptions"
            :key="option.value || 'all'"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.success"
          clearable
          placeholder="结果"
          class="ct-audit-page__filter-item ct-audit-page__filter-item--compact"
        >
          <ElOption label="全部结果" value="" />
          <ElOption label="成功" value="true" />
          <ElOption label="拒绝" value="false" />
        </ElSelect>
        <div class="ct-audit-page__actions">
          <ElButton type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            查询
          </ElButton>
          <ElButton :icon="Refresh" :disabled="loading" @click="handleReset">
            重置
          </ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="logs" stripe class="ct-audit-page__table">
        <ElTableColumn prop="createdAt" label="时间" min-width="168">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="action" label="动作" min-width="120" />
        <ElTableColumn label="结果" min-width="88">
          <template #default="{ row }">
            <StatusTag :tone="successTone(row.success)" :label="successText(row.success)" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="userId" label="用户" min-width="80" />
        <ElTableColumn prop="departmentId" label="科室" min-width="80" />
        <ElTableColumn prop="volumeId" label="volumeId" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="checkRequestId" label="检查单" min-width="88" />
        <ElTableColumn prop="denialReason" label="拒绝原因" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="clientIp" label="IP" min-width="120" />
        <template #empty>
          <ElEmpty description="暂无审计记录" />
        </template>
      </ElTable>

      <div class="ct-audit-page__pagination">
        <ElPagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.ct-audit-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.ct-audit-page__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5);
}

.ct-audit-page__filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: var(--space-3);
  align-items: center;
}

.ct-audit-page__filter-item--compact {
  min-width: 120px;
}

.ct-audit-page__actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
}

.ct-audit-page__pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .ct-audit-page__filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ct-audit-page__actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }
}
</style>
