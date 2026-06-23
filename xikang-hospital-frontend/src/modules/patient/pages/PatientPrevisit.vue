<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import { aiApi } from '@/shared/api/modules/ai'
import type { PrevisitSummary } from '@/shared/types/ai'

interface ChatMessage {
  role: 'ai' | 'user' | 'system'
  content: string
  streaming?: boolean
}

const route = useRoute()
const router = useRouter()

const registerId = computed(() => Number(route.query.registerId))
const patientId = computed(() => Number(route.query.patientId) || 1)

const messages = ref<ChatMessage[]>([])
const sessionUuid = ref<string>('')
const finished = ref(false)
const summary = ref<PrevisitSummary | null>(null)
const inputValue = ref('')
const loading = ref(false)
const started = ref(false)
const messagesEl = ref<HTMLElement | null>(null)
let abortCtl: AbortController | null = null

function scrollToBottom() {
  nextTick(() => {
    const el = messagesEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function pushMessage(role: ChatMessage['role'], content: string, streaming = false) {
  messages.value.push({ role, content, streaming })
  scrollToBottom()
}

function updateLastMessage(chunk: string) {
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'ai') {
    last.content += chunk
    scrollToBottom()
  }
}

async function startSession() {
  if (!registerId.value) {
    ElMessage.warning('缺少挂号ID参数')
    return
  }
  // 先把上一轮未结束的请求中断，避免 emitter 残留
  abortCtl?.abort()
  loading.value = true
  started.value = false
  messages.value = []
  finished.value = false
  summary.value = null
  sessionUuid.value = ''

  // 先尝试加载历史
  try {
    const existing = await aiApi.previsitSession(registerId.value)
    if (existing && existing.exists) {
      // 恢复历史
      sessionUuid.value = existing.sessionUuid || ''
      const rounds = existing.rounds || []
      for (const r of rounds) {
        if (r.aiQuestion) {
          pushMessage('ai', r.aiQuestion)
        }
        if (r.patientAnswer) {
          pushMessage('user', r.patientAnswer)
        }
      }
      if (existing.state === 'completed') {
        finished.value = true
        const s = existing.summary
        if (s) {
          summary.value = {
            chiefComplaint: s.chiefComplaint,
            symptomDuration: s.symptomDuration,
            presentIllness: s.aiSummary,
            historySummary: s.historySummary,
            allergySummary: s.allergySummary,
            medicationSummary: s.medicationSummary,
            suggestedExam: s.suggestedExam ? JSON.parse(s.suggestedExam) : [],
          }
        }
        loading.value = false
        started.value = true
        return
      }
      // 进行中：不需要 start，直接等用户输入
      started.value = true
      loading.value = false
      return
    }
  } catch (e) {
    // 没有历史，正常往下走
  }

  // 开始新会话
  pushMessage('ai', '')
  abortCtl = new AbortController()
  try {
    const meta = await aiApi.previsitStart(
      { registerId: registerId.value, patientId: patientId.value },
      updateLastMessage,
      abortCtl.signal,
    )
    sessionUuid.value = meta.sessionUuid || ''
    const last = messages.value[messages.value.length - 1]
    if (last) last.streaming = false
    if (meta.finished) {
      finished.value = true
      await callFinish()
    }
  } catch (e) {
    const last = messages.value[messages.value.length - 1]
    if (last) last.content = '抱歉，AI 服务暂不可用，请稍后再试。'
  } finally {
    loading.value = false
    started.value = true
    abortCtl = null
  }
}

async function sendAnswer() {
  const text = inputValue.value.trim()
  if (!text || loading.value || finished.value) return

  pushMessage('user', text)
  inputValue.value = ''
  pushMessage('ai', '')
  loading.value = true
  abortCtl = new AbortController()
  try {
    const meta = await aiApi.previsitReply(
      { sessionUuid: sessionUuid.value, answer: text },
      updateLastMessage,
      abortCtl.signal,
    )
    const last = messages.value[messages.value.length - 1]
    if (last) last.streaming = false
    if (meta.finished) {
      finished.value = true
      await callFinish()
    }
  } catch (e) {
    const last = messages.value[messages.value.length - 1]
    if (last) last.content = '抱歉，回复失败，请重试。'
  } finally {
    loading.value = false
  }
}

async function callFinish() {
  try {
    const result = await aiApi.previsitFinish({ sessionUuid: sessionUuid.value })
    summary.value = result
    pushMessage(
      'system',
      `✅ 预问诊完成！\n主诉：${result.chiefComplaint || '—'}\n时长：${result.symptomDuration || '—'}\n既往史：${result.historySummary || '—'}\n过敏史：${result.allergySummary || '—'}`,
    )
  } catch (e) {
    pushMessage('system', '预问诊完成，但生成总结时出错。')
  }
}

function quickReply(text: string) {
  inputValue.value = text
  sendAnswer()
}

async function handleExit() {
  try {
    await ElMessageBox.confirm(
      finished.value
        ? '确定要返回吗？'
        : '确定要退出吗？当前对话已保存，可以稍后从挂号记录继续。',
      '提示',
      { confirmButtonText: '退出', cancelButtonText: '继续对话', type: 'warning' },
    )
  } catch {
    return // 继续对话
  }
  abortCtl?.abort()
  abortCtl = null
  // 优先返回上一页；没有历史则回到患者工作台
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'PatientWorkspace' })
  }
}

