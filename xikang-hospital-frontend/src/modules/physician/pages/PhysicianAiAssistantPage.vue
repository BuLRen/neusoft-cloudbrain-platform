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
  ElOption,
  ElScrollbar,
  ElSelect,
} from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  CircleCheck,
  CircleClose,
  Delete,
  Document,
  Grid,
  Headset,
  MagicStick,
  Paperclip,
  Plus,
  Promotion,
  QuestionFilled,
  Refresh,
  Search,
  StarFilled,
  User,
} from '@element-plus/icons-vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import AiConsultSummaryCard from '../components/AiConsultSummaryCard.vue'
import MedicalRecordSummaryCard from '../components/MedicalRecordSummaryCard.vue'
import AgentActionCard from '../components/AgentActionCard.vue'
import AgentConfirmCard from '../components/AgentConfirmCard.vue'
import { applyConfirmCompletions, enrichAssistantMessage, parseStoredThoughts, sanitizeAgentContent, stripBlocksForDisplay } from '../utils/copilotConfirm'
import {
  WORKFLOW_CATALOG,
  detectRunningWorkflow,
  emptyWorkflowCompletion,
  fetchWorkflowCompletion,
  mergeWorkflowCompletion,
  resolveWorkflowState,
  workflowCompletionFromMessages,
  workflowResultRoute,
  workflowStatusLabel,
  type WorkflowCatalogItem,
  type WorkflowId,
  type WorkflowRunState,
} from '../utils/workflowStatus'
import { copilotApi } from '@/shared/api/modules/copilot'
import { physicianApi, type MedicalRecord, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { useAuthStore } from '@/app/stores/auth'
import { PHYSICIAN_ASSISTANT, PHYSICIAN_QUEUE, VISIT_STATE, visitStateLabel } from '../constants/visitState'
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
const patient = ref<PhysicianPatient | null>(null)
const medicalRecord = ref<MedicalRecord | null>(null)
const medicalRecordLoading = ref(false)
const workflowCompletion = ref(emptyWorkflowCompletion())
const pendingWorkflowId = ref<WorkflowId | null>(null)
const workflowStatusLoading = ref(false)
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

const onboardingCards = [
  {
    title: '调取工作流',
    description: '运行初步诊断、检查推荐等工作流，快速获取 AI 辅助结论',
    prompt: '请根据当前患者信息运行初步诊断与检查推荐工作流',
  },
  {
    title: '补充病历',
    description: '根据口述或预问诊信息，生成并补充病历草案',
    prompt: '帮我把预问诊内容补充到病历中，并生成病历草案',
  },
  {
    title: '查询检查项目',
    description: '在医技目录中检索检查检验项目，辅助开立申请',
    prompt: '请检索适合当前病情的检查检验项目并说明理由',
  },
] as const

const knowledgeBaseItems = [
  { label: '临床指南库', count: '1,248 条' },
  { label: '检验参考值库', count: '860 条' },
  { label: '药品说明书', count: '2,310 条' },
  { label: '影像知识库', count: '420 条' },
] as const

const recommendedActions = ref([...composerPrompts.slice(0, 4)])

const draftCharCount = computed(() => draft.value.length)

const patientStatusItems = computed(() => {
  if (!patient.value) return []
  const visit = visitStateLabel(patient.value.visitState)
  const hasRecord = Boolean(medicalRecord.value?.readme?.trim())
  const hasPrevisit = Boolean(patient.value.hasAiConsultation)
  const examDone =
    patient.value.visitState === VISIT_STATE.EXAM_COMPLETED ||
    patient.value.visitState === VISIT_STATE.ENDED

  return [
    { label: '就诊状态', value: visit.text, tone: visit.tone },
    { label: '检查检验', value: examDone ? '已完成' : '未完成', tone: examDone ? 'success' : 'warning' },
    { label: '患者病历', value: hasRecord ? '已填写' : '未填写', tone: hasRecord ? 'success' : 'warning' },
    { label: 'AI 预问诊', value: hasPrevisit ? '有记录' : '无记录', tone: hasPrevisit ? 'success' : 'neutral' },
  ]
})

const medicalRecordDraftPreview = computed(() => {
  if (!medicalRecord.value) return ''
  const parts = [
    medicalRecord.value.readme,
    medicalRecord.value.present,
    medicalRecord.value.proposal,
  ].filter(Boolean)
  return parts.join('\n\n').trim()
})

const runningWorkflowId = computed(() =>
  pendingWorkflowId.value ?? detectRunningWorkflow(messages.value, loading.value),
)

const workflowItems = computed(() =>
  WORKFLOW_CATALOG.map((item) => ({
    ...item,
    state: resolveWorkflowState(item.id, workflowCompletion.value, runningWorkflowId.value),
    statusLabel: workflowStatusLabel(
      resolveWorkflowState(item.id, workflowCompletion.value, runningWorkflowId.value),
    ),
  })),
)

async function refreshWorkflowStatus(options?: { skipMedicalRecordFetch?: boolean }) {
  if (!registerId.value) {
    workflowCompletion.value = emptyWorkflowCompletion()
    return
  }

  workflowStatusLoading.value = true
  try {
    let record = medicalRecord.value
    if (!options?.skipMedicalRecordFetch) {
      try {
        record = await physicianApi.medicalRecord(registerId.value)
        medicalRecord.value = record
      } catch {
        record = medicalRecord.value
      }
    }

    const fromServer = await fetchWorkflowCompletion(registerId.value, record)
    const fromChat = workflowCompletionFromMessages(messages.value)
    workflowCompletion.value = mergeWorkflowCompletion(fromServer, fromChat)
  } finally {
    workflowStatusLoading.value = false
  }
}

async function runWorkflow(item: WorkflowCatalogItem) {
  if (loading.value || !currentSessionId.value) return
  pendingWorkflowId.value = item.id
  try {
    await sendMessage(item.prompt)
  } finally {
    pendingWorkflowId.value = null
  }
}

function handleWorkflowClick(item: WorkflowCatalogItem & { state: WorkflowRunState }) {
  if (item.state === 'running') return
  if (item.state === 'completed') {
    if (!registerId.value) return
    void router.push(workflowResultRoute(item.id, registerId.value))
    return
  }
  void runWorkflow(item)
}

function workflowClickDisabled(item: { state: WorkflowRunState }) {
  if (item.state === 'completed') return !registerId.value
  if (item.state === 'running') return true
  return loading.value || !currentSessionId.value
}

function workflowStateClass(state: ReturnType<typeof resolveWorkflowState>) {
  return `is-${state}`
}

function refreshRecommendedActions() {
  const shuffled = [...composerPrompts].sort(() => Math.random() - 0.5)
  recommendedActions.value = shuffled.slice(0, 4)
}

function handleSessionSelect(sessionId: number | string) {
  const id = typeof sessionId === 'string' ? Number(sessionId) : sessionId
  if (Number.isFinite(id)) void switchSession(id)
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
    await refreshWorkflowStatus({ skipMedicalRecordFetch: true })
  } catch {
    if (seq !== medicalRecordLoadSeq) return
    medicalRecord.value = null
    await refreshWorkflowStatus({ skipMedicalRecordFetch: true })
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
    await refreshWorkflowStatus()
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
    await refreshWorkflowStatus()
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
    await refreshWorkflowStatus()
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
    await refreshWorkflowStatus()
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
    workflowCompletion.value = emptyWorkflowCompletion()
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
  <div class="agent-shell">
    <div v-if="!registerId" class="agent-empty">
      <div class="agent-card agent-empty__card">
        <ElEmpty description="请先选择患者后再使用 AI 助手">
          <ElButton type="primary" @click="goSelectPatient">选择患者</ElButton>
        </ElEmpty>
      </div>
    </div>

    <template v-else>
      <header class="agent-topbar">
        <div class="agent-topbar__left">
          <ElButton class="agent-topbar__back" text :icon="ArrowLeft" @click="goQueue">返回</ElButton>
          <span class="agent-topbar__logo" aria-hidden="true"><ElIcon><StarFilled /></ElIcon></span>
          <div class="agent-topbar__brand">
            <strong>临床 Agent</strong>
            <small>门诊诊疗 Copilot</small>
          </div>
          <span class="agent-topbar__status">
            <span class="agent-topbar__status-dot" aria-hidden="true" />
            在线 / 就绪
          </span>
        </div>

        <div class="agent-topbar__center">
          <ElSelect
            v-if="sessions.length"
            :model-value="currentSessionId ?? undefined"
            class="agent-topbar__session-select"
            placeholder="选择对话"
            @change="handleSessionSelect"
          >
            <ElOption
              v-for="session in sessions"
              :key="session.id"
              :label="session.title"
              :value="session.id"
            />
          </ElSelect>
          <span v-else class="agent-topbar__session-fallback">新对话</span>
        </div>

        <div class="agent-topbar__right">
          <span class="agent-topbar__user">
            <ElIcon><User /></ElIcon>
            {{ authStore.realName || '医生' }}
          </span>
          <ElButton class="agent-topbar__action" :icon="Search" @click="goQueue">待诊接诊</ElButton>
          <ElButton
            class="agent-topbar__action"
            :disabled="!registerId || !currentSessionId"
            @click="clearHistory"
          >
            清空
          </ElButton>
          <ElButton class="agent-topbar__action agent-topbar__action--icon" circle :icon="QuestionFilled" />
        </div>
      </header>

      <div class="agent-body">
        <aside class="agent-panel agent-panel--left">
          <div class="agent-card agent-card--panel">
            <div class="agent-panel__head">
              <h3>患者上下文</h3>
              <StatusTag v-if="patient" :tone="visitStateLabel(patient.visitState).tone">
                {{ visitStateLabel(patient.visitState).text }}
              </StatusTag>
            </div>

            <div v-if="patient" class="agent-patient">
              <span class="agent-patient__avatar"><ElIcon :size="22"><User /></ElIcon></span>
              <div class="agent-patient__info">
                <strong>{{ patient.realName }}</strong>
                <p>{{ patient.caseNumber }} · {{ patient.gender || '-' }} · {{ patient.age ?? '-' }}岁</p>
              </div>
            </div>

            <div class="agent-sessions" v-loading="sessionsLoading">
              <div class="agent-sessions__head">
                <h4>对话列表</h4>
                <ElButton type="primary" link :icon="Plus" @click="createNewSession">新建</ElButton>
              </div>
              <ul v-if="sessions.length" class="agent-sessions__list">
                <li
                  v-for="session in sessions"
                  :key="session.id"
                  class="agent-sessions__item"
                  :class="{ 'is-active': session.id === currentSessionId }"
                >
                  <button type="button" class="agent-sessions__btn" @click="switchSession(session.id)">
                    <span class="agent-sessions__title" :title="session.title">{{ session.title }}</span>
                    <span class="agent-sessions__time">{{ formatSessionTime(session.updatedAt) }}</span>
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
              <p v-else class="agent-sessions__empty">暂无对话，点击新建开始</p>
            </div>
          </div>

          <div class="agent-context-actions">
            <button
              type="button"
              class="agent-context-actions__btn"
              :class="{ 'is-active': contextPanel === 'ai-consult' }"
              @click="toggleContextPanel('ai-consult')"
            >
              <ElIcon><MagicStick /></ElIcon>
              <span>AI 预问诊</span>
            </button>
            <button
              type="button"
              class="agent-context-actions__btn"
              :class="{ 'is-active': contextPanel === 'medical-record' }"
              @click="toggleContextPanel('medical-record')"
            >
              <ElIcon><Document /></ElIcon>
              <span>患者病历</span>
            </button>
          </div>

          <div v-if="contextPanel" class="agent-card agent-card--panel agent-context-panel">
            <AiConsultSummaryCard
              v-if="contextPanel === 'ai-consult'"
              :summary="patient?.aiConsultSummary"
              :has-ai-consultation="patient?.hasAiConsultation"
            />
            <MedicalRecordSummaryCard
              v-else
              :record="medicalRecord"
              :loading="medicalRecordLoading"
            />
          </div>
        </aside>

        <main class="agent-main">
          <div v-if="patient" class="agent-card agent-patient-banner">
            <div class="agent-patient-banner__title">
              <strong>当前患者：{{ patient.realName }}</strong>
              <span>{{ patient.gender || '-' }} · 门诊</span>
            </div>
            <div class="agent-patient-banner__stats">
              <div
                v-for="item in patientStatusItems"
                :key="item.label"
                class="agent-patient-banner__stat"
                :class="`is-${item.tone}`"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>

          <div class="agent-card agent-chat">
            <ElScrollbar ref="chatScrollRef" class="agent-chat__scroll" v-loading="historyLoading">
              <div v-if="!messages.length" class="agent-chat__welcome">
                <p class="agent-chat__welcome-lead">
                  你好，我是 Dify 临床 Copilot。当前患者信息如下，你希望我先做什么？
                </p>
                <div class="agent-onboarding">
                  <button
                    v-for="(card, index) in onboardingCards"
                    :key="card.title"
                    type="button"
                    class="agent-onboarding__card"
                    @click="sendMessage(card.prompt)"
                  >
                    <span class="agent-onboarding__index">{{ index + 1 }}</span>
                    <div class="agent-onboarding__body">
                      <strong>{{ card.title }}</strong>
                      <p>{{ card.description }}</p>
                    </div>
                    <ElIcon class="agent-onboarding__arrow"><ArrowRight /></ElIcon>
                  </button>
                </div>
              </div>

              <div
                v-for="(msg, index) in messages"
                :key="index"
                class="agent-chat__bubble"
                :class="`is-${msg.role}`"
              >
                <template v-if="msg.role === 'assistant'">
                  <p v-if="msg.agentStatus" class="agent-chat__agent-status">
                    <span class="agent-chat__spinner" aria-hidden="true" />
                    {{ msg.agentStatus }}
                  </p>
                  <MarkdownContent v-if="msg.content" :source="msg.content" />
                  <p
                    v-else-if="!msg.agentStatus && isStreaming(index)"
                    class="agent-chat__agent-status"
                  >
                    <span class="agent-chat__spinner" aria-hidden="true" />
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
                    class="agent-agent-thoughts"
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
                  <div class="agent-action-result">
                    <div class="agent-action-result__head">
                      <ElIcon :class="msg.actionResult?.success ? 'is-success' : 'is-error'">
                        <CircleCheck v-if="msg.actionResult?.success" />
                        <CircleClose v-else />
                      </ElIcon>
                      <div>
                        <strong>{{ msg.actionResult?.label || '工作流执行' }}</strong>
                        <p>{{ msg.actionResult?.summary || msg.content }}</p>
                      </div>
                    </div>
                    <ElCollapse v-if="msg.actionResult?.rawData" class="agent-action-result__detail">
                      <ElCollapseItem title="查看完整结果" name="detail">
                        <pre>{{ JSON.stringify(msg.actionResult.rawData, null, 2) }}</pre>
                      </ElCollapseItem>
                    </ElCollapse>
                  </div>
                </template>

                <p v-else class="agent-chat__text">{{ msg.content }}</p>
              </div>
            </ElScrollbar>

            <div class="agent-composer">
              <div class="agent-composer__input-wrap">
                <ElInput
                  v-model="draft"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  :disabled="!currentSessionId || loading"
                  placeholder="输入临床问题，例如：帮我智能分析初步诊断"
                  @keydown.enter.exact.prevent="sendMessage()"
                />
                <div class="agent-composer__toolbar">
                  <div class="agent-composer__tools">
                    <button type="button" class="agent-composer__tool" aria-label="附件"><ElIcon><Paperclip /></ElIcon></button>
                    <button type="button" class="agent-composer__tool" aria-label="语音"><ElIcon><Headset /></ElIcon></button>
                    <button type="button" class="agent-composer__tool" aria-label="工具"><ElIcon><Grid /></ElIcon></button>
                  </div>
                  <div class="agent-composer__actions">
                    <span class="agent-composer__count">{{ draftCharCount }} / 1000</span>
                    <ElButton
                      v-if="loading"
                      type="danger"
                      plain
                      @click="stopGeneration"
                    >
                      停止
                    </ElButton>
                    <ElButton
                      class="agent-composer__send"
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
              </div>
            </div>
          </div>
        </main>

        <aside class="agent-panel agent-panel--right">
          <div class="agent-card agent-card--panel">
            <div class="agent-side__head">
              <h3>工作流</h3>
            </div>
            <ul class="agent-workflow-list" v-loading="workflowStatusLoading">
              <li v-for="item in workflowItems" :key="item.id">
                <button
                  type="button"
                  class="agent-workflow-list__btn"
                  :class="{ 'is-completed': item.state === 'completed' }"
                  :disabled="workflowClickDisabled(item)"
                  :aria-label="item.state === 'completed' ? `查看${item.label}结果` : `运行${item.label}`"
                  @click="handleWorkflowClick(item)"
                >
                  <span>{{ item.label }}</span>
                  <span
                    class="agent-workflow-list__status"
                    :class="workflowStateClass(item.state)"
                  >
                    {{ item.state === 'completed' ? '查看结果' : item.statusLabel }}
                  </span>
                </button>
              </li>
            </ul>
          </div>

          <div class="agent-card agent-card--panel">
            <div class="agent-side__head">
              <h3>知识库</h3>
            </div>
            <ul class="agent-knowledge-list">
              <li v-for="item in knowledgeBaseItems" :key="item.label">
                <span>{{ item.label }}</span>
                <em>{{ item.count }}</em>
              </li>
            </ul>
          </div>

          <div class="agent-card agent-card--panel">
            <div class="agent-side__head">
              <h3>病历草稿</h3>
            </div>
            <p v-if="medicalRecordDraftPreview" class="agent-draft-preview">{{ medicalRecordDraftPreview }}</p>
            <p v-else class="agent-draft-empty">对话中生成的病历草案将显示在这里</p>
          </div>

          <div class="agent-card agent-card--panel">
            <div class="agent-side__head">
              <h3>推荐动作</h3>
              <ElButton link :icon="Refresh" @click="refreshRecommendedActions">换一换</ElButton>
            </div>
            <ul class="agent-recommend-list">
              <li v-for="item in recommendedActions" :key="item">
                <button type="button" @click="sendMessage(item)">{{ item }}</button>
              </li>
            </ul>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.agent-shell {
  --agent-bg: #f4f8fc;
  --agent-surface: #ffffff;
  --agent-surface-2: #f7fbff;
  --agent-border: rgba(72, 118, 169, 0.14);
  --agent-text: #243c55;
  --agent-muted: #8ba0b6;
  --agent-accent: #0b7cdf;
  --agent-accent-soft: #e9f4ff;
  --agent-success: #059669;
  --agent-warning: #d97706;
  --agent-msg-max-height: min(360px, 42vh);
  color-scheme: light;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  max-height: 100dvh;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 0%, rgba(47, 141, 247, 0.1), transparent 28%),
    radial-gradient(circle at 88% 12%, rgba(32, 194, 211, 0.08), transparent 24%),
    var(--agent-bg);
  color: var(--agent-text);
}

.agent-empty {
  display: grid;
  place-items: center;
  flex: 1;
  padding: 24px;
}

.agent-empty__card {
  width: min(480px, 100%);
  padding: 32px;
}

.agent-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 360px) minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--agent-border);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  box-shadow: 0 1px 0 rgba(72, 118, 169, 0.06);
}

