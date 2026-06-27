<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElCheckbox, ElDialog, ElInput } from 'element-plus'
import { stripMarkdown } from '@/shared/utils/plainText'
import type { FollowUpCaseSummary } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  summary: FollowUpCaseSummary | null
  saving?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  approve: [payload: { doctorContent: string; sharedToPatient: boolean }]
}>()

const doctorContent = ref('')
const sharedToPatient = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

watch(
  () => props.summary,
  (summary) => {
    if (!summary) return
    const draft =
      summary.doctorContent?.trim() ||
      summary.aiDraftContent?.trim() ||
      ''
    doctorContent.value = stripMarkdown(draft)
    sharedToPatient.value = Boolean(summary.sharedToPatient)
  },
  { immediate: true },
)

const plainAiDraft = computed(() => stripMarkdown(props.summary?.aiDraftContent))
const plainMedicalAdvice = computed(() => stripMarkdown(props.summary?.aiMedicalAdvice))
const plainRiskAlerts = computed(() => {
  const raw = props.summary?.aiRiskAlerts
  if (!raw) return ''
  return stripMarkdown(String(raw).replace(/^\[|\]$/g, '').replace(/","/g, '；').replace(/"/g, ''))
})

function handleApprove() {
  emit('approve', {
    doctorContent: doctorContent.value.trim(),
    sharedToPatient: sharedToPatient.value,
  })
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="审核 AI 病例总结"
    width="880px"
    :lock-scroll="false"
    modal-class="outcome-dialog-overlay"
  >
    <div v-if="summary" class="summary-dialog">
      <div class="summary-dialog__panel summary-dialog__panel--readonly">
        <h4>AI 草稿（只读）</h4>
        <pre class="summary-dialog__body">{{ plainAiDraft || '—' }}</pre>
        <p v-if="plainMedicalAdvice" class="summary-dialog__advice">
          <strong>医学建议：</strong>{{ plainMedicalAdvice }}
        </p>
        <p v-if="plainRiskAlerts" class="summary-dialog__advice">
          <strong>风险提示：</strong>{{ plainRiskAlerts }}
        </p>
      </div>
      <div class="summary-dialog__panel">
        <h4>医生定稿（可编辑）</h4>
        <ElInput v-model="doctorContent" type="textarea" :rows="14" placeholder="在此编辑后确认发布" />
        <ElCheckbox v-model="sharedToPatient" class="summary-dialog__share">
          向患者展示此总结
        </ElCheckbox>
      </div>
    </div>
    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="!doctorContent.trim()" @click="handleApprove">
        确认定稿
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.summary-dialog {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.summary-dialog__panel h4 {
  margin: 0 0 var(--space-2);
  font-size: 14px;
}

.summary-dialog__panel--readonly {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-soft) 55%, #fff);
  border: 1px solid var(--color-border);
}

.summary-dialog__body {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.summary-dialog__advice {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.summary-dialog__share {
  margin-block-start: var(--space-3);
}

.summary-dialog :deep(.el-textarea__inner) {
  resize: vertical;
  max-height: 360px;
}

@media (max-width: 900px) {
  .summary-dialog {
    grid-template-columns: 1fr;
  }
}
</style>
