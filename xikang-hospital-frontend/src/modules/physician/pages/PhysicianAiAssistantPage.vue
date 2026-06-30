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
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import AiConsultSummaryCard from '../components/AiConsultSummaryCard.vue'
import AgentActionCard from '../components/AgentActionCard.vue'
import { copilotApi } from '@/shared/api/modules/copilot'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { useAuthStore } from '@/app/stores/auth'
import { PHYSICIAN_ASSISTANT, PHYSICIAN_QUEUE, visitStateLabel } from '../constants/visitState'
import { usePhysicianPatientSelectStore } from '@/app/stores/physicianPatientSelect'
import type {
  AgentAction,
  AgentActionStatus,
  AgentActionType,
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
const actionStates = reactive<Record<string, AgentActionStatus>>({})
const rawContentMap = new Map<number, string>()
let abortController: AbortController | null = null

const ACTION_BLOCK_RE = /```action\r?\n([\s\S]*?)\r?\n```/g
const INCOMPLETE_ACTION_RE = /```action\r?\n[\s\S]*$/

const VALID_ACTION_TYPES = new Set<AgentActionType>([
  'trigger_preliminary_diagnosis',
  'trigger_w2',
  'trigger_w3',
  'trigger_w4',
  'trigger_w5',
])

const registerId = computed(() => {
  const raw = route.query.registerId ?? encounterStore.registerId
  const id = typeof raw === 'string' ? Number(raw) : typeof raw === 'number' ? raw : NaN
  return Number.isFinite(id) && id > 0 ? id : null
})

const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) ?? null,
)

const quickPrompts = [
  '请总结这位患者的主诉与病史要点',
  '根据现有检查检验结果，有哪些异常需要关注？',
  '请给出鉴别诊断思路',
  '是否需要补充哪些检查？',
  '帮我生成初步诊断',
]

function actionStateKey(messageIndex: number, actionIndex: number) {
  return `${messageIndex}-${actionIndex}`
}

function stripCompletedActionBlocks(content: string) {
  return content.replace(ACTION_BLOCK_RE, '').trim()
}

function stripActionBlocksForDisplay(content: string) {
  let text = stripCompletedActionBlocks(content)
  const incompleteMatch = text.match(INCOMPLETE_ACTION_RE)
  if (incompleteMatch && incompleteMatch.index !== undefined) {
    text = text.slice(0, incompleteMatch.index).trim()
  }
  return text
}

function parseActionBlocks(content: string) {
  const actions: AgentAction[] = []
  const normalized = content.replace(/\r\n/g, '\n')
  const text = normalized.replace(ACTION_BLOCK_RE, (_, json: string) => {
    try {
      const parsed = JSON.parse(json.trim()) as Partial<AgentAction>
      if (parsed.type && VALID_ACTION_TYPES.has(parsed.type) && parsed.label) {
        actions.push({
          type: parsed.type,
          label: parsed.label,
          description: parsed.description,
          reason: parsed.reason,
        })
      }
    } catch {
      /* ignore malformed action block */
    }
    return ''
  })
  return { text: text.trim(), actions }
}

function formatAgentThought(thought: CopilotAgentThought) {
  if (thought.tool) {
    return `正在调用 ${thought.tool}…`
  }
  if (thought.thought) {
    return thought.thought
  }
  return '思考中…'
}

function applyAgentThought(msg: CopilotMessage, thought: CopilotAgentThought) {
  const thoughts = msg.agentThoughts ? [...msg.agentThoughts, thought] : [thought]
  msg.agentThoughts = thoughts
  msg.agentStatus = formatAgentThought(thought)
}

function parseStoredThoughts(msg: CopilotMessage): CopilotMessage {
  if (msg.role !== 'assistant' || !msg.toolCallsJson) return msg
  try {
    const parsed = JSON.parse(msg.toolCallsJson) as CopilotAgentThought[]
    if (Array.isArray(parsed) && parsed.length) {
      return { ...msg, agentThoughts: parsed }
    }
  } catch {
    /* ignore */
  }
  return msg
}

function normalizeAssistantMessage(msg: CopilotMessage): CopilotMessage {
  if (msg.role !== 'assistant') return msg
  const withThoughts = parseStoredThoughts(msg)
  const { text, actions } = parseActionBlocks(withThoughts.content)
  return {
    ...withThoughts,
    content: text || withThoughts.content,
    actions: actions.length ? actions : withThoughts.actions,
  }
}

function resetChatState() {
  messages.value = []
  Object.keys(actionStates).forEach((key) => delete actionStates[key])
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
  } catch {
    patient.value = null
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
    const history = (await copilotApi.history(regId, sessionId)).map(normalizeAssistantMessage)
    resetChatState()
    messages.value = [...history, ...localResults]
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
        assistant.content = stripActionBlocksForDisplay(raw)
        assistant.agentStatus = undefined
        void scrollToBottom()
      },
      (thought) => {
        const assistant = messages.value[assistantIndex]
        if (!assistant) return
        applyAgentThought(assistant, thought)
        void scrollToBottom()
      },
      abortController.signal,
    )

    const assistant = messages.value[assistantIndex]
    if (assistant?.role === 'assistant') {
      const raw = rawContentMap.get(assistantIndex) ?? assistant.content
      const parsed = parseActionBlocks(raw)
      assistant.content = parsed.text || assistant.content
      assistant.actions = parsed.actions.length ? parsed.actions : undefined
      assistant.agentStatus = undefined
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
    const msg = error instanceof Error ? error.message : '发送失败'
    const assistant = messages.value[assistantIndex]
    if (assistant && !assistant.content) assistant.content = msg
    rawContentMap.delete(assistantIndex)
    ElMessage.error(msg)
  } finally {
    loading.value = false
    abortController = null
    await scrollToBottom()
  }
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
    sessions.value = []
    currentSessionId.value = null
    resetChatState()
    return
  }
  if (id !== prevId) {
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
    <PageHeader
      :title="patient ? `AI 助手 · ${patient.realName}` : 'AI 助手'"
      eyebrow="门诊诊疗"
    >
      <template #actions>
        <ElButton @click="goQueue">待诊接诊</ElButton>
        <ElButton :disabled="!registerId || !currentSessionId" @click="clearHistory">清空对话</ElButton>
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
                  <span class="copilot-sessions__title">{{ session.title }}</span>
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

          <AiConsultSummaryCard
            v-if="patient?.aiConsultSummary"
            :summary="patient.aiConsultSummary"
            class="copilot-context__summary"
          />
          <p v-else class="copilot-context__hint">暂无 AI 预问诊摘要，仍可基于病历与检验结果问答。</p>
          <p class="copilot-context__note">
            助手可提议运行初步诊断、W2/W3/W4/W5 等工作流；执行前需你确认。回答仅供临床参考，请结合实际情况决策。
          </p>
        </GlassCard>
      </aside>

      <section class="copilot-chat">
        <GlassCard class="copilot-chat__card">
          <div class="copilot-chat__toolbar">
            <span><ElIcon><MagicStick /></ElIcon> 临床 Agent</span>
            <span class="copilot-chat__session">{{ currentSession?.title || '新对话' }}</span>
            <span class="copilot-chat__doctor">{{ authStore.realName || '医生' }}</span>
          </div>

          <ElScrollbar ref="chatScrollRef" class="copilot-chat__scroll" v-loading="historyLoading">
            <div v-if="!messages.length" class="copilot-chat__welcome">
              <p>你好，我是 Dify 临床 Copilot。你可以询问病情、检验解读、鉴别诊断；需要时会自动调用初步诊断、检查推荐等工作流工具。</p>
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
              <template v-if="msg.role === 'assistant'">
                <p v-if="msg.agentStatus" class="copilot-chat__agent-status">{{ msg.agentStatus }}</p>
                <MarkdownContent v-if="msg.content" :source="msg.content" />
                <ElCollapse
                  v-if="msg.agentThoughts?.length"
                  class="copilot-agent-thoughts"
                >
                  <ElCollapseItem title="工具调用记录" name="thoughts">
                    <ul>
                      <li v-for="(thought, ti) in msg.agentThoughts" :key="ti">
                        <strong v-if="thought.tool">{{ thought.tool }}</strong>
                        <span v-if="thought.thought">{{ thought.thought }}</span>
                      </li>
                    </ul>
                  </ElCollapseItem>
                </ElCollapse>
                <AgentActionCard
                  v-for="(action, actionIndex) in msg.actions"
                  :key="`${index}-${actionIndex}`"
                  :action="action"
                  :status="getActionStatus(index, actionIndex)"
                  @confirm="handleActionConfirm(index, actionIndex, action)"
                  @dismiss="handleActionDismiss(index, actionIndex)"
                />
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

          <div class="copilot-chat__composer">
            <ElInput
              v-model="draft"
              type="textarea"
              :rows="3"
              resize="none"
              :disabled="!currentSessionId"
              placeholder="输入临床问题，例如：帮我生成初步诊断"
              @keydown.enter.exact.prevent="sendMessage()"
            />
            <ElButton
              type="primary"
              :loading="loading"
              :disabled="!currentSessionId"
              :icon="Promotion"
              @click="sendMessage()"
            >
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
  overflow-y: auto;
}

.copilot-sessions__item {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  border-radius: var(--radius-sm);
}

.copilot-sessions__item.is-active {
  background: var(--color-primary-soft);
}

.copilot-sessions__btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 8px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  min-width: 0;
}

.copilot-sessions__title {
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
  min-height: 560px;
}

.copilot-chat__toolbar {
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
  text-align: center;
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.copilot-chat__agent-status {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-color-primary);
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
</style>