.agent-topbar__left,
.agent-topbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.agent-topbar__right {
  justify-content: flex-end;
}

.agent-topbar__back {
  color: var(--agent-muted);
}

.agent-topbar__logo {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--agent-accent);
  background: var(--agent-accent-soft);
}

.agent-topbar__brand strong {
  display: block;
  font-size: 15px;
}

.agent-topbar__brand small {
  display: block;
  margin-top: 2px;
  color: var(--agent-muted);
  font-size: 11px;
}

.agent-topbar__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #059669;
  font-size: 12px;
}

.agent-topbar__status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 6px rgba(52, 211, 153, 0.45);
}

.agent-topbar__center {
  min-width: 0;
}

.agent-topbar__session-select {
  width: 100%;
}

.agent-topbar__session-select :deep(.el-select__wrapper) {
  background: var(--agent-surface);
  box-shadow: inset 0 0 0 1px var(--agent-border);
}

.agent-topbar__session-fallback {
  display: block;
  text-align: center;
  color: var(--agent-muted);
  font-size: 13px;
}

.agent-topbar__user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--agent-surface);
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-topbar__action {
  --el-button-bg-color: var(--agent-surface);
  --el-button-border-color: var(--agent-border);
  --el-button-text-color: var(--agent-text);
  --el-button-hover-bg-color: var(--agent-accent-soft);
  --el-button-hover-border-color: rgba(11, 124, 223, 0.28);
  --el-button-hover-text-color: var(--agent-accent);
}

