<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDrawer, ElTag } from 'element-plus'
import ClinicalTimelineContent from './ClinicalTimelineContent.vue'
import { clinicalRecordApi, type ClinicalTimelineEntry } from '@/shared/api/modules/clinicalRecord'

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
const timeline = ref<ClinicalTimelineEntry[]>([])
const drawerSubtitle = ref('')

const detailMode = computed(() => (props.mode === 'patient' ? 'full' : 'compact'))

const headerTags = computed(() => {
  if (archived.value) return [{ type: 'success' as const, text: '已归档' }]
  return [{ type: 'info' as const, text: props.mode === 'physician' ? '实时' : '待归档' }]
})

async function loadTimeline() {
  if (!props.registerId) {
    timeline.value = []
    archived.value = false
    message.value = ''
    drawerSubtitle.value = props.subtitle || ''
    return
  }
  loading.value = true
  try {
    // #region agent log
    fetch('http://127.0.0.1:7351/ingest/40a86613-9d86-4846-bc86-2da24038373a',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'2aef16'},body:JSON.stringify({sessionId:'2aef16',runId:'pre-fix',hypothesisId:'B',location:'ClinicalRecordDrawer.vue:loadTimeline',message:'loadTimeline start',data:{registerId:props.registerId,mode:props.mode},timestamp:Date.now()})}).catch(()=>{});
    // #endregion
    if (props.mode === 'physician') {
      const data = await clinicalRecordApi.physicianTimeline(props.registerId)
      timeline.value = data.timeline || []
      archived.value = Boolean(data.archived || data.clinicalArchivedAt)
      message.value = ''
    } else {
      const data = await clinicalRecordApi.patientVisitDetail(props.registerId)
      timeline.value = data.timeline || []
      archived.value = Boolean(data.archived)
      message.value = data.message || ''
      if (!props.subtitle) {
        drawerSubtitle.value = [data.physicianName, data.visitDate ? String(data.visitDate).slice(0, 16) : ''].filter(Boolean).join(' · ')
      }
    }
    if (props.subtitle) {
      drawerSubtitle.value = props.subtitle
    }
  } catch (error) {
    // #region agent log
    fetch('http://127.0.0.1:7351/ingest/40a86613-9d86-4846-bc86-2da24038373a',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'2aef16'},body:JSON.stringify({sessionId:'2aef16',runId:'pre-fix',hypothesisId:'B',location:'ClinicalRecordDrawer.vue:loadTimeline',message:'loadTimeline failed',data:{registerId:props.registerId,mode:props.mode,error:error instanceof Error?error.message:String(error)},timestamp:Date.now()})}).catch(()=>{});
    // #endregion
    console.warn('加载病历本失败:', error)
    timeline.value = []
  } finally {
    loading.value = false
  }
}

watch(visible, (open) => {
  if (open) {
    void loadTimeline()
  }
})

watch(() => props.registerId, () => {
  if (visible.value) {
    void loadTimeline()
  }
})

defineExpose({ reload: loadTimeline })
</script>

<template>
  <ElDrawer
    v-model="visible"
    :title="title"
    direction="rtl"
    size="min(520px, 100vw)"
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

      <ClinicalTimelineContent
        :timeline="timeline"
        :loading="loading"
        :archived="archived"
        :detail-mode="detailMode"
        :empty-text="mode === 'patient' && !archived ? '医生归档后将展示完整病历时间线。' : '暂无记录，各环节保存后将自动出现在此处。'"
      />

      <div class="clinical-record-drawer__footer">
        <ElButton size="small" :loading="loading" @click="loadTimeline">刷新</ElButton>
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
