<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDrawer, ElTag } from 'element-plus'
import ClinicalNotebookContent from './ClinicalNotebookContent.vue'
import { clinicalRecordApi, type ClinicalNotebook } from '@/shared/api/modules/clinicalRecord'

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
        <ElButton size="small" :loading="loading" @click="loadNotebook">刷新</ElButton>
      </div>
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
  margin-block-start: auto;
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}
</style>
