<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElIcon, ElInput, ElMessage, ElScrollbar } from 'element-plus'
import { MagicStick, Promotion, User } from '@element-plus/icons-vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import AiConsultSummaryCard from '../components/AiConsultSummaryCard.vue'
import { copilotApi } from '@/shared/api/modules/copilot'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { useAuthStore } from '@/app/stores/auth'
import { PHYSICIAN_ASSISTANT, PHYSICIAN_QUEUE, visitStateLabel } from '../constants/visitState'
import { usePhysicianPatientSelectStore } from '@/app/stores/physicianPatientSelect'
import type { CopilotMessage } from '@/shared/types/copilot'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const encounterStore = useEncounterStore()
const patientSelectStore = usePhysicianPatientSelectStore()

const messages = ref<CopilotMessage[]>([])
const draft = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const chatScrollRef = ref<InstanceType<typeof ElScrollbar> | null>(null)
const patient = ref<PhysicianPatient | null>(null)
let abortController: AbortController | null = null

const registerId = computed(() => {
  const raw = route.query.registerId ?? encounterStore.registerId
  const id = typeof raw === 'string' ? Number(raw) : typeof raw === 'number' ? raw : NaN
  return Number.isFinite(id) && id > 0 ? id : null
})

const quickPrompts = [
  '请总结这位患者的主诉与病史要点',
  '根据现有检查检验结果，有哪些异常需要关注？',
  '请给出鉴别诊断思路',
  '是否需要补充哪些检查？',
]

async function loadPatient(id: number) {
  try {
    patient.value = await physicianApi.patient(id)
    if (!encounterStore.registerId) {
      encounterStore.applyPatient(patient.value)
    }
  } catch {
    patient.value = null
  }
}