.agent-body {
  flex: 1;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 300px;
  gap: 14px;
  min-height: 0;
  overflow: hidden;
  padding: 14px;
}

.agent-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  min-width: 0;
}

.agent-panel--left {
  overflow: hidden;
}

.agent-panel--right {
  overflow-y: auto;
}

.agent-card {
  border: 1px solid var(--agent-border);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 24px rgba(49, 105, 171, 0.06);
  backdrop-filter: blur(10px);
}

.agent-card--panel {
  padding: 14px;
}

.agent-panel__head,
.agent-side__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.agent-panel__head h3,
.agent-side__head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.agent-patient {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 12px;
  background: var(--agent-surface-2);
}

.agent-patient__avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: var(--agent-accent);
  background: var(--agent-accent-soft);
}

.agent-patient__info p {
  margin: 4px 0 0;
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.agent-sessions__head h4 {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
}

.agent-sessions__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
}

.agent-sessions__item {
  display: flex;
  align-items: stretch;
  border-radius: 10px;
  background: var(--agent-surface-2);
  box-shadow: inset 0 0 0 1px var(--agent-border);
}

.agent-sessions__item.is-active {
  box-shadow: inset 0 0 0 1px rgba(11, 124, 223, 0.28);
  background: var(--agent-accent-soft);
}

