<template>
  <div class="calling-board">
    <!-- 顶部：标题 + 时间 -->
    <header class="board-header">
      <div class="title">熙康云医院 · 全院候诊大屏</div>
      <div class="clock">{{ now }}</div>
    </header>

    <!-- 顶部滚动播报：最近叫号 -->
    <section class="recent-bar">
      <div class="recent-label">★ 最新叫号</div>
      <div class="recent-track">
        <transition-group name="recent-slide" tag="div" class="recent-list">
          <div v-for="item in recentCalls" :key="item.registerId" class="recent-item" :class="{ 'recent-flash': item.registerId === flashRegisterId }">
            <span class="r-dept">{{ item.departmentName || '—' }}</span>
            <span class="r-sep">·</span>
            <span class="r-doctor">{{ item.doctorName || '—' }}</span>
            <span class="r-sep">·</span>
            <span class="r-num">{{ item.queueNumber ?? '—' }}号</span>
            <span class="r-sep">·</span>
            <span class="r-name">{{ maskName(item.patientName) }}</span>
          </div>
        </transition-group>
        <div v-if="!recentCalls.length" class="recent-empty">暂无叫号</div>
      </div>
    </section>

    <!-- 科室卡片墙 -->
    <section class="dept-grid">
      <div v-for="dept in departments" :key="dept.departmentId"
           class="dept-card"
           :class="{ 'is-calling': dept.callingCount > 0, 'flash': dept.departmentId === flashDeptId }">
        <div class="dept-card__name">{{ dept.departmentName || '—' }}</div>
        <div v-if="dept.callingCount > 0" class="dept-card__main">
          <div v-for="c in dept.calling.slice(0, 3)" :key="c.registerId" class="dept-card__call">
            <div class="dept-card__doctor">{{ c.doctorName || '—' }}</div>
            <div class="dept-card__num">{{ c.queueNumber ?? '—' }}<span class="unit">号</span></div>
            <div class="dept-card__patient">{{ maskName(c.patientName) }}</div>
          </div>
        </div>
        <div v-else class="dept-card__idle">
          暂无叫号
        </div>
        <div class="dept-card__meta">
          候诊 <strong>{{ dept.waitingCount }}</strong> 人
        </div>
      </div>
      <div v-if="!departments.length" class="dept-empty">暂无科室数据</div>
    </section>

    <!-- 连接状态条 + 语音入口 -->
    <footer class="conn-bar">
      <span :class="['dot', connStatusClass]"></span>
      <span>{{ connStatusText }}</span>
      <span class="hint">提示音：<button @click="enableVoice">{{ voiceReady ? '已开启' : '点击开启' }}</button></span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { http } from '@/shared/api'

interface CallItem {
  registerId: number
  patientName?: string
  queueNumber?: number
  doctorName?: string
  departmentName?: string
  departmentId?: number
  calledTime?: string
  callStatus?: number
  callRound?: number
}
interface DeptItem {
  departmentId: number
  departmentName: string
  calling: CallItem[]
  callingCount: number
  waitingCount: number
}

// ===== 状态 =====
const now = ref(formatTime(new Date()))
const departments = ref<DeptItem[]>([])
const recentCalls = ref<CallItem[]>([])
const flashRegisterId = ref<number | null>(null)
const flashDeptId = ref<number | null>(null)

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

const voiceReady = ref(false)

let es: EventSource | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

// ===== 启动 =====
onMounted(() => {
  console.log('[DEBUG CallingBoard] onMounted! 页面已挂载')
  console.log('[DEBUG CallingBoard] 当前 URL =', window.location.href)
  console.log('[DEBUG CallingBoard] 当前 localStorage access_token =', localStorage.getItem('access_token'))
  clockTimer = setInterval(() => { now.value = formatTime(new Date()) }, 1000)
  refreshBoard()
  pollTimer = setInterval(refreshBoard, 10_000)
  connectSSE()
})

onUnmounted(() => {
  es?.close()
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
})

// ===== SSE 订阅 /stream/global =====
function connectSSE() {
  connState.value = 'connecting'
  console.log('[DEBUG CallingBoard] 即将创建 EventSource, URL = /api/registration/calling/stream/global')
  es = new EventSource('/api/registration/calling/stream/global')

  es.onopen = () => {
    console.log('[DEBUG CallingBoard] SSE onopen!')
    connState.value = 'open'
  }
  es.addEventListener('READY', (e: any) => {
    console.log('[DEBUG CallingBoard] SSE READY 事件', e.data)
    refreshBoard()
  })

  es.addEventListener('CALLED', (e: any) => {
    try {
      const payload = JSON.parse(e.data)
      handleEvent('CALLED', payload)
    } catch {}
  })
  es.addEventListener('ANSWERED', (e: any) => {
    try {
      const payload = JSON.parse(e.data)
      handleEvent('ANSWERED', payload)
    } catch {}
  })
  es.addEventListener('PASSED', (e: any) => {
    try {
      const payload = JSON.parse(e.data)
      handleEvent('PASSED', payload)
    } catch {}
  })

  es.onerror = (e: any) => {
    console.warn('[DEBUG CallingBoard] SSE onerror!', e, 'readyState =', es?.readyState)
    connState.value = 'error' /* EventSource 自动重连 */
  }
}

