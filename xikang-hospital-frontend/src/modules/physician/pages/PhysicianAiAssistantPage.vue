<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElEmpty,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElScrollbar,
} from 'element-plus'
import { CircleCheck, CircleClose, Delete, MagicStick, Plus, Promotion, User } from '@element-plus/icons-vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import AiConsultSummaryCard from '../components/AiConsultSummaryCard.vue'
import MedicalRecordSummaryCard from '../components/MedicalRecordSummaryCard.vue'
import AgentActionCard from '../components/AgentActionCard.vue'
import AgentConfirmCard from '../components/AgentConfirmCard.vue'
import { applyConfirmCompletions, enrichAssistantMessage, parseStoredThoughts, sanitizeAgentContent, stripBlocksForDisplay } from '../utils/copilotConfirm'
import { copilotApi } from '@/shared/api/modules/copilot'
import { physicianApi, type MedicalRecord, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { useAuthStore } from '@/app/stores/auth'
import { PHYSICIAN_ASSISTANT, PHYSICIAN_QUEUE, visitStateLabel } from '../constants/visitState'
import { usePhysicianPatientSelectStore } from '@/app/stores/physicianPatientSelect'
import type {
  AgentAction,
  AgentActionStatus,
  AgentConfirmAction,
  CopilotAgentThought,
  CopilotMessage,
  CopilotSession,
} from '@/shared/types/copilot'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const encounterStore = useEncounterStore()
const patientSelectStore = usePhysicianPatientSelectStore()

const sessions = ref<CopilotSession[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<CopilotMessage[]>([])
const draft = ref('')
const loading = ref(false)
const historyLoading = ref(false)
const sessionsLoading = ref(false)
const chatScrollRef = ref<InstanceType<typeof ElScrollbar> | null>(null)
const composerInputRef = ref<InstanceType<typeof ElInput> | null>(null)
const patient = ref<PhysicianPatient | null>(null)
const medicalRecord = ref<MedicalRecord | null>(null)
const medicalRecordLoading = ref(false)
type ContextPanel = 'ai-consult' | 'medical-record'
const contextPanel = ref<ContextPanel | null>(null)
const actionStates = reactive<Record<string, AgentActionStatus>>({})
const confirmStates = reactive<Record<string, AgentActionStatus>>({})
const rawContentMap = new Map<number, string>()
let abortController: AbortController | null = null
let medicalRecordLoadSeq = 0

const registerId = computed(() => {
  const raw = route.query.registerId ?? encounterStore.registerId
  const id = typeof raw === 'string' ? Number(raw) : typeof raw === 'number' ? raw : NaN
  return Number.isFinite(id) && id > 0 ? id : null
})

const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) ?? null,
)

const composerPrompts = [
  '帮我智能分析初步诊断',
  '请总结这位患者的主诉与病史要点',
  '根据检查检验结果，有哪些异常需要关注？',
  '请给出鉴别诊断思路',
  '推荐需要补充哪些检查？',
  '帮我把预问诊内容补充到病历中',
]

function applyComposerPrompt(text: string) {
  if (loading.value || !currentSessionId.value) return
  draft.value = text
  void nextTick(() => {
    composerInputRef.value?.focus?.()
  })
}

function actionStateKey(messageIndex: number, actionIndex: number) {
  return `${messageIndex}-${actionIndex}`
}

function confirmStateKey(messageIndex: number, confirmIndex: number) {
  return `confirm-${messageIndex}-${confirmIndex}`
}

function syncAssistantMessage(index: number, options?: { display?: boolean }) {
  const current = messages.value[index]
  if (!current || current.role !== 'assistant') return
  const raw = rawContentMap.get(index) ?? current.content
  messages.value[index] = enrichAssistantMessage(current, raw, options)
}

const TOOL_LABELS: Record<string, string> = {
  tool_get_medical_record: '读取病历',
  tool_get_lab_results: '读取检查检验结果',
  tool_get_patient: '读取患者信息',
  tool_get_medical_technologies: '检索检查检验项目',
  tool_get_diseases: '检索疾病',
  tool_get_drugs: '检索药品',
  tool_get_prescriptions: '读取处方',
  tool_get_visit_timeline: '读取就诊时间线',
  tool_get_exam_suggestions: '读取检查建议',
  tool_get_diagnosis_suggestions: '读取诊断建议',
  tool_run_preliminary_diagnosis: '运行初步诊断',
  tool_run_w1: '运行病历结构化',
  tool_run_w2: '运行检查推荐',
  tool_run_w3: '运行结果解读',
  tool_run_w4: '运行确诊推理',
  tool_run_w5: '运行智能荐药',
  tool_draft_medical_record: '生成病历草案',
  tool_draft_order_basket: '生成检查检验草案',
  tool_draft_diagnosis: '生成确诊草案',
  tool_draft_prescription: '生成处方草案',
  tool_draft_preliminary_diagnosis: '生成初步诊断草案',
}