.agent-sessions__btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--agent-text);
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.agent-sessions__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.agent-sessions__time {
  color: var(--agent-muted);
  font-size: 11px;
}

.agent-sessions__empty {
  margin: 0;
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-context-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.agent-context-actions__btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 72px;
  padding: 10px;
  border: 1px solid var(--agent-border);
  border-radius: 12px;
  background: var(--agent-surface-2);
  color: var(--agent-muted);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}

.agent-context-actions__btn:hover,
.agent-context-actions__btn.is-active {
  border-color: rgba(11, 124, 223, 0.32);
  color: var(--agent-accent);
  background: var(--agent-accent-soft);
}

.agent-context-panel {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.agent-context-panel :deep(.ai-consult-card),
.agent-context-panel :deep(.record-card) {
  background: transparent;
  border: none;
  box-shadow: none;
  color: var(--agent-text);
}

.agent-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
}

.agent-patient-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
}

.agent-patient-banner__title strong {
  display: block;
  font-size: 15px;
}

.agent-patient-banner__title span {
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-patient-banner__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  flex: 1;
}

.agent-patient-banner__stat {
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--agent-surface-2);
  text-align: center;
}

.agent-patient-banner__stat span {
  display: block;
  color: var(--agent-muted);
  font-size: 11px;
}

