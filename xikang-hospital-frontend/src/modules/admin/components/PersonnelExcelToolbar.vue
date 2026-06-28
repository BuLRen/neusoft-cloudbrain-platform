<script setup lang="ts">
import { ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload,
  type UploadRequestOptions,
} from 'element-plus'
import { adminMedtechApi } from '@/shared/api/modules/adminMedtech'
import { adminPhysicianApi } from '@/shared/api/modules/adminPhysician'
import type { PersonnelImportResult, PersonnelListFilters } from '@/shared/types/adminPersonnel'

const props = defineProps<{
  kind: 'physician' | 'medtech'
  filters: PersonnelListFilters
}>()

const emit = defineEmits<{
  imported: []
}>()

const templateLoading = ref(false)
const exportLoading = ref(false)
const importLoading = ref(false)
const resultVisible = ref(false)
const importResult = ref<PersonnelImportResult | null>(null)

const api = props.kind === 'physician' ? adminPhysicianApi : adminMedtechApi

function formatErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

async function downloadTemplate() {
  templateLoading.value = true
  try {
    await api.downloadTemplate()
  } catch (error) {
    ElMessage.error(formatErrorMessage(error, '模板下载失败'))
  } finally {
    templateLoading.value = false
  }
}

async function exportExcel() {
  exportLoading.value = true
  try {
    await api.exportExcel(props.filters)
    ElMessage.success('导出已开始')
  } catch (error) {
    ElMessage.error(formatErrorMessage(error, '导出失败'))
  } finally {
    exportLoading.value = false
  }
}

async function handleImport(options: UploadRequestOptions) {
  const file = options.file as File
  importLoading.value = true
  try {
    importResult.value = await api.importExcel(file)
    resultVisible.value = true
    if (importResult.value.successCount > 0) {
      emit('imported')
    }
    ElMessage.success(
      `导入完成：成功 ${importResult.value.successCount}，跳过 ${importResult.value.skippedCount}，失败 ${importResult.value.failedCount}`,
    )
  } catch (error) {
    ElMessage.error(formatErrorMessage(error, '导入失败'))
  } finally {
    importLoading.value = false
    options.onSuccess?.({})
  }
}

function statusTagType(status: string) {
  if (status === 'success') return 'success'
  if (status === 'skipped') return 'warning'
  return 'danger'
}

function statusLabel(status: string) {
  if (status === 'success') return '成功'
  if (status === 'skipped') return '跳过'
  return '失败'
}
</script>

<template>
  <div class="personnel-excel-toolbar">
    <ElButton :loading="templateLoading" @click="downloadTemplate">下载模板</ElButton>
    <ElUpload
      :show-file-list="false"
      accept=".xlsx"
      :http-request="handleImport"
      :disabled="importLoading"
    >
      <ElButton :loading="importLoading">导入 Excel</ElButton>
    </ElUpload>
    <ElButton :loading="exportLoading" @click="exportExcel">导出 Excel</ElButton>
  </div>

  <ElDialog v-model="resultVisible" title="导入结果" width="720px">
    <template v-if="importResult">
      <p class="import-summary">
        共 {{ importResult.totalRows }} 行：
        成功 {{ importResult.successCount }}，
        跳过 {{ importResult.skippedCount }}，
        失败 {{ importResult.failedCount }}
      </p>
      <ElTable :data="importResult.rows" max-height="360" border>
        <ElTableColumn prop="rowNumber" label="Excel 行号" width="100" align="center" />
        <ElTableColumn label="结果" width="90" align="center">
          <template #default="{ row }">
            <ElTag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="username" label="登录账号" min-width="120">
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </ElTableColumn>
      </ElTable>
    </template>
  </ElDialog>
</template>

<style scoped>
.personnel-excel-toolbar {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.import-summary {
  margin: 0 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: 0.9rem;
}
</style>