function friendlyToolName(tool: string) {
  return TOOL_LABELS[tool] ?? tool.replace(/^tool_/, '').replace(/_/g, ' ')
}

function formatThoughtPreview(thought: string) {
  return stripBlocksForDisplay(sanitizeAgentContent(thought))
}

function formatAgentThought(thought: CopilotAgentThought) {
  if (thought.tool) {
    return thought.observation
      ? `已完成「${friendlyToolName(thought.tool)}」`
      : `正在${friendlyToolName(thought.tool)}…`
  }
  // 无工具的 thought 通常是模型的推理/最终答复本身（含 <think> 与 confirm 块），
  // 不能原样塞进状态条，否则会泄露思考过程与 JSON；答复正文由 content 渲染。
  return '正在整理回复…'
}

function thoughtKey(thought: CopilotAgentThought) {
  if (thought.id != null && String(thought.id).trim()) return `id:${thought.id}`
  if (thought.position != null && String(thought.position).trim()) return `pos:${thought.position}`
  return null
}

function applyAgentThought(msg: CopilotMessage, thought: CopilotAgentThought): CopilotMessage {
  const thoughts = msg.agentThoughts ? [...msg.agentThoughts] : []
  const key = thoughtKey(thought)
  const existingIndex = key
    ? thoughts.findIndex((t) => thoughtKey(t) === key)
    : -1
  if (existingIndex >= 0) {
    thoughts[existingIndex] = { ...thoughts[existingIndex], ...thought }
  } else {
    thoughts.push(thought)
  }
  return {
    ...msg,
    agentThoughts: thoughts,
    agentStatus: formatAgentThought(thought),
  }
}

function normalizeAssistantMessage(msg: CopilotMessage): CopilotMessage {
  if (msg.role !== 'assistant') return msg
  return enrichAssistantMessage({ ...msg, agentThoughts: parseStoredThoughts(msg) })
}

function resetChatState() {
  messages.value = []
  Object.keys(actionStates).forEach((key) => delete actionStates[key])
  Object.keys(confirmStates).forEach((key) => delete confirmStates[key])
  rawContentMap.clear()
}

function formatSessionTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function loadPatient(id: number) {
  try {
    patient.value = await physicianApi.patient(id)
    if (!encounterStore.registerId) {
      encounterStore.applyPatient(patient.value)
    }
    await loadMedicalRecord(id)
  } catch {
    patient.value = null
    medicalRecord.value = null
  }
}

async function loadMedicalRecord(id: number) {
  medicalRecordLoading.value = true
  const seq = ++medicalRecordLoadSeq
  try {
    const record = await physicianApi.medicalRecord(id)
    if (seq !== medicalRecordLoadSeq) return
    medicalRecord.value = record
  } catch {
    if (seq !== medicalRecordLoadSeq) return
    medicalRecord.value = null
  } finally {
    if (seq === medicalRecordLoadSeq) medicalRecordLoading.value = false
  }
}

function toggleContextPanel(panel: ContextPanel) {
  contextPanel.value = contextPanel.value === panel ? null : panel
  if (contextPanel.value === 'medical-record' && registerId.value) {
    void loadMedicalRecord(registerId.value)
  }
}

async function loadSessions(id: number, preferSessionId?: number | null) {
  sessionsLoading.value = true
  try {
    let list = await copilotApi.listSessions(id)
    if (!list.length) {
      const created = await copilotApi.createSession(id)
      list = [created]
    }
    sessions.value = list

    const preferred = preferSessionId && list.some((s) => s.id === preferSessionId)
      ? preferSessionId
      : list[0]?.id ?? null
    currentSessionId.value = preferred

    if (preferred) {
      await loadHistory(id, preferred)
    } else {
      resetChatState()
    }
  } finally {
    sessionsLoading.value = false
  }
}

async function loadHistory(regId: number, sessionId: number) {
  historyLoading.value = true
  try {
    const localResults = messages.value.filter((msg) => msg.role === 'action_result')
    const [history, completions] = await Promise.all([
      copilotApi.history(regId, sessionId),
      copilotApi.confirmCompletions(regId, sessionId).catch(() => []),
    ])
    const normalized = history.map(normalizeAssistantMessage)
    resetChatState()
    messages.value = [...normalized, ...localResults]
    Object.assign(confirmStates, applyConfirmCompletions(messages.value, completions))
    await scrollToBottom()
  } finally {
    historyLoading.value = false
  }
}

async function switchSession(sessionId: number) {
  if (sessionId === currentSessionId.value || !registerId.value) return
  currentSessionId.value = sessionId
  resetChatState()
  await loadHistory(registerId.value, sessionId)
}