.agent-patient-banner__stat strong {
  display: block;
  margin-top: 4px;
  font-size: 12px;
}

.agent-patient-banner__stat.is-success strong { color: var(--agent-success); }
.agent-patient-banner__stat.is-warning strong { color: var(--agent-warning); }
.agent-patient-banner__stat.is-primary strong { color: var(--agent-accent); }

.agent-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.agent-chat__scroll {
  flex: 1 1 0;
  height: 0;
  min-height: 0;
  overflow: hidden;
  padding: 16px;
}

.agent-chat__scroll :deep(.el-scrollbar) {
  height: 100%;
}

.agent-chat__scroll :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.agent-chat__scroll :deep(.el-scrollbar__view) {
  padding-bottom: 4px;
}

.agent-chat__welcome-lead {
  margin: 0 0 14px;
  color: var(--agent-muted);
  line-height: 1.7;
}

.agent-onboarding {
  display: grid;
  gap: 10px;
}

.agent-onboarding__card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--agent-border);
  border-radius: 12px;
  background: var(--agent-surface-2);
  color: var(--agent-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.agent-onboarding__card:hover {
  border-color: rgba(11, 124, 223, 0.32);
  background: var(--agent-accent-soft);
}

.agent-onboarding__index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--agent-accent-soft);
  color: var(--agent-accent);
  font-size: 13px;
  font-weight: 700;
}

