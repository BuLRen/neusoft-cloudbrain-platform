<template>
  <div class="calling-board">
    <!-- 顶部：标题 + 时间 -->
    <header class="board-header">
      <div class="title">
        熙康云医院 · 候诊大屏
        <span v-if="departmentName" class="dept">· {{ departmentName }}</span>
      </div>
      <div class="clock">{{ now }}</div>
    </header>

    <!-- 主区：当前叫号 -->
    <section class="now-calling" :class="{ flash: flashFlag }">
      <div class="label">★ 现在叫号 ★</div>
      <div v-if="current" class="card">
        <div class="doctor">{{ current.doctorName || '—' }} 医生</div>
        <div class="queue-number">{{ current.queueNumber ?? '—' }} <span class="unit">号</span></div>
        <div class="patient">{{ current.patientName || '—' }} 请就诊</div>
      </div>
      <div v-else class="empty">暂无叫号</div>
    </section>

    <!-- 候诊队列 -->
    <section class="waiting">
      <div class="section-title">候诊队列（{{ waitingList.length }} 人）</div>
      <div v-if="waitingList.length" class="queue-list">
        <div v-for="w in waitingList.slice(0, 8)" :key="w.registerId" class="queue-item">
          <span class="q-no">{{ w.queueNumber ?? '—' }}号</span>
          <span class="q-name">{{ maskName(w.patientName) }}</span>
          <span v-if="w.callStatus === 1" class="q-tag tag-called">已叫</span>
          <span v-else-if="w.callStatus === 3" class="q-tag tag-passed">过号</span>
        </div>
      </div>
      <div v-else class="empty-small">暂无候诊</div>
    </section>

    <!-- 连接状态条 -->
    <footer class="conn-bar">
      <span :class="['dot', connStatusClass]"></span>
      <span>{{ connStatusText }}</span>
      <span class="hint">提示音：<button @click="enableVoice">{{ voiceReady ? '已开启' : '点击开启' }}</button></span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/shared/api'

const route = useRoute()
const departmentId = computed(() => String(route.params.departmentId || ''))

// ===== 状态 =====
const now = ref(formatTime(new Date()))
const current = ref<any>(null)
const waitingList = ref<any[]>([])
const departmentName = ref('')
const flashFlag = ref(false)

const connState = ref<'idle' | 'connecting' | 'open' | 'error'>('idle')
const connStatusText = computed(() => ({
  idle: '未连接',
  connecting: '连接中…',
  open: '已订阅实时推送',
  error: '连接出错，自动重连中…',
}[connState.value] || ''))
const connStatusClass = computed(() => ({
  idle: 'dot-gray',
  connecting: 'dot-yellow',
  open: 'dot-green',
  error: 'dot-red',
}[connState.value] || 'dot-gray'))

// 音频：浏览器自动播放限制，需用户点击后才能播
const voiceReady = ref(false)
let ttsUtterance: SpeechSynthesisUtterance | null = null

// SSE & 定时器
let es: EventSource | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

// ===== 启动 =====
onMounted(() => {
  // 时钟
  clockTimer = setInterval(() => { now.value = formatTime(new Date()) }, 1000)

  // 初次拉全量
  refreshBoard()

  // 轮询兜底（10s 一次，SSE 万一断了也能保持基本可用）
  pollTimer = setInterval(refreshBoard, 10_000)

  // 订阅 SSE
  connectSSE()
})

onUnmounted(() => {
  es?.close()
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
})

// ===== SSE 订阅 =====
function connectSSE() {
  if (!departmentId.value) return
  connState.value = 'connecting'
  const url = `/api/registration/calling/stream/department/${departmentId.value}`
  es = new EventSource(url)

  es.onopen = () => { connState.value = 'open' }

  es.addEventListener('READY', () => {
    // 连接建立，立刻拉一次确保最新状态
    refreshBoard()
  })

  es.addEventListener('CALLED', (e: any) => {
    const payload = JSON.parse(e.data)
    handleEvent('CALLED', payload)
  })
  es.addEventListener('ANSWERED', (e: any) => {
    const payload = JSON.parse(e.data)
    handleEvent('ANSWERED', payload)
  })
  es.addEventListener('PASSED', (e: any) => {
    const payload = JSON.parse(e.data)
    handleEvent('PASSED', payload)
  })

  es.onerror = () => { connState.value = 'error' /* EventSource 自动重连 */ }
}

function handleEvent(type: string, payload: any) {
  if (type === 'CALLED' || (type === 'PASSED' && current.value?.registerId !== payload.registerId)) {
    // CALLED：更新当前叫号区域；PASSED 不一定更新主区，但若是别的号过号也刷一下
  }
  if (type === 'CALLED') {
    current.value = payload
    triggerFlash()
    speak(payload)
  } else if (type === 'ANSWERED') {
    // 当前号已应答，清空主区
    if (current.value?.registerId === payload.registerId) {
      current.value = null
    }
  } else if (type === 'PASSED') {
    // 当前号过号了，也清空主区
    if (current.value?.registerId === payload.registerId) {
      current.value = null
    }
  }
  // 任何事件后都刷一下候诊队列
  refreshBoard()
}