async function createNewSession() {
  if (!registerId.value) return
  try {
    const session = await copilotApi.createSession(registerId.value)
    sessions.value = [session, ...sessions.value]
    currentSessionId.value = session.id
    resetChatState()
    await scrollToBottom()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新建对话失败')
  }
}

async function deleteSession(session: CopilotSession) {
  if (!registerId.value) return
  try {
    await ElMessageBox.confirm(`确定删除对话「${session.title}」吗？`, '删除对话', { type: 'warning' })
  } catch {
    return
  }

  try {
    await copilotApi.deleteSession(registerId.value, session.id)
    sessions.value = sessions.value.filter((s) => s.id !== session.id)

    if (currentSessionId.value === session.id) {
      if (sessions.value.length) {
        await switchSession(sessions.value[0].id)
      } else {
        await loadSessions(registerId.value)
      }
    }
    ElMessage.success('对话已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function maybeAutoRenameSession(firstMessage: string) {
  if (!registerId.value || !currentSessionId.value || !currentSession.value) return
  if (currentSession.value.title !== '新对话') return

  const title = firstMessage.trim().slice(0, 15) || '新对话'
  try {
    await copilotApi.renameSession(registerId.value, currentSessionId.value, title)
    const idx = sessions.value.findIndex((s) => s.id === currentSessionId.value)
    if (idx >= 0) {
      sessions.value[idx] = { ...sessions.value[idx], title, updatedAt: new Date().toISOString() }
    }
  } catch {
    /* non-blocking */
  }
}

async function scrollToBottom() {
  await nextTick()
  const wrap = chatScrollRef.value?.wrapRef
  if (wrap) wrap.scrollTop = wrap.scrollHeight
}

async function sendMessage(text?: string) {
  const content = (text ?? draft.value).trim()
  if (!content || !registerId.value || !currentSessionId.value || loading.value) return

  const isFirstMessage = messages.value.filter((m) => m.role === 'user').length === 0
  draft.value = ''
  loading.value = true
  abortController = new AbortController()

  messages.value.push({ role: 'user', content })
  const assistantIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', agentThoughts: [] })
  rawContentMap.set(assistantIndex, '')
  await scrollToBottom()

  try {
    await copilotApi.chat(
      registerId.value,
      currentSessionId.value,
      content,
      (chunk) => {
        const assistant = messages.value[assistantIndex]
        if (!assistant) return
        const raw = (rawContentMap.get(assistantIndex) ?? '') + chunk
        rawContentMap.set(assistantIndex, raw)
        messages.value[assistantIndex] = enrichAssistantMessage(
          { ...assistant, agentStatus: undefined },
          raw,
          { display: true },
        )
        void scrollToBottom()
      },
      (thought) => {
        const assistant = messages.value[assistantIndex]
        if (!assistant) return
        const withThought = applyAgentThought(assistant, thought)
        const raw = rawContentMap.get(assistantIndex) ?? withThought.content
        messages.value[assistantIndex] = enrichAssistantMessage(withThought, raw, { display: true })
        void scrollToBottom()
      },
      abortController.signal,
    )

    const assistant = messages.value[assistantIndex]
    if (assistant?.role === 'assistant') {
      syncAssistantMessage(assistantIndex)
      const finalized = messages.value[assistantIndex]
      if (finalized?.role === 'assistant') {
        messages.value[assistantIndex] = { ...finalized, agentStatus: undefined }
        // 若流式阶段未挂上 confirms（极少见），从服务端历史重载并重新解析
        if (!messages.value[assistantIndex]?.confirms?.length && registerId.value && currentSessionId.value) {
          await loadHistory(registerId.value, currentSessionId.value)
        }
      }
      rawContentMap.delete(assistantIndex)
    }

    if (isFirstMessage) {
      await maybeAutoRenameSession(content)
      if (registerId.value) {
        const list = await copilotApi.listSessions(registerId.value)
        sessions.value = list
      }
    }
  } catch (error) {
    const assistant = messages.value[assistantIndex]
    if (error instanceof DOMException && error.name === 'AbortError') {
      if (assistant?.role === 'assistant') {
        syncAssistantMessage(assistantIndex)
        const finalized = messages.value[assistantIndex]
        if (finalized?.role === 'assistant') {
          messages.value[assistantIndex] = { ...finalized, agentStatus: undefined }
        }
        rawContentMap.delete(assistantIndex)
      }
    } else {
      const msg = error instanceof Error ? error.message : '发送失败'
      if (assistant && !assistant.content) assistant.content = msg
      rawContentMap.delete(assistantIndex)
      ElMessage.error(msg)
    }
  } finally {
    loading.value = false
    abortController = null
    await scrollToBottom()
  }
}

function stopGeneration() {
  abortController?.abort()
}

function isStreaming(index: number) {
  return loading.value && index === messages.value.length - 1
}

async function handleActionConfirm(messageIndex: number, actionIndex: number, action: AgentAction) {
  if (!registerId.value) return
  const key = actionStateKey(messageIndex, actionIndex)
  if (actionStates[key] === 'loading' || actionStates[key] === 'done') return

  actionStates[key] = 'loading'
  try {
    const result = await copilotApi.runAction(registerId.value, action.type)
    actionStates[key] = 'done'

    messages.value.push({
      role: 'action_result',
      content: result.summary,
      actionResult: {
        actionType: result.actionType,
        label: action.label,
        success: result.success,
        summary: result.summary,
        rawData: result.data,
      },
    })

    await loadPatient(registerId.value)
    await scrollToBottom()
  } catch (error) {
    actionStates[key] = 'pending'
    ElMessage.error(error instanceof Error ? error.message : '操作执行失败')
  }
}

function handleActionDismiss(messageIndex: number, actionIndex: number) {
  actionStates[actionStateKey(messageIndex, actionIndex)] = 'dismissed'
}

async function handleConfirmSubmit(
  messageIndex: number,
  confirmIndex: number,
  action: AgentConfirmAction,
  payload: Record<string, unknown>,
) {
  if (!registerId.value || !currentSessionId.value) return
  const key = confirmStateKey(messageIndex, confirmIndex)
  if (confirmStates[key] === 'loading' || confirmStates[key] === 'done') return

  confirmStates[key] = 'loading'
  try {
    const prepared = await copilotApi.prepareAction(
      registerId.value,
      currentSessionId.value,
      action.type,
      payload,
    )
    await copilotApi.confirmAction(
      registerId.value,
      currentSessionId.value,
      prepared.confirmationToken,
      payload,
    )
    confirmStates[key] = 'done'

    await loadPatient(registerId.value)
    await scrollToBottom()
  } catch (error) {
    confirmStates[key] = 'pending'
    ElMessage.error(error instanceof Error ? error.message : '确认提交失败')
  }
}

function handleConfirmDismiss(messageIndex: number, confirmIndex: number) {
  confirmStates[confirmStateKey(messageIndex, confirmIndex)] = 'dismissed'
}

function getConfirmStatus(messageIndex: number, confirmIndex: number): AgentActionStatus {
  return confirmStates[confirmStateKey(messageIndex, confirmIndex)] ?? 'pending'
}

function getActionStatus(messageIndex: number, actionIndex: number): AgentActionStatus {
  return actionStates[actionStateKey(messageIndex, actionIndex)] ?? 'pending'
}

async function clearHistory() {
  if (!registerId.value || !currentSessionId.value) return
  await copilotApi.clearHistory(registerId.value, currentSessionId.value)
  resetChatState()
  ElMessage.success('已清空当前对话')
}

function goSelectPatient() {
  patientSelectStore.open(PHYSICIAN_ASSISTANT)
}

function goQueue() {
  void router.push(PHYSICIAN_QUEUE)
}

watch(registerId, (id, prevId) => {
  if (!id) {
    patient.value = null
    medicalRecord.value = null
    contextPanel.value = null
    sessions.value = []
    currentSessionId.value = null
    resetChatState()
    return
  }
  if (id !== prevId) {
    contextPanel.value = null
    void loadPatient(id)
    void loadSessions(id)
  }
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

          <div class="copilot-sessions" v-loading="sessionsLoading">
            <div class="copilot-sessions__head">
              <h4>对话列表</h4>
              <ElButton type="primary" link :icon="Plus" @click="createNewSession">新建</ElButton>
            </div>
            <ul v-if="sessions.length" class="copilot-sessions__list">
              <li
                v-for="session in sessions"
                :key="session.id"
                class="copilot-sessions__item"
                :class="{ 'is-active': session.id === currentSessionId }"
              >
                <button type="button" class="copilot-sessions__btn" @click="switchSession(session.id)">
                  <span class="copilot-sessions__title" :title="session.title">{{ session.title }}</span>
                  <span class="copilot-sessions__time">{{ formatSessionTime(session.updatedAt) }}</span>
                </button>
                <ElButton
                  v-if="sessions.length > 1"
                  type="danger"
                  link
                  :icon="Delete"
                  aria-label="删除对话"
                  @click.stop="deleteSession(session)"
                />
              </li>
            </ul>
            <p v-else class="copilot-sessions__empty">暂无对话，点击新建开始</p>
          </div>

          <div class="copilot-context__tabs" role="tablist" aria-label="患者资料">
            <button
              type="button"
              role="tab"
              class="copilot-context__tab"
              :class="{ 'is-active': contextPanel === 'ai-consult' }"
              :aria-selected="contextPanel === 'ai-consult'"
              @click="toggleContextPanel('ai-consult')"
            >
              AI 预问诊
            </button>
            <button
              type="button"
              role="tab"
              class="copilot-context__tab"
              :class="{ 'is-active': contextPanel === 'medical-record' }"
              :aria-selected="contextPanel === 'medical-record'"
              @click="toggleContextPanel('medical-record')"
            >
              患者病历
            </button>
          </div>

          <div v-if="contextPanel" class="copilot-context__panel">
            <AiConsultSummaryCard
              v-if="contextPanel === 'ai-consult'"
              :summary="patient?.aiConsultSummary"
              :has-ai-consultation="patient?.hasAiConsultation"
              class="copilot-context__summary"
            />
            <MedicalRecordSummaryCard
              v-else
              :record="medicalRecord"
              :loading="medicalRecordLoading"
              class="copilot-context__summary"
            />
          </div>
        </GlassCard>
      </aside>

      <section class="copilot-chat">
        <GlassCard class="copilot-chat__card">
          <div class="copilot-chat__toolbar">
            <div class="copilot-chat__brand">
              <span class="copilot-chat__brand-icon"><ElIcon><MagicStick /></ElIcon></span>
              <div>
                <strong>临床 Agent</strong>
                <small>门诊诊疗 Copilot</small>
              </div>
            </div>
            <span class="copilot-chat__session">{{ currentSession?.title || '新对话' }}</span>
            <div class="copilot-chat__ops">
              <span class="copilot-chat__doctor"><ElIcon><User /></ElIcon>{{ authStore.realName || '医生' }}</span>
              <ElButton size="small" @click="goQueue">待诊接诊</ElButton>
              <ElButton size="small" :disabled="!registerId || !currentSessionId" @click="clearHistory">清空</ElButton>
            </div>
          </div>

          <ElScrollbar ref="chatScrollRef" class="copilot-chat__scroll" v-loading="historyLoading">
            <div v-if="!messages.length" class="copilot-chat__welcome">
              <p>你好，我是 Dify 临床 Copilot。你可以询问病情、检验解读、鉴别诊断；需要时会自动调用初步诊断、检查推荐等工作流工具。</p>
              <div class="copilot-chat__quick">
                <button
                  v-for="item in composerPrompts"
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
              <template v-if="msg.role === 'assistant'">
                <p v-if="msg.agentStatus" class="copilot-chat__agent-status">
                  <span class="copilot-chat__spinner" aria-hidden="true" />
                  {{ msg.agentStatus }}
                </p>
                <MarkdownContent v-if="msg.content" :source="msg.content" />
                <p
                  v-else-if="!msg.agentStatus && isStreaming(index)"
                  class="copilot-chat__agent-status"
                >
                  <span class="copilot-chat__spinner" aria-hidden="true" />
                  正在生成…
                </p>
                <AgentActionCard
                  v-for="(action, actionIndex) in msg.actions"
                  :key="`${index}-${actionIndex}`"
                  :action="action"
                  :status="getActionStatus(index, actionIndex)"
                  @confirm="handleActionConfirm(index, actionIndex, action)"
                  @dismiss="handleActionDismiss(index, actionIndex)"
                />
                <AgentConfirmCard
                  v-for="(confirm, confirmIndex) in msg.confirms"
                  :key="`confirm-${index}-${confirmIndex}`"
                  :action="confirm"
                  :status="getConfirmStatus(index, confirmIndex)"
                  @confirm="(payload) => handleConfirmSubmit(index, confirmIndex, confirm, payload)"
                  @dismiss="handleConfirmDismiss(index, confirmIndex)"
                />
                <ElCollapse
                  v-if="msg.agentThoughts?.length"
                  class="copilot-agent-thoughts"
                >
                  <ElCollapseItem title="工具调用记录" name="thoughts">
                    <ul>
                      <li v-for="(thought, ti) in msg.agentThoughts" :key="ti">
                        <strong v-if="thought.tool">{{ friendlyToolName(thought.tool) }}</strong>
                        <span v-if="thought.thought">{{ formatThoughtPreview(thought.thought) }}</span>
                      </li>
                    </ul>
                  </ElCollapseItem>
                </ElCollapse>
              </template>

              <template v-else-if="msg.role === 'action_result'">
                <div class="copilot-action-result">
                  <div class="copilot-action-result__head">
                    <ElIcon :class="msg.actionResult?.success ? 'is-success' : 'is-error'">
                      <CircleCheck v-if="msg.actionResult?.success" />
                      <CircleClose v-else />
                    </ElIcon>
                    <div>
                      <strong>{{ msg.actionResult?.label || '工作流执行' }}</strong>
                      <p>{{ msg.actionResult?.summary || msg.content }}</p>
                    </div>
                  </div>
                  <ElCollapse v-if="msg.actionResult?.rawData" class="copilot-action-result__detail">
                    <ElCollapseItem title="查看完整结果" name="detail">
                      <pre>{{ JSON.stringify(msg.actionResult.rawData, null, 2) }}</pre>
                    </ElCollapseItem>
                  </ElCollapse>
                </div>
              </template>

              <p v-else>{{ msg.content }}</p>
            </div>
          </ElScrollbar>

          <div class="copilot-chat__composer-area">
            <div v-if="currentSessionId" class="copilot-chat__suggestions">
              <span class="copilot-chat__suggestions-label">快捷提问</span>
              <div class="copilot-chat__suggestions-list">
                <button
                  v-for="item in composerPrompts"
                  :key="`composer-${item}`"
                  type="button"
                  class="copilot-chat__suggestion-btn"
                  :disabled="loading"
                  @click="applyComposerPrompt(item)"
                >
                  {{ item }}
                </button>
              </div>
            </div>

            <div class="copilot-chat__composer">
              <ElInput
                ref="composerInputRef"
                v-model="draft"
                type="textarea"
                :rows="3"
                resize="none"
                :disabled="!currentSessionId || loading"
                placeholder="输入临床问题，例如：帮我智能分析初步诊断"
                @keydown.enter.exact.prevent="sendMessage()"
              />
              <ElButton
                v-if="loading"
                type="danger"
                plain
                @click="stopGeneration"
              >
                停止
              </ElButton>
              <ElButton
                type="primary"
                :loading="loading"
                :disabled="!currentSessionId || loading"
                :icon="Promotion"
                @click="sendMessage()"
              >
                发送
              </ElButton>
            </div>
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

.copilot-sessions {
  margin-block-end: var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.copilot-sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-block-end: var(--space-2);
}

.copilot-sessions__head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.copilot-sessions__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 180px;
  overflow-x: hidden;
  overflow-y: auto;
}

.copilot-sessions__item {
  display: flex;
  align-items: stretch;
  gap: var(--space-1);
  min-width: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.copilot-sessions__item.is-active {
  background: var(--color-primary-soft);
}

.copilot-sessions__btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  min-width: 0;
  overflow: hidden;
}

.copilot-sessions__title {
  width: 100%;
  min-width: 0;
  font-size: 13px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copilot-sessions__time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-text-muted);
}

.copilot-sessions__empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
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
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.copilot-chat__toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  margin-block-end: var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.copilot-chat__session {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copilot-chat__scroll {
  flex: 1 1 0;
  min-height: 0;
  height: 0;
  margin-block-end: var(--space-4);
  overflow: hidden;
}

.copilot-chat__scroll :deep(.el-scrollbar) {
  height: 100%;
}

.copilot-chat__scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.copilot-chat__composer {
  flex-shrink: 0;
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

.copilot-chat__agent-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.copilot-chat__spinner {
  width: 10px;
  height: 10px;
  border: 2px solid var(--el-color-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: copilot-spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes copilot-spin {
  to {
    transform: rotate(360deg);
  }
}

.copilot-agent-thoughts {
  margin-top: 8px;
}

.copilot-agent-thoughts ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.copilot-chat__bubble.is-action_result {
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.6) 0%, rgba(255, 255, 255, 0.95) 100%);
  box-shadow: inset 0 0 0 1px rgba(110, 231, 183, 0.4);
}

.copilot-action-result__head {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.copilot-action-result__head strong {
  display: block;
  font-size: 14px;
}

.copilot-action-result__head p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.copilot-action-result__head .el-icon {
  margin-top: 2px;
  font-size: 18px;
}

.copilot-action-result__head .el-icon.is-success {
  color: #059669;
}

.copilot-action-result__head .el-icon.is-error {
  color: #dc2626;
}

.copilot-action-result__detail {
  margin-block-start: var(--space-3);
  border: none;
}

.copilot-action-result__detail pre {
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  max-height: 240px;
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

.copilot-page {
  --copilot-blue: #2f8df7;
  --copilot-blue-soft: #e8f3ff;
  --copilot-blue-softer: #f5faff;
  --copilot-green: #14a978;
  --copilot-green-soft: #ecfff7;
  --copilot-line: rgba(72, 118, 169, 0.14);
  --copilot-shadow: 0 16px 42px rgba(49, 105, 171, 0.1);
  display: flex;
  flex-direction: column;
  max-width: 1180px;
  height: calc(100dvh - 112px);
  max-height: calc(100dvh - 112px);
  min-height: 0;
  padding: 10px;
  overflow: hidden;
  border-radius: 24px;
  background:
    radial-gradient(circle at 18% 8%, rgba(47, 141, 247, 0.13), transparent 32%),
    linear-gradient(180deg, #f7fbff 0%, #eef7ff 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72);
}

.copilot-grid {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(230px, 270px) minmax(0, 1fr);
  gap: 14px;
  min-height: 0;
  height: 100%;
  max-height: 100%;
  align-items: stretch;
}

.copilot-context,
.copilot-chat {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.copilot-context__card,
.copilot-chat__card {
  border: 1px solid rgba(214, 231, 247, 0.92);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--copilot-shadow);
  backdrop-filter: blur(18px) saturate(1.2);
}

.copilot-context__card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  min-width: 0;
  padding: 16px;
  overflow: hidden;
  border-radius: 22px;
}

.copilot-context__head {
  margin-block-end: 0;
  flex-shrink: 0;
  min-width: 0;
}

.copilot-context__head h3 {
  font-size: 15px;
  color: #23415f;
}

.copilot-context__profile {
  margin-block-end: 0;
  padding: 12px;
  border-radius: 18px;
  background: linear-gradient(135deg, #f9fcff 0%, #eef7ff 100%);
  box-shadow: inset 0 0 0 1px rgba(206, 226, 245, 0.78);
  min-width: 0;
  flex-shrink: 0;
}

.copilot-context__profile > div {
  min-width: 0;
  overflow: hidden;
}

.copilot-context__profile p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copilot-context__avatar {
  width: 42px;
  height: 42px;
  color: #0b7cdf;
  background: #dff0ff;
  box-shadow: inset 0 0 0 4px rgba(255, 255, 255, 0.9);
}

.copilot-sessions {
  margin-block-end: 0;
  padding: 0;
  background: transparent;
  box-shadow: none;
  min-width: 0;
  overflow: hidden;
  flex-shrink: 0;
}

.copilot-sessions__head {
  padding-inline: 2px;
}

.copilot-sessions__list {
  display: grid;
  gap: 8px;
  max-height: 196px;
  overflow-x: hidden;
  overflow-y: auto;
}

.copilot-sessions__item {
  border-radius: 14px;
  background: #f7fbff;
  box-shadow: inset 0 0 0 1px rgba(215, 231, 246, 0.86);
  min-width: 0;
  overflow: hidden;
}

.copilot-sessions__item.is-active {
  background: #e9f4ff;
  box-shadow: inset 0 0 0 1px rgba(91, 164, 243, 0.28);
}

.copilot-sessions__btn {
  padding: 10px 12px;
}

.copilot-sessions__item :deep(.el-button) {
  flex-shrink: 0;
  align-self: center;
}

.copilot-context__summary {
  margin-block-end: 0;
}

.copilot-context__tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  flex-shrink: 0;
}

.copilot-context__tab {
  padding: 8px 10px;
  border: none;
  border-radius: 12px;
  background: #f7fbff;
  box-shadow: inset 0 0 0 1px rgba(215, 231, 246, 0.86);
  color: #5a728a;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background var(--duration-fast) var(--ease-standard),
    color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.copilot-context__tab:hover {
  color: #2a5f91;
  background: #eef7ff;
}

.copilot-context__tab.is-active {
  color: #0b7cdf;
  background: #e9f4ff;
  box-shadow: inset 0 0 0 1px rgba(91, 164, 243, 0.32);
}

.copilot-context__panel {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.copilot-context__panel :deep(.ai-consult-card),
.copilot-context__panel :deep(.record-card) {
  padding: 12px;
  border-radius: 16px;
}

.copilot-context__panel :deep(.ai-consult-grid),
.copilot-context__panel :deep(.record-card__grid) {
  grid-template-columns: 1fr;
  gap: 8px;
}

.copilot-context__panel :deep(.ai-consult-item),
.copilot-context__panel :deep(.record-card__item) {
  padding: 10px 12px;
}

.copilot-context__note,
.copilot-context__hint {
  margin: 0;
  padding: 12px;
  border-radius: 16px;
  background: #f7fbff;
  box-shadow: inset 0 0 0 1px rgba(215, 231, 246, 0.86);
  flex-shrink: 0;
  overflow-wrap: anywhere;
}

.copilot-chat__card {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  border-radius: 24px;
}

.copilot-chat__toolbar {
  flex-shrink: 0;
  margin-block-end: 0;
  padding: 14px 18px;
  border-bottom: 1px solid var(--copilot-line);
  background: rgba(255, 255, 255, 0.78);
}

.copilot-chat__brand,
.copilot-chat__ops,
.copilot-chat__doctor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.copilot-chat__brand {
  min-width: 190px;
  color: #21476c;
}

.copilot-chat__brand-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--copilot-blue);
  background: #e5f2ff;
}

.copilot-chat__brand strong {
  display: block;
  font-size: 14px;
}

.copilot-chat__brand small {
  display: block;
  margin-top: 2px;
  color: #8ba0b6;
  font-size: 11px;
}

.copilot-chat__session {
  flex: 1;
  min-width: 0;
  color: #243c55;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copilot-chat__doctor {
  padding: 5px 9px;
  border-radius: 999px;
  color: #5a728a;
  background: #f3f8fe;
}

.copilot-chat__scroll {
  flex: 1 1 0;
  min-height: 0;
  height: 0;
  margin-block-end: 0;
  padding: 16px 18px 8px;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(247, 251, 255, 0.62), rgba(255, 255, 255, 0.9)),
    repeating-linear-gradient(0deg, transparent 0 31px, rgba(224, 238, 250, 0.32) 32px);
}

.copilot-chat__scroll :deep(.el-scrollbar) {
  height: 100%;
}

.copilot-chat__scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.copilot-chat__welcome {
  padding: 18px;
  border-radius: 20px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--copilot-line);
}

.copilot-chat__welcome p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.copilot-chat__quick-btn {
  border-color: #d5e8fb;
  background: #f6fbff;
  color: #2a5f91;
}

.copilot-chat__bubble {
  position: relative;
  max-width: min(680px, 100%);
  margin-block-end: 14px;
  padding: 12px 16px;
  border-radius: 18px;
  line-height: 1.75;
  box-shadow: 0 8px 22px rgba(54, 96, 143, 0.06);
  overflow-wrap: anywhere;
}

.copilot-chat__bubble.is-user {
  margin-inline: auto 0;
  padding: 10px 16px 10px 42px;
  color: #1f5c91;
  background: #dceeff;
  box-shadow: inset 0 0 0 1px rgba(119, 183, 246, 0.28);
}

.copilot-chat__bubble.is-user::before {
  content: "";
  position: absolute;
  inset-block-start: 9px;
  inset-inline-start: 14px;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 36%, #2f8df7 0 4px, transparent 4.5px),
    radial-gradient(circle at 50% 78%, #2f8df7 0 7px, transparent 7.5px),
    rgba(255, 255, 255, 0.86);
}

.copilot-chat__bubble.is-assistant {
  max-width: min(760px, 96%);
  background: #fff;
  box-shadow:
    0 10px 28px rgba(54, 96, 143, 0.07),
    inset 0 0 0 1px rgba(216, 231, 247, 0.9);
}

.copilot-chat__bubble.is-action_result {
  max-width: min(760px, 96%);
  background: var(--copilot-green-soft);
  box-shadow: inset 0 0 0 1px rgba(40, 186, 132, 0.26);
}

.copilot-chat__agent-status {
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  color: #2777c9;
  background: #edf7ff;
}

.copilot-agent-thoughts {
  margin-top: 12px;
  border-radius: 14px;
  background: #f8fbff;
}

.copilot-chat__composer-area {
  flex-shrink: 0;
  border-top: 1px solid var(--copilot-line);
  background: rgba(255, 255, 255, 0.92);
}

.copilot-chat__suggestions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 18px 0;
}

.copilot-chat__suggestions-label {
  flex-shrink: 0;
  padding-top: 6px;
  color: #8ba0b6;
  font-size: 12px;
  font-weight: 600;
}

.copilot-chat__suggestions-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.copilot-chat__suggestion-btn {
  padding: 6px 12px;
  border: 1px solid #d5e8fb;
  border-radius: 999px;
  background: #f6fbff;
  color: #2a5f91;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard),
    color var(--duration-fast) var(--ease-standard);
}

.copilot-chat__suggestion-btn:hover:not(:disabled) {
  border-color: #8ec5fa;
  background: #e9f4ff;
  color: #0b7cdf;
}

.copilot-chat__suggestion-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.copilot-chat__composer {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: end;
  padding: 10px 18px 16px;
}

.copilot-chat__composer :deep(.el-textarea__inner) {
  min-height: 44px !important;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fbfdff;
  box-shadow: inset 0 0 0 1px #d7e8f7;
}

.copilot-chat__composer .el-button {
  min-height: 44px;
  border-radius: 14px;
}

@media (max-width: 960px) {
  .copilot-page {
    height: auto;
    max-height: none;
    overflow: visible;
  }

  .copilot-grid {
    height: auto;
    max-height: none;
    min-height: 720px;
  }

  .copilot-context__card,
  .copilot-chat__card {
    height: auto;
    max-height: none;
  }

  .copilot-chat__scroll {
    flex: 1;
    height: auto;
    min-height: 360px;
  }
}

@media (max-width: 720px) {
  .copilot-chat__toolbar,
  .copilot-chat__ops {
    flex-wrap: wrap;
  }

  .copilot-chat__brand,
  .copilot-chat__session {
    min-width: 0;
    flex: 1 1 100%;
    text-align: left;
  }

  .copilot-chat__composer-area .copilot-chat__composer {
    grid-template-columns: 1fr;
  }
}
</style>