.agent-onboarding__body p {
  margin: 4px 0 0;
  color: var(--agent-muted);
  font-size: 12px;
  line-height: 1.5;
}

.agent-onboarding__arrow {
  color: var(--agent-muted);
}

.agent-chat__bubble {
  max-width: min(760px, 96%);
  margin-bottom: 14px;
  padding: 12px 16px;
  border-radius: 14px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  min-width: 0;
}

.agent-chat__text {
  margin: 0;
  max-height: var(--agent-msg-max-height);
  overflow-y: auto;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  overscroll-behavior: contain;
}

.agent-chat__bubble.is-user {
  margin-inline-start: auto;
  color: #1f5c91;
  background: #dceeff;
  box-shadow: inset 0 0 0 1px rgba(119, 183, 246, 0.28);
}

.agent-chat__bubble.is-assistant {
  background: #ffffff;
  box-shadow:
    0 6px 18px rgba(54, 96, 143, 0.05),
    inset 0 0 0 1px var(--agent-border);
}

.agent-chat__bubble.is-action_result {
  background: #ecfff7;
  box-shadow: inset 0 0 0 1px rgba(40, 186, 132, 0.26);
}

.agent-chat__agent-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--agent-accent);
  background: var(--agent-accent-soft);
  font-size: 12px;
}