onBeforeUnmount(() => {
  abortCtl?.abort()
})

// 自动开始
if (registerId.value) {
  startSession()
}
</script>

<template>
  <div class="previsit-chat">
    <GlassCard class="chat-card">
      <div class="chat-header">
        <div class="header-left">
          <span class="ai-avatar">🤖</span>
          <div>
            <h2>AI 预问诊</h2>
            <p class="subtitle">挂号 #{{ registerId }} · {{ finished ? '已完成' : '进行中' }}</p>
          </div>
        </div>
        <button class="btn-exit" @click="handleExit">退出</button>
      </div>

      <div ref="messagesEl" class="messages">
        <div v-if="!started" class="placeholder">
          <p>正在初始化...</p>
        </div>
        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['message', `message-${msg.role}`]"
        >
          <div v-if="msg.role === 'ai'" class="avatar">🤖</div>
          <div v-else-if="msg.role === 'user'" class="avatar avatar-user">我</div>
          <div class="bubble" :class="{ streaming: msg.streaming }">
            <template v-if="msg.role === 'system'">
              <pre>{{ msg.content }}</pre>
            </template>
            <template v-else>
              {{ msg.content }}<span v-if="msg.streaming" class="cursor">▍</span>
            </template>
          </div>
        </div>
      </div>

      <div v-if="!finished && started" class="quick-options">
        <button
          v-for="opt in ['今天开始', '1-3天', '一周以上', '一个月以上']"
          :key="opt"
          class="quick-btn"
          :disabled="loading"
          @click="quickReply(opt)"
        >{{ opt }}</button>
      </div>

      <div v-if="!finished" class="input-bar">
        <input
          v-model="inputValue"
          class="input"
          type="text"
          placeholder="输入您的回答..."
          :disabled="loading || !started"
          @keydown.enter="sendAnswer"
        />
        <button
          class="send-btn"
          :disabled="!inputValue.trim() || loading || !started"
          @click="sendAnswer"
        >{{ loading ? '发送中...' : '发送' }}</button>
      </div>

      <div v-else class="finished-banner">
        <span>✅ 预问诊已完成</span>
        <p>已自动生成病历摘要，医生工作站可查看。</p>
      </div>
    </GlassCard>

    <GlassCard v-if="summary && finished" class="summary-card">
      <h3>预问诊摘要</h3>
      <div class="summary-grid">
        <div class="summary-item">
          <label>主诉</label>
          <p>{{ summary.chiefComplaint || '—' }}</p>
        </div>
        <div class="summary-item">
          <label>症状时长</label>
          <p>{{ summary.symptomDuration || '—' }}</p>
        </div>
        <div class="summary-item">
          <label>现病史</label>
          <p>{{ summary.presentIllness || '—' }}</p>
        </div>
        <div class="summary-item">
          <label>既往史</label>
          <p>{{ summary.historySummary || '—' }}</p>
        </div>
        <div class="summary-item">
          <label>过敏史</label>
          <p>{{ summary.allergySummary || '—' }}</p>
        </div>
        <div class="summary-item">
          <label>用药史</label>
          <p>{{ summary.medicationSummary || '—' }}</p>
        </div>
        <div v-if="summary.suggestedExam?.length" class="summary-item full">
          <label>建议检查</label>
          <div class="exam-list">
            <span v-for="exam in summary.suggestedExam" :key="exam" class="exam-tag">{{ exam }}</span>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.previsit-chat {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 80%;
  margin: var(--space-4) 10%;
}

.chat-card {
  display: flex;
  flex-direction: column;
  height: 70vh;
  padding: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-muted, #fafafa);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.ai-avatar {
  font-size: 32px;
}

.chat-header h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.btn-exit {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.placeholder {
  text-align: center;
  color: var(--color-text-muted);
  padding: var(--space-5);
}

.message {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
}

.message-user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary, #409eff);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.avatar-user {
  background: #67c23a;
  font-size: 12px;
}

.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f4f4f5;
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-user .bubble {
  background: var(--color-primary, #409eff);
  color: white;
}

.bubble.streaming {
  border: 1px dashed var(--color-primary);
}

.cursor {
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.message-system .bubble {
  background: #fdf6ec;
  color: #b88230;
  border: 1px solid #faecd8;
  max-width: 100%;
}

.message-system .bubble pre {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
}

.quick-options {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  padding: 0 var(--space-5) var(--space-2);
}

.quick-btn {
  padding: 4px 12px;
  background: white;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
}

.quick-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-bar {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--color-border);
}

.input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  font-size: 14px;
  outline: none;
}

.input:focus {
  border-color: var(--color-primary);
}

.send-btn {
  padding: 0 20px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.finished-banner {
  text-align: center;
  padding: var(--space-3);
  color: #67c23a;
  font-size: 14px;
  border-top: 1px solid var(--color-border);
  background: #f0f9eb;
}

.finished-banner p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.summary-card {
  padding: var(--space-5);
}

.summary-card h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 var(--space-4);
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

.summary-item.full {
  grid-column: 1 / -1;
}

.summary-item label {
  display: block;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.summary-item p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
}

.exam-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.exam-tag {
  padding: 2px 10px;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 10px;
  font-size: 12px;
}
</style>
