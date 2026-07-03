<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDrawer, ElIcon, ElTag } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import ClinicalNotebookContent from './ClinicalNotebookContent.vue'
import ClinicalNotebookExportDialog from './ClinicalNotebookExportDialog.vue'
import ClinicalNotebookPrintSheet from '@/shared/components/ClinicalNotebookPrintSheet.vue'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import CtDiagnosisReportPrintSheet from '@/shared/components/CtDiagnosisReportPrintSheet.vue'
import CtFilmPrintSheet from '@/shared/components/CtFilmPrintSheet.vue'
import { useClinicalNotebookExport } from '@/shared/composables/useClinicalNotebookExport'
import { clinicalRecordApi, type ClinicalExamItem, type ClinicalNotebook } from '@/shared/api/modules/clinicalRecord'
import { getCompletedExamItemsForExportSelection } from '@/shared/utils/clinicalNotebook'

const visible = defineModel<boolean>('visible', { default: false })

const props = withDefaults(defineProps<{
  registerId: number | null
  mode: 'physician' | 'patient'
  title?: string
  subtitle?: string
}>(), {
  title: '本次病历本',
})

const loading = ref(false)
const archived = ref(false)
const message = ref('')
const notebook = ref<ClinicalNotebook | null>(null)
const drawerSubtitle = ref('')
const exportDialogVisible = ref(false)

const notebookPrintRef = ref<InstanceType<typeof ClinicalNotebookPrintSheet> | null>(null)
const labPrintRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)
const ctDiagnosisPrintRef = ref<InstanceType<typeof CtDiagnosisReportPrintSheet> | null>(null)
const ctFilmPrintRef = ref<InstanceType<typeof CtFilmPrintSheet> | null>(null)

const {
  exporting: notebookExporting,
  notebookExportContext,
  labExportContext,
  filmExportContext,
  volumeMeta,
  ctDiagnosisExportContext,
  exportNotebookBundle,
} = useClinicalNotebookExport()

const canExportNotebook = computed(() => Boolean(notebook.value && !loading.value))

const selectableExamItems = computed(() =>
  getCompletedExamItemsForExportSelection(notebook.value?.examItems ?? []),
)

const headerTags = computed(() => {
  if (archived.value) return [{ type: 'success' as const, text: '已归档' }]
  return [{ type: 'info' as const, text: props.mode === 'physician' ? '实时' : '待归档' }]
})

const emptyText = computed(() => {
  if (props.mode === 'patient' && !archived.value) {
    return '医生归档后将展示完整病历本。'
  }
  return '暂无记录，各环节保存后将自动出现在此处。'
})

async function loadNotebook() {
  if (!props.registerId) {
    notebook.value = null
    archived.value = false
    message.value = ''
    drawerSubtitle.value = props.subtitle || ''
    return
  }
  loading.value = true
  try {
    if (props.mode === 'physician') {
      const data = await clinicalRecordApi.physicianNotebook(props.registerId)
      notebook.value = data
      archived.value = Boolean(data.archived || data.clinicalArchivedAt)
      message.value = ''
    } else {
      const data = await clinicalRecordApi.patientNotebook(props.registerId)
      archived.value = Boolean(data.archived)
      message.value = data.message || ''
      notebook.value = data.archived ? data : null
      if (!props.subtitle && data.archived) {
        drawerSubtitle.value = [
          data.header?.physicianName,
          data.header?.visitDate ? String(data.header.visitDate).slice(0, 16) : '',
        ].filter(Boolean).join(' · ')
      }
    }
    if (props.subtitle) {
      drawerSubtitle.value = props.subtitle
    }
  } catch (error) {
    console.warn('加载病历本失败:', error)
    notebook.value = null
  } finally {
    loading.value = false
  }
}

watch(visible, (open) => {
  if (open) {
    void loadNotebook()
  }
})

watch(() => props.registerId, () => {
  if (visible.value) {
    void loadNotebook()
  }
})

defineExpose({ reload: loadNotebook })

function handleExportNotebookClick() {
  if (!notebook.value) return
  if (selectableExamItems.value.length > 0) {
    exportDialogVisible.value = true
    return
  }
  void runNotebookExport([])
}

function handleExportConfirm(selected: ClinicalExamItem[]) {
  void runNotebookExport(selected)
}

async function runNotebookExport(selected: ClinicalExamItem[]) {
  if (!notebook.value) return
  await exportNotebookBundle(
    notebook.value,
    selected,
    props.mode,
    {
      notebookPrintRef,
      labPrintRef,
      ctFilmPrintRef,
      ctDiagnosisPrintRef,
    },
  )
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    :title="title"
    direction="rtl"
    size="min(680px, 100vw)"
    append-to-body
    :lock-scroll="true"
    class="clinical-record-drawer"
  >
    <div class="clinical-record-drawer__content">
      <div v-if="drawerSubtitle || headerTags.length" class="clinical-record-drawer__meta">
        <p v-if="drawerSubtitle" class="clinical-record-drawer__subtitle">{{ drawerSubtitle }}</p>
        <div class="clinical-record-drawer__tags">
          <ElTag v-for="tag in headerTags" :key="tag.text" size="small" :type="tag.type">{{ tag.text }}</ElTag>
        </div>
      </div>

      <p v-if="message" class="clinical-record-drawer__notice">{{ message }}</p>

      <ClinicalNotebookContent
        :notebook="notebook"
        :loading="loading"
        :archived="archived"
        :mode="mode"
        :empty-text="emptyText"
      />

      <div class="clinical-record-drawer__footer">
        <ElButton
          type="primary"
          size="small"
          :disabled="!canExportNotebook"
          :loading="notebookExporting"
          @click="handleExportNotebookClick"
        >
          <ElIcon><Document /></ElIcon>
          导出病历本
        </ElButton>
        <ElButton size="small" :loading="loading" @click="loadNotebook">刷新</ElButton>
      </div>
    </div>

    <ClinicalNotebookExportDialog
      v-model:visible="exportDialogVisible"
      :exam-items="notebook?.examItems ?? []"
      :mode="mode"
      @confirm="handleExportConfirm"
    />

    <div class="clinical-notebook-print-host" aria-hidden="true">
      <ClinicalNotebookPrintSheet ref="notebookPrintRef" :context="notebookExportContext" />
      <LabReportPrintSheet ref="labPrintRef" :context="labExportContext" />
      <CtFilmPrintSheet
        ref="ctFilmPrintRef"
        :context="filmExportContext"
        :volume-meta="volumeMeta"
      />
      <CtDiagnosisReportPrintSheet ref="ctDiagnosisPrintRef" :context="ctDiagnosisExportContext" />
    </div>
  </ElDrawer>
</template>

<style scoped>
.clinical-record-drawer__content {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-height: 100%;
}

.clinical-record-drawer__meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.clinical-record-drawer__subtitle {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

.clinical-record-drawer__tags {
  display: flex;
  gap: var(--space-2);
}

.clinical-record-drawer__notice {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: rgba(255, 193, 7, 0.12);
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.6;
}

.clinical-record-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: auto;
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.clinical-notebook-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}
</style>
