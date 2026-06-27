<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import CommunicationThread from '@/modules/medtech/follow-up/components/CommunicationThread.vue'
import { clinicalRecordApi, type ClinicalVisitSummary } from '@/shared/api/modules/clinicalRecord'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { useAuthStore } from '@/app/stores/auth'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'
import { stripMarkdown } from '@/shared/utils/plainText'
import type {
  FollowUpCaseSummary,
  FollowUpCommunicationMessage,
  FollowUpCommunicationSession,
} from '@/shared/types/medtechFollowUp'

const authStore = useAuthStore()

const loading = ref(false)
const sending = ref(false)
const visits = ref<ClinicalVisitSummary[]>([])
const registerId = ref<number | undefined>()
const session = ref<FollowUpCommunicationSession | null>(null)
const messages = ref<FollowUpCommunicationMessage[]>([])
const sharedSummary = ref<FollowUpCaseSummary | null>(null)
const draft = ref('')

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

const visitOptions = computed(() =>
  visits.value.map((item) => ({
    value: item.registerId,
    label: `${item.departmentName ?? '科室'} · ${item.visitDate?.slice(0, 10) ?? item.registerId}`,
  })),
)

const plainSharedSummary = computed(() => {
  const s = sharedSummary.value
  if (!s) return ''
  return stripMarkdown(s.content ?? s.doctorContent ?? s.aiDraftContent ?? '')
})

async function loadVisits() {
  if (!patientId.value) return
  loading.value = true
  try {
    visits.value = await clinicalRecordApi.patientVisits(patientId.value)
    if (!registerId.value && visits.value.length) {
      registerId.value = visits.value[0]!.registerId
    }
  } catch {
    ElMessage.error('加载就诊记录失败')
  } finally {
    loading.value = false
  }
}

async function loadCommunication() {
  if (!registerId.value) return
  loading.value = true
  try {
    const sessionRes = await medtechFollowUpApi.getPatientCommunicationSession(registerId.value)
    session.value = sessionRes
    const [summaryRes, messagesRes] = await Promise.all([
      medtechFollowUpApi.getSharedCaseSummary(registerId.value).catch(() => null),
      medtechFollowUpApi.listCommunicationMessages(sessionRes.id, { limit: 200 }),
    ])
    sharedSummary.value = summaryRes?.exists === false ? null : summaryRes
    messages.value = messagesRes.items ?? []
  } catch {
    session.value = null
    messages.value = []
    sharedSummary.value = null
  } finally {
    loading.value = false
  }
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!text || !session.value) return
  sending.value = true
  try {
    await medtechFollowUpApi.sendPatientMessage(session.value.id, text, true)
    draft.value = ''
    const page = await medtechFollowUpApi.listCommunicationMessages(session.value.id, { limit: 200 })
    messages.value = page.items ?? []
  } catch {
    // 统一错误提示
  } finally {
    sending.value = false
  }
}

watch(registerId, () => {
  void loadCommunication()
})

onMounted(async () => {
  await loadVisits()
  if (registerId.value) {
    await loadCommunication()
  }
})
</script>

<template>
  <GlassCard class="patient-comm" v-loading="loading">
    <div class="patient-comm__head">
      <div>
        <h3>医患沟通</h3>
        <p>查看医生发布的病例总结，并就随访与用药问题与医生或 AI 助手交流。</p>
      </div>
      <ElSelect
        v-if="visitOptions.length > 1"
        v-model="registerId"
        placeholder="选择就诊"
        class="patient-comm__visit-select"
      >
        <ElOption
          v-for="item in visitOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
    </div>

    <section v-if="sharedSummary" class="patient-comm__summary">
      <h4>医生发布的病例总结</h4>
      <pre>{{ plainSharedSummary }}</pre>
      <p v-if="sharedSummary.approvedAt" class="patient-comm__summary-meta">
        发布时间：{{ formatBeijingDateTime(sharedSummary.approvedAt) }}
      </p>
    </section>
    <p v-else class="patient-comm__placeholder">医生审核发布病例总结后，您可在此查看。</p>

    <template v-if="session">
      <CommunicationThread :messages="messages" />
      <div class="patient-comm__composer">
        <ElInput
          v-model="draft"
          type="textarea"
          :rows="3"
          placeholder="描述症状、用药疑问等随访相关问题..."
          @keydown.ctrl.enter="sendMessage"
        />
        <ElButton type="primary" :loading="sending" :disabled="!draft.trim()" @click="sendMessage">
          发送
        </ElButton>
      </div>
      <p class="patient-comm__hint">AI 助手仅回答随访、用药与康复相关问题；医生在线时将优先由医生回复。</p>
    </template>
    <ElEmpty v-else-if="!loading && !registerId" description="暂无可用就诊记录" />
  </GlassCard>
</template>

<style scoped>
.patient-comm {
  padding: var(--space-5);
}

.patient-comm__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-block-end: var(--space-4);
}

.patient-comm__head h3 {
  margin: 0 0 var(--space-1);
}

.patient-comm__head p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.patient-comm__visit-select {
  min-width: 220px;
}

.patient-comm__summary {
  margin-block-end: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, #22c55e 6%, #fff);
  border: 1px solid color-mix(in srgb, #22c55e 30%, var(--color-border));
}

.patient-comm__summary h4 {
  margin: 0 0 var(--space-2);
  font-size: 14px;
}

.patient-comm__summary pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.patient-comm__summary-meta {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.patient-comm__placeholder {
  margin: 0 0 var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  color: var(--color-text-muted);
  font-size: 13px;
}

.patient-comm__composer {
  display: grid;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.patient-comm__hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}
</style>