.agent-chat__spinner {
  width: 10px;
  height: 10px;
  border: 2px solid var(--agent-accent);
  border-top-color: transparent;
  border-radius: 50%;
  animation: agent-spin 0.7s linear infinite;
}

@keyframes agent-spin {
  to { transform: rotate(360deg); }
}

.agent-agent-thoughts {
  margin-top: 10px;
  border: none;
  background: #f8fbff;
  border-radius: 10px;
}

.agent-agent-thoughts :deep(.el-collapse-item__header) {
  background: transparent;
  color: var(--agent-muted);
  border-bottom-color: var(--agent-border);
}

.agent-agent-thoughts ul {
  margin: 0;
  padding-left: 18px;
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-action-result__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.agent-action-result__head p {
  margin: 4px 0 0;
  color: var(--agent-muted);
  max-height: var(--agent-msg-max-height);
  overflow-y: auto;
  overscroll-behavior: contain;
  white-space: pre-wrap;
  word-break: break-word;
}

.agent-action-result__head .el-icon.is-success { color: var(--agent-success); }
.agent-action-result__head .el-icon.is-error { color: #f87171; }

.agent-action-result__detail pre {
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--agent-muted);
  font-size: 12px;
  overflow-x: auto;
}

.agent-composer {
  border-top: 1px solid var(--agent-border);
  background: rgba(255, 255, 255, 0.95);
}

.agent-composer__input-wrap {
  padding: 10px 16px 14px;
}

.agent-composer__input-wrap :deep(.el-textarea__inner) {
  min-height: 88px !important;
  padding: 12px 14px;
  border: 1px solid #d7e8f7;
  border-radius: 12px;
  background: #fbfdff;
  color: var(--agent-text);
  box-shadow: none;
}

.agent-composer__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.agent-composer__tools {
  display: flex;
  gap: 8px;
}

.agent-composer__tool {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--agent-border);
  border-radius: 10px;
  background: var(--agent-surface-2);
  color: var(--agent-muted);
  cursor: pointer;
}