function handleEvent(type: string, payload: any) {
  // 新叫号：闪动对应科室卡片 + 顶部跑马灯置顶 + TTS
  if (type === 'CALLED') {
    flashDeptId.value = payload.departmentId ?? null
    flashRegisterId.value = payload.registerId ?? null
    setTimeout(() => { flashDeptId.value = null; flashRegisterId.value = null }, 1500)
    speak(payload)
  }
  // 任何事件后都刷一次（让数据更新到最新状态）
  refreshBoard()
}

function speak(payload: any) {
  if (!voiceReady.value) return
  if (!('speechSynthesis' in window)) return
  const text = `请${payload.patientName || ''}到${payload.departmentName || ''}${payload.doctorName || ''}医生处就诊`
  const u = new SpeechSynthesisUtterance(text)
  u.lang = 'zh-CN'
  window.speechSynthesis.speak(u)
}

function enableVoice() {
  voiceReady.value = true
  if ('speechSynthesis' in window) {
    const u = new SpeechSynthesisUtterance('语音已开启')
    u.lang = 'zh-CN'
    window.speechSynthesis.speak(u)
  }
}

// ===== HTTP 拉全院叫号板 =====
async function refreshBoard() {
  console.log('[DEBUG CallingBoard] refreshBoard 开始')
  try {
    const data: any = await http({
      url: '/registration/calling/board/all',
      method: 'GET',
      skipAuthHandling: true,
      skipErrorMessage: true,
    })
    console.log('[DEBUG CallingBoard] refreshBoard 成功, departments count =', data?.departments?.length)
    if (data) {
      departments.value = data.departments || []
      recentCalls.value = data.recent || []
    }
  } catch (err) {
    console.warn('[DEBUG CallingBoard] refreshBoard 失败:', err)
    // 静默失败，SSE 兜底
  }
}

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
  padding: 16px 28px;
  display: flex;
  flex-direction: column;
  font-family: "Noto Sans SC", -apple-system, sans-serif;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  padding-bottom: 10px;
}
.title { font-size: 26px; font-weight: 700; }
.clock { font-size: 20px; color: #cbd5e1; font-variant-numeric: tabular-nums; }

/* ===== 顶部滚动播报 ===== */
.recent-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 12px 0;
  padding: 12px 20px;
  background: rgba(251, 191, 36, 0.12);
  border: 1px solid rgba(251, 191, 36, 0.4);
  border-radius: 10px;
}
.recent-label {
  font-size: 18px;
  font-weight: 700;
  color: #fbbf24;
  letter-spacing: 4px;
  flex-shrink: 0;
}
.recent-track { flex: 1; overflow: hidden; }
.recent-list {
  display: flex;
  gap: 32px;
  overflow: hidden;
  white-space: nowrap;
}
.recent-item {
  font-size: 18px;
  color: #fde68a;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.recent-item.recent-flash {
  animation: recentFlash 1.5s ease-out;
  color: #fff;
  font-weight: 700;
}
.recent-item .r-sep { color: rgba(251, 191, 36, 0.5); }
.recent-item .r-num { color: #fff; font-weight: 700; margin: 0 4px; }
.recent-empty { font-size: 16px; color: #94a3b8; }

@keyframes recentFlash {
  0%   { background: rgba(251, 191, 36, 0.4); transform: scale(1.05); }
  100% { background: transparent; transform: scale(1); }
}

.recent-slide-enter-active, .recent-slide-leave-active { transition: all 0.4s ease; }
.recent-slide-enter-from { opacity: 0; transform: translateX(20px); }
.recent-slide-leave-to { opacity: 0; transform: translateX(-20px); }

/* ===== 科室卡片墙 ===== */
.dept-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  align-content: start;
  overflow-y: auto;
  padding: 4px;
}
.dept-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.2s ease;
}
.dept-card.is-calling {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.5);
}
.dept-card.flash {
  animation: cardFlash 1.5s ease-out;
}
@keyframes cardFlash {
  0%   { background: rgba(251, 191, 36, 0.5); transform: scale(1.04); }
  100% { background: rgba(59, 130, 246, 0.12); transform: scale(1); }
}

.dept-card__name {
  font-size: 20px;
  font-weight: 700;
  color: #93c5fd;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 6px;
}
.dept-card__main {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.dept-card__call {
  flex: 1;
  text-align: center;
  background: rgba(0, 0, 0, 0.2);
  padding: 8px 12px;
  border-radius: 6px;
  min-width: 80px;
}
.dept-card__doctor { font-size: 14px; color: #cbd5e1; margin-bottom: 2px; }
.dept-card__num {
  font-size: 40px;
  font-weight: 900;
  color: #fbbf24;
  line-height: 1.1;
}
.dept-card__num .unit { font-size: 18px; color: #fbbf24; margin-left: 2px; }
.dept-card__patient { font-size: 14px; color: #e2e8f0; margin-top: 2px; }

.dept-card__idle {
  text-align: center;
  color: #64748b;
  font-size: 16px;
  padding: 20px 0;
}
.dept-card__meta {
  font-size: 13px;
  color: #94a3b8;
  text-align: right;
}
.dept-card__meta strong { color: #cbd5e1; }

.dept-empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #94a3b8;
  padding: 60px 0;
  font-size: 22px;
}

/* ===== 底部连接条 ===== */
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
