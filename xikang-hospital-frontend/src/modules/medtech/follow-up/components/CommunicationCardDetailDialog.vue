<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import type { CommunicationCardPayload, CommunicationMessageType } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  messageType?: CommunicationMessageType
  title?: string
  cardPayload?: CommunicationCardPayload | string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const parsed = computed(() => {
  const raw = props.cardPayload
  if (!raw) return {} as Record<string, string | undefined>
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as Record<string, string | undefined>
    } catch {
      return {}
    }
  }
  return raw as Record<string, string | undefined>
})

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog :model-value="visible" :title="title || '消息详情'" width="520px" @close="close">
    <template v-if="messageType === 'drug_card'">
      <dl class="card-detail">
        <dt>药品名称</dt>
        <dd>{{ parsed.drugName ?? '—' }}</dd>
        <dt v-if="parsed.drugFormat">规格</dt>
        <dd v-if="parsed.drugFormat">{{ parsed.drugFormat }}</dd>
        <dt v-if="parsed.manufacturer">生产厂家</dt>
        <dd v-if="parsed.manufacturer">{{ parsed.manufacturer }}</dd>
        <dt v-if="parsed.drugUsage">用法用量</dt>
        <dd v-if="parsed.drugUsage">{{ parsed.drugUsage }}</dd>
        <dt v-if="parsed.cautionNotes">注意事项</dt>
        <dd v-if="parsed.cautionNotes">{{ parsed.cautionNotes }}</dd>
      </dl>
    </template>
    <template v-else-if="messageType === 'diagnosis_card'">
      <dl class="card-detail">
        <dt>病况名称</dt>
        <dd>{{ parsed.diseaseName ?? parsed.diagnosisText ?? '—' }}</dd>
        <dt v-if="parsed.diseaseIcd">ICD</dt>
        <dd v-if="parsed.diseaseIcd">{{ parsed.diseaseIcd }}</dd>
        <dt v-if="parsed.treatmentDirection">诊疗建议</dt>
        <dd v-if="parsed.treatmentDirection">{{ parsed.treatmentDirection }}</dd>
      </dl>
    </template>
    <template v-else>
      <p class="card-detail__fallback">暂无可展示的卡片详情。</p>
    </template>
    <template #footer>
      <ElButton type="primary" @click="close">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.card-detail {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: var(--space-2) var(--space-3);
  margin: 0;
}

.card-detail dt {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.card-detail dd {
  margin: 0;
  line-height: 1.6;
}

.card-detail__fallback {
  margin: 0;
  color: var(--color-text-muted);
}
</style>