.agent-composer__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-composer__count {
  color: var(--agent-muted);
  font-size: 12px;
}

.agent-composer__send {
  --el-button-bg-color: var(--agent-accent);
  --el-button-border-color: var(--agent-accent);
  --el-button-hover-bg-color: #0969c7;
  --el-button-hover-border-color: #0969c7;
  min-height: 40px;
  border-radius: 10px;
}

.agent-workflow-list,
.agent-knowledge-list,
.agent-recommend-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.agent-workflow-list__btn,
.agent-recommend-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--agent-border);
  border-radius: 10px;
  background: var(--agent-surface-2);
  color: var(--agent-text);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.agent-workflow-list__btn.is-completed:not(:disabled):hover {
  border-color: rgba(5, 150, 105, 0.32);
  background: #ecfff7;
}

.agent-workflow-list__btn:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.agent-workflow-list__status {
  color: var(--agent-muted);
  font-size: 11px;
}

.agent-workflow-list__status.is-running {
  color: var(--agent-accent);
}

.agent-workflow-list__status.is-completed {
  color: var(--agent-success);
}

.agent-knowledge-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--agent-surface-2);
  font-size: 12px;
}

.agent-knowledge-list em {
  color: var(--agent-muted);
  font-style: normal;
  font-size: 11px;
}

.agent-draft-preview,
.agent-draft-empty {
  margin: 0;
  color: var(--agent-muted);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.agent-side__head :deep(.el-button) {
  color: var(--agent-muted);
}

.agent-chat :deep(.markdown-content) {
  color: var(--agent-text);
  background: #f8fbff;
  border-color: var(--agent-border);
  max-height: var(--agent-msg-max-height);
  overscroll-behavior: contain;
}

.agent-chat :deep(.agent-action-card),
.agent-chat :deep(.agent-confirm-card) {
  border-color: var(--agent-border);
  background: #ffffff;
  color: var(--agent-text);
}

.agent-chat :deep(.agent-action-card__title p),
.agent-chat :deep(.agent-confirm-card__title p),
.agent-chat :deep(.agent-action-card__reason),
.agent-chat :deep(.agent-confirm-card__reason),
.agent-chat :deep(.agent-confirm-card__hint) {
  color: var(--agent-muted);
}

@media (max-width: 1280px) {
  .agent-body {
    grid-template-columns: 250px minmax(0, 1fr);
  }

  .agent-panel--right {
    display: none;
  }
}

@media (max-width: 960px) {
  .agent-topbar {
    grid-template-columns: 1fr;
  }

  .agent-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .agent-panel--left {
    max-height: none;
  }

  .agent-patient-banner {
    flex-direction: column;
    align-items: stretch;
  }

  .agent-patient-banner__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