function triggerFlash() {
  flashFlag.value = false
  // 强制重排触发动画
  requestAnimationFrame(() => { flashFlag.value = true })
  setTimeout(() => { flashFlag.value = false }, 1200)
}

function speak(payload: any) {
  if (!voiceReady.value) return
  if (!('speechSynthesis' in window)) return
  const text = `请${payload.patientName || ''}到${payload.doctorName || ''}医生处就诊`
  ttsUtterance = new SpeechSynthesisUtterance(text)
  ttsUtterance.lang = 'zh-CN'
  window.speechSynthesis.speak(ttsUtterance)
}

function enableVoice() {
  voiceReady.value = true
  // 试播一句确认
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance('语音已开启')
    u.lang = 'zh-CN'
    window.speechSynthesis.speak(u)
  }
}

// ===== HTTP 拉取叫号板 =====
async function refreshBoard() {
  if (!departmentId.value) return
  try {
    // http() 已剥 ApiResult 外层，返回 data 本身
    const data: any = await http({
      url: `/api/registration/calling/board/${departmentId.value}`,
      method: 'GET',
      skipAuthHandling: true,
      skipErrorMessage: true,
    })
    if (data) {
      // 候诊队列
      waitingList.value = data.waiting || []
      // 主区：如果本地 current 没有就用后端 calling[0]
      if (!current.value && Array.isArray(data.calling) && data.calling.length > 0) {
        current.value = data.calling[0]
      }
      // 科室名
      const first = (data.calling?.[0]) || (data.waiting?.[0])
      if (first?.departmentName) departmentName.value = first.departmentName
    }
  } catch (e) {
    // 静默失败，SSE 兜底
  }
}

// ===== 辅助 =====
function formatTime(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function maskName(name?: string) {
  if (!name) return '—'
  if (name.length <= 1) return name
  if (name.length === 2) return name[0] + '*'
  return name[0] + '*'.repeat(name.length - 2) + name[name.length - 1]
}
</script>

<style scoped>
.calling-board {
  background: linear-gradient(180deg, #0f172a 0%, #1e3a8a 100%);
  color: #f8fafc;
  min-height: 100vh;
  padding: 20px 32px;
  display: flex;
  flex-direction: column;
  font-family: "Noto Sans SC", -apple-system, sans-serif;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  padding-bottom: 12px;
}
.title { font-size: 28px; font-weight: 700; }
.title .dept { color: #93c5fd; font-weight: 500; margin-left: 8px; }
.clock { font-size: 22px; color: #cbd5e1; font-variant-numeric: tabular-nums; }

.now-calling {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 16px 0;
}
.now-calling .label {
  font-size: 36px;
  color: #fbbf24;
  font-weight: 700;
  margin-bottom: 16px;
  letter-spacing: 8px;
}
.now-calling .card {
  background: rgba(255, 255, 255, 0.08);
  border: 3px solid #fbbf24;
  border-radius: 16px;
  padding: 32px 64px;
  text-align: center;
  min-width: 540px;
}
.now-calling .doctor { font-size: 32px; color: #93c5fd; margin-bottom: 12px; }
.now-calling .queue-number {
  font-size: 144px;
  font-weight: 900;
  color: #fff;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.now-calling .queue-number .unit { font-size: 56px; color: #fbbf24; margin-left: 8px; }
.now-calling .patient { font-size: 32px; color: #fde68a; margin-top: 12px; }
.now-calling .empty { font-size: 32px; color: #94a3b8; }

.now-calling.flash .card {
  animation: flash-card 1.2s ease-out;
}
@keyframes flash-card {
  0%   { background: rgba(251, 191, 36, 0.4); transform: scale(1.05); }
  100% { background: rgba(255, 255, 255, 0.08); transform: scale(1); }
}

.waiting {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
  padding: 16px 20px;
  margin-top: 8px;
}
.section-title { font-size: 22px; color: #93c5fd; margin-bottom: 12px; }
.queue-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.queue-item {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.q-no { font-size: 22px; font-weight: 700; color: #fbbf24; min-width: 60px; }
.q-name { font-size: 18px; flex: 1; }
.q-tag { font-size: 12px; padding: 2px 8px; border-radius: 10px; }
.tag-called { background: #f59e0b; color: #1e293b; }
.tag-passed { background: #ef4444; color: #fff; }
.empty-small { color: #94a3b8; font-size: 16px; }

.conn-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #94a3b8;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot-gray { background: #64748b; }
.dot-yellow { background: #fbbf24; animation: pulse 1s infinite; }
.dot-green { background: #22c55e; }
.dot-red { background: #ef4444; }
@keyframes pulse { 50% { opacity: 0.4; } }
.conn-bar .hint { margin-left: auto; }
.conn-bar button {
  background: transparent;
  border: 1px solid #475569;
  color: #cbd5e1;
  padding: 2px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.conn-bar button:hover { background: rgba(255, 255, 255, 0.08); }
</style>
