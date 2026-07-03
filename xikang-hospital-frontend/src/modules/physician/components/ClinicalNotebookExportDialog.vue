<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElCheckbox, ElDialog, ElTable, ElTableColumn } from 'element-plus'
import type { ClinicalExamItem } from '@/shared/api/modules/clinicalRecord'
import {
  describeExamItemExportCapability,
  examCategoryLabel,
  getCompletedExamItemsForExportSelection,
  isCtExamItem,
} from '@/shared/utils/clinicalNotebook'

const visible = defineModel<boolean>('visible', { default: false })

const props = defineProps<{
  examItems: ClinicalExamItem[]
  mode: 'physician' | 'patient'
}>()

const emit = defineEmits<{
  confirm: [selected: ClinicalExamItem[]]
}>()

const selectableItems = computed(() => getCompletedExamItemsForExportSelection(props.examItems))
const selectedIds = ref<number[]>([])

const exportableIds = computed(() =>
  selectableItems.value
    .filter((item) => describeExamItemExportCapability(item, props.mode).exportable)
    .map((item) => item.id),
)

const allExportableSelected = computed(() =>
  exportableIds.value.length > 0 &&
  exportableIds.value.every((id) => selectedIds.value.includes(id)),
)

const indeterminate = computed(() =>
  selectedIds.value.length > 0 && !allExportableSelected.value,
)

watch(visible, (open) => {
  if (open) {
    selectedIds.value = []
  }
})

function toggleSelectAll(checked: boolean) {
  selectedIds.value = checked ? [...exportableIds.value] : []
}

function handleRowSelect(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value = [...selectedIds.value, id]
    }
    return
  }
  selectedIds.value = selectedIds.value.filter((itemId) => itemId !== id)
}

function isRowDisabled(row: ClinicalExamItem): boolean {
  return !describeExamItemExportCapability(row, props.mode).exportable
}

function rowDisabledReason(row: ClinicalExamItem): string {
  return describeExamItemExportCapability(row, props.mode).reason || '暂不可导出'
}

function rowExportHint(row: ClinicalExamItem): string {
  if (isRowDisabled(row)) return rowDisabledReason(row)
  if (isCtExamItem(row) && props.mode === 'physician') return '诊断报告 + 胶片（2 个 PDF）'
  if (isCtExamItem(row)) return '诊断报告'
  return '可导出'
}

function handleConfirm() {
  const selected = selectableItems.value.filter((item) => selectedIds.value.includes(item.id))
  emit('confirm', selected)
  visible.value = false
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="导出病历本"
    width="min(560px, 92vw)"
    align-center
    append-to-body
    destroy-on-close
    class="clinical-notebook-export-dialog"
  >
    <p class="clinical-notebook-export-dialog__lead">
      将导出<strong>门诊病历本 PDF</strong>。您还可以选择一并导出的检查/检验报告；不选则仅导出病历本。CT 影像项目将额外导出胶片 PDF。
    </p>

    <ElTable :data="selectableItems" size="small" border max-height="320">
      <ElTableColumn width="48" align="center">
        <template #header>
          <ElCheckbox
            :model-value="allExportableSelected"
            :indeterminate="indeterminate"
            :disabled="!exportableIds.length"
            @change="toggleSelectAll(Boolean($event))"
          />
        </template>
        <template #default="{ row }">
          <ElCheckbox
            :model-value="selectedIds.includes(row.id)"
            :disabled="isRowDisabled(row)"
            @change="handleRowSelect(row.id, Boolean($event))"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="techName" label="项目" min-width="120" show-overflow-tooltip />
      <ElTableColumn label="类型" width="72" align="center">
        <template #default="{ row }">{{ examCategoryLabel(row.category) }}</template>
      </ElTableColumn>
      <ElTableColumn prop="state" label="状态" width="88" align="center" />
      <ElTableColumn label="说明" min-width="120">
        <template #default="{ row }">
          <span v-if="isRowDisabled(row)" class="clinical-notebook-export-dialog__muted">
            {{ rowDisabledReason(row) }}
          </span>
          <span v-else class="clinical-notebook-export-dialog__ok">{{ rowExportHint(row) }}</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">开始导出</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.clinical-notebook-export-dialog__lead {
  margin: 0 0 var(--space-4);
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text);
}

.clinical-notebook-export-dialog__muted {
  color: var(--color-text-muted);
  font-size: 12px;
}

.clinical-notebook-export-dialog__ok {
  color: #389e0d;
  font-size: 12px;
}
</style>
