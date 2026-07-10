<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog } from 'element-plus'
import type { CommunicationCardPayload, CommunicationMessageType } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  messageType?: CommunicationMessageType
  title?: string
  cardPayload?: CommunicationCardPayload | string
  viewerRole?: 'doctor' | 'patient'
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const router = useRouter()

const parsed = computed(() => {
  const raw = props.cardPayload
  if (!raw) return {} as Record<string, string | number | undefined>
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as Record<string, string | number | undefined>
    } catch {
      return {}
    }
  }
  return raw as Record<string, string | number | undefined>
})

function close() {
  emit('update:visible', false)
}

function openRegistration() {
  const path = String(parsed.value.linkPath ?? '/patient/registration')
  const departmentId = parsed.value.departmentId
  void router.push(
    departmentId != null
      ? { path, query: { departmentId: String(departmentId) } }
      : path,
  )
  close()
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
    <template v-else-if="messageType === 'registration_card'">
      <div class="registration-card">
        <p class="registration-card__dept">{{ parsed.departmentName ?? '复诊科室' }}</p>
        <p class="registration-card__text">
          {{ parsed.reminderText ?? '建议您近期到院复诊，请点击下方按钮自行预约。' }}
        </p>
        <ElButton
          v-if="viewerRole === 'patient'"
          type="primary"
          class="registration-card__cta"
          @click="openRegistration"
        >
          前往挂号
        </ElButton>
      </div>
    </template>
    <template v-else>
      <p class="card-detail__fallback">暂无可展示的卡片详情。</p>
    </template>
    <template #footer>
      <ElButton v-if="messageType === 'registration_card' && viewerRole === 'patient'" @click="close">关闭</ElButton>
      <ElButton v-else type="primary" @click="close">关闭</ElButton>
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
}

.card-detail dd {
  margin: 0;
}

.card-detail__fallback,
.registration-card__text {
  margin: 0;
  line-height: 1.6;
}

.registration-card {
  display: grid;
  gap: var(--space-3);
}

.registration-card__dept {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
}

.registration-card__cta {
  justify-self: start;
}
</style>