async function loadHistory(id: number) {
  historyLoading.value = true
  try {
    messages.value = await copilotApi.history(id)
    await scrollToBottom()
  } finally {
    historyLoading.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  const wrap = chatScrollRef.value?.wrapRef
  if (wrap) wrap.scrollTop = wrap.scrollHeight
}

async function sendMessage(text?: string) {
  const content = (text ?? draft.value).trim()
  if (!content || !registerId.value || loading.value) return

  draft.value = ''
  loading.value = true
  abortController = new AbortController()

  messages.value.push({ role: 'user', content })
  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  await scrollToBottom()

  try {
    await copilotApi.chat(
      registerId.value,
      content,
      (chunk) => {
        const assistant = messages.value[assistantIndex]
        if (assistant) assistant.content += chunk
        void scrollToBottom()
      },
      abortController.signal,
    )
  } catch (error) {
    const msg = error instanceof Error ? error.message : '发送失败'
    const assistant = messages.value[assistantIndex]
    if (assistant && !assistant.content) assistant.content = msg
    ElMessage.error(msg)
  } finally {
    loading.value = false
    abortController = null
    if (registerId.value) await loadHistory(registerId.value)
  }
}

async function clearHistory() {
  if (!registerId.value) return
  await copilotApi.clearHistory(registerId.value)
  messages.value = []
  ElMessage.success('已清空对话')
}

function goSelectPatient() {
  patientSelectStore.open(PHYSICIAN_ASSISTANT)
}

function goQueue() {
  void router.push(PHYSICIAN_QUEUE)
}

watch(registerId, (id) => {
  if (!id) {
    patient.value = null
    messages.value = []
    return
  }
  void loadPatient(id)
  void loadHistory(id)
}, { immediate: true })

onMounted(() => {
  if (!registerId.value && encounterStore.registerId) {
    void router.replace({
      path: route.path,
      query: { ...route.query, registerId: String(encounterStore.registerId) },
    })
  }
})
</script>

<template>
  <div class="copilot-page u-page-grid">
    <PageHeader
      :title="patient ? `AI 助手 · ${patient.realName}` : 'AI 助手'"
      description="基于当前患者病历、检查检验与 AI 工作流产出，提供临床问答与辅助决策。"
      eyebrow="门诊诊疗"
    >
      <template #actions>
        <ElButton @click="goQueue">待诊接诊</ElButton>
        <ElButton :disabled="!registerId" @click="clearHistory">清空对话</ElButton>
      </template>
    </PageHeader>

    <div v-if="!registerId" class="copilot-empty">
      <GlassCard>
        <ElEmpty description="请先选择患者后再使用 AI 助手">
          <ElButton type="primary" @click="goSelectPatient">选择患者</ElButton>
        </ElEmpty>
      </GlassCard>
    </div>

    <div v-else class="copilot-grid">
      <aside class="copilot-context">
        <GlassCard class="copilot-context__card">
          <div class="copilot-context__head">
            <h3>患者上下文</h3>
            <StatusTag v-if="patient" :tone="visitStateLabel(patient.visitState).tone">
              {{ visitStateLabel(patient.visitState).text }}
            </StatusTag>
          </div>
          <div v-if="patient" class="copilot-context__profile">
            <span class="copilot-context__avatar"><ElIcon :size="22"><User /></ElIcon></span>
            <div>
              <strong>{{ patient.realName }}</strong>
              <p>{{ patient.caseNumber }} · {{ patient.gender }} · {{ patient.age ?? '-' }}岁</p>
            </div>
          </div>
          <AiConsultSummaryCard
            v-if="patient?.aiConsultSummary"
            :summary="patient.aiConsultSummary"
            class="copilot-context__summary"
          />
          <p v-else class="copilot-context__hint">暂无 AI 预问诊摘要，仍可基于病历与检验结果问答。</p>
          <p class="copilot-context__note">
            助手可调用 W2/W4/W5 等工具；回答仅供临床参考，请结合实际情况决策。
          </p>
        </GlassCard>
      </aside>

      <section class="copilot-chat">
        <GlassCard class="copilot-chat__card">
          <div class="copilot-chat__toolbar">
            <span><ElIcon><MagicStick /></ElIcon> 临床 Copilot</span>
            <span class="copilot-chat__doctor">{{ authStore.realName || '医生' }}</span>
          </div>

          <ElScrollbar ref="chatScrollRef" class="copilot-chat__scroll" v-loading="historyLoading">
            <div v-if="!messages.length" class="copilot-chat__welcome">
              <p>你好，我是你的 AI 临床助手。你可以询问患者病情、检验解读、鉴别诊断或用药建议。</p>
              <div class="copilot-chat__quick">
                <button
                  v-for="item in quickPrompts"
                  :key="item"
                  type="button"
                  class="copilot-chat__quick-btn"
                  @click="sendMessage(item)"
                >
                  {{ item }}
                </button>
              </div>
            </div>
            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="copilot-chat__bubble"
              :class="`is-${msg.role}`"
            >
              <MarkdownContent v-if="msg.role === 'assistant'" :source="msg.content" />
              <p v-else>{{ msg.content }}</p>
            </div>
          </ElScrollbar>

          <div class="copilot-chat__composer">
            <ElInput
              v-model="draft"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="输入临床问题，例如：这个指标异常说明什么？"
              @keydown.enter.exact.prevent="sendMessage()"
            />
            <ElButton type="primary" :loading="loading" :icon="Promotion" @click="sendMessage()">
              发送
            </ElButton>
          </div>
        </GlassCard>
      </section>
    </div>
  </div>
</template>

<style scoped>
.copilot-grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: var(--space-4);
  min-height: 560px;
}

.copilot-context__card,
.copilot-chat__card {
  height: 100%;
  padding: var(--space-5);
}

.copilot-context__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.copilot-context__head h3 {
  margin: 0;
  font-size: 16px;
}

.copilot-context__profile {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  margin-block-end: var(--space-4);
}

.copilot-context__avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.copilot-context__profile p,
.copilot-context__hint,
.copilot-context__note {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.copilot-context__summary {
  margin-block-end: var(--space-4);
}

.copilot-context__note {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.copilot-chat__card {
  display: flex;
  flex-direction: column;
  min-height: 560px;
}

.copilot-chat__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-block-end: var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.copilot-chat__scroll {
  flex: 1;
  min-height: 360px;
  margin-block-end: var(--space-4);
}

.copilot-chat__welcome p {
  color: var(--color-text-muted);
  line-height: 1.7;
}

.copilot-chat__quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
}

.copilot-chat__quick-btn {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
}

.copilot-chat__quick-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

.copilot-chat__bubble {
  max-width: 88%;
  margin-block-end: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  line-height: 1.7;
  font-size: 14px;
}

.copilot-chat__bubble.is-user {
  margin-inline-start: auto;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.copilot-chat__bubble.is-assistant {
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.copilot-chat__composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-3);
  align-items: end;
}

@media (max-width: 960px) {
  .copilot-grid {
    grid-template-columns: 1fr;
  }
}
</style>
