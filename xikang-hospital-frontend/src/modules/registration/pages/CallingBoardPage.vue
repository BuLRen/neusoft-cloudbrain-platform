<template>
  <div class="calling-board">
    <header class="board-header">
      <div class="title-wrap">
        <div class="title">{{ boardTitle }}</div>
        <div v-if="mockMode" class="mock-badge">演示数据</div>
        <div v-if="zoneLabel" class="zone-label">{{ zoneLabel }}</div>
      </div>
      <div class="clock">{{ now }}</div>
    </header>

    <CallingBoardHub
      v-if="isHubView"
      :departments="hubDepartments"
      :flash-dept-id="flashDeptId"
      @select="goToDepartment"
    />

    <CallingBoardDept
      v-else
      :hero-call="heroCall"
      :hero-flash="heroFlash"
      :flash-register-id="flashRegisterId"
      :flash-type="flashType"
      :recent-calls="recentCalls"
      :active-rows="activeRows"
      :table-page="tablePage"
      @back="goToHub"
    />

    <footer class="conn-bar">
      <span :class="['dot', connStatusClass]"></span>
      <span>{{ connStatusText }}</span>
      <span class="stats">{{ statsText }}</span>
      <span class="hint">提示音：<button @click="enableVoice">{{ voiceReady ? '已开启' : '点击开启' }}</button></span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { CallItem, DeptBoardItem } from '@/shared/types/calling'
import CallingBoardHub from '@/modules/registration/pages/CallingBoardHub.vue'
import CallingBoardDept from '@/modules/registration/pages/CallingBoardDept.vue'
import { apiUrl } from '@/config/api'
import {
  PAGE_ROTATE_MS,
  buildSpeakText,
  fetchDeptBoard,
  fetchHubBoard,
  formatTime,
} from '@/modules/registration/composables/useCallingBoard'
import {
  applyMockCallToHub,
  createMockCallingBoardData,
  createMockHubData,
  isCallingBoardMockMode,
  startCallingBoardHubMockSimulation,
  startCallingBoardMockSimulation,
} from '@/modules/registration/mock/callingBoardMock'

const route = useRoute()
const router = useRouter()

const mockMode = computed(() => isCallingBoardMockMode(route.query as Record<string, unknown>))
const isHubView = computed(() => !route.params.departmentId)
const departmentId = computed(() => {
  const raw = route.params.departmentId
  if (!raw || !String(raw).match(/^\d+$/)) return null
  return Number(raw)
})

const now = ref(formatTime(new Date()))
const hubDepartments = ref<DeptBoardItem[]>([])
const activeRows = ref<CallItem[]>([])
const recentCalls = ref<CallItem[]>([])
const totalWaiting = ref(0)
const heroCall = ref<CallItem | null>(null)
const flashRegisterId = ref<number | null>(null)
const flashType = ref<'called' | 'answered' | 'passed' | null>(null)
const flashDeptId = ref<number | null>(null)
const heroFlash = ref(false)
const tablePage = ref(0)
const voiceReady = ref(false)

const connState = ref<'idle' | 'connecting' | 'open' | 'error'>('idle')

const zoneLabel = computed(() => {
  const z = route.query.zone
  if (!z) return ''
  return Array.isArray(z) ? z[0] : String(z)
})

const boardTitle = computed(() => {
  if (!isHubView.value) {
    const name = activeRows.value[0]?.departmentName
      ?? hubDepartments.value.find(d => d.departmentId === departmentId.value)?.departmentName
    if (name) return `熙康云医院 · ${name}候诊大屏`
    return '熙康云医院 · 科室候诊大屏'
  }
  return '熙康云医院 · 全院候诊导航'
})

const statsText = computed(() => {
  if (isHubView.value) {
    const calling = hubDepartments.value.reduce((s, d) => s + d.callingCount, 0)
    const waiting = hubDepartments.value.reduce((s, d) => s + d.waitingCount, 0)
    return `全院候诊 ${waiting} 人 · 正在叫号 ${calling} 人`
  }
  return `本科候诊 ${totalWaiting.value} 人 · 正在叫号 ${activeRows.value.length} 人`
})

const connStatusText = computed(() => {
  if (mockMode.value) return '演示模式 · 模拟实时叫号'
  return ({
    idle: '未连接',
    connecting: '连接中…',
    open: '已订阅实时推送',
    error: '连接出错，自动重连中…',
  }[connState.value] || '')
})

const connStatusClass = computed(() => {
  if (mockMode.value) return 'dot-yellow'
  return ({
    idle: 'dot-gray',
    connecting: 'dot-yellow',
    open: 'dot-green',
    error: 'dot-red',
  }[connState.value] || 'dot-gray')
})

const totalPages = computed(() =>
  Math.max(1, Math.ceil(activeRows.value.length / 10)),
)

let es: EventSource | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let pageTimer: ReturnType<typeof setInterval> | null = null
let stopMockSimulation: (() => void) | null = null

function applyDeptData(data: ReturnType<typeof createMockCallingBoardData>) {
  activeRows.value = (data.active || []).filter(r => r.callStatus === 1)
  recentCalls.value = data.recent || []
  totalWaiting.value = data.stats?.totalWaiting ?? 0

  if (!heroCall.value && recentCalls.value.length) {
    heroCall.value = recentCalls.value[0]
  } else if (heroCall.value) {
    const stillActive = activeRows.value.some(r => r.registerId === heroCall.value?.registerId)
    if (!stillActive) {
      heroCall.value = recentCalls.value[0] ?? activeRows.value[0] ?? null
    }
  }

  if (tablePage.value >= totalPages.value) {
    tablePage.value = 0
  }
}

function triggerDeptHighlight(payload: CallItem) {
  heroCall.value = payload
  flashRegisterId.value = payload.registerId ?? null
  flashType.value = 'called'
  heroFlash.value = true
  setTimeout(() => {
    flashRegisterId.value = null
    flashType.value = null
    heroFlash.value = false
  }, 1500)
  speak(payload, false)
}

// ANSWERED：患者进诊室。叫号"主角"离场，对应行绿闪提示"已应答"。
// 不做 TTS（避免大厅反复播报"XX 已进诊室"造成噪音）。
function triggerAnsweredHighlight(payload: CallItem) {
  flashRegisterId.value = payload.registerId ?? null
  flashType.value = 'answered'
  setTimeout(() => {
    flashRegisterId.value = null
    flashType.value = null
  }, 1200)
}

// PASSED：过号。对应行柔橙闪提示"号已过"，不立即清 heroCall
// （医生可能马上重叫同一条，避免 hero 区抖动）。
function triggerPassedHighlight(payload: CallItem) {
  flashRegisterId.value = payload.registerId ?? null
  flashType.value = 'passed'
  setTimeout(() => {
    flashRegisterId.value = null
    flashType.value = null
  }, 1000)
}

function triggerHubHighlight(payload: CallItem) {
  flashDeptId.value = payload.departmentId ?? null
  setTimeout(() => { flashDeptId.value = null }, 1500)
  speak(payload, true)
}

function speak(payload: CallItem, includeDept: boolean) {
  if (!voiceReady.value || !('speechSynthesis' in window)) return
  const u = new SpeechSynthesisUtterance(buildSpeakText(payload, includeDept))
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

function goToDepartment(id: number) {
  router.push({ path: `/calling-board/${id}`, query: { ...route.query } })
}

function goToHub() {
  router.push({ path: '/calling-board', query: { ...route.query } })
}

async function refreshHub() {
  if (mockMode.value) return
  try {
    const data = await fetchHubBoard()
    if (data) hubDepartments.value = data.departments || []
  } catch { /* silent */ }
}

async function refreshDept() {
  if (mockMode.value || departmentId.value == null) return
  try {
    const data = await fetchDeptBoard(departmentId.value)
    if (data) applyDeptData(data)
  } catch { /* silent */ }
}

function refreshCurrent() {
  if (isHubView.value) refreshHub()
  else refreshDept()
}

function initMockHub() {
  const hub = createMockHubData()
  hubDepartments.value = hub.departments
  stopMockSimulation = startCallingBoardHubMockSimulation({
    onCalled: (item) => triggerHubHighlight(item),
    onHubUpdate: (updated) => { hubDepartments.value = updated.departments },
    getHub: () => ({ departments: hubDepartments.value, recent: [] }),
  })
}

function initMockDept() {
  if (departmentId.value == null) return
  const data = createMockCallingBoardData([departmentId.value])
  applyDeptData(data)
  heroCall.value = data.recent[0] ?? data.active[0] ?? null

  stopMockSimulation = startCallingBoardMockSimulation(
    {
      onCalled: (item) => {
        if (item.departmentId === departmentId.value) {
          triggerDeptHighlight(item)
        }
      },
      onRefresh: () => createMockCallingBoardData([departmentId.value!]),
    },
    () => activeRows.value,
    applyDeptData,
  )
}

function initMock() {
  stopMockSimulation?.()
  if (isHubView.value) initMockHub()
  else initMockDept()
}

function connectSSE() {
  es?.close()
  connState.value = 'connecting'

  const url = isHubView.value
    ? apiUrl('/registration/calling/stream/global')
    : apiUrl(`/registration/calling/stream/department/${departmentId.value}`)

  es = new EventSource(url)
  es.onopen = () => { connState.value = 'open' }
  es.addEventListener('READY', () => { refreshCurrent() })
  es.addEventListener('CALLED', (e: MessageEvent) => {
    try { handleEvent('CALLED', JSON.parse(e.data)) } catch { /* ignore */ }
  })
  es.addEventListener('ANSWERED', (e: MessageEvent) => {
    try { handleEvent('ANSWERED', JSON.parse(e.data)) } catch { /* ignore */ }
  })
  es.addEventListener('PASSED', (e: MessageEvent) => {
    try { handleEvent('PASSED', JSON.parse(e.data)) } catch { /* ignore */ }
  })
  es.onerror = () => { connState.value = 'error' }
}

function handleEvent(type: string, payload: CallItem) {
  // Hub 视图：所有事件都全量刷新科室卡片即可，CALLED 时额外高亮科室
  if (isHubView.value) {
    if (type === 'CALLED') triggerHubHighlight(payload)
    refreshHub()
    return
  }

  // Dept 视图：本科事件触发局部高亮 + 全量刷新
  if (payload.departmentId !== departmentId.value) {
    refreshDept()
    return
  }

  if (type === 'CALLED') {
    triggerDeptHighlight(payload)
  } else if (type === 'ANSWERED') {
    triggerAnsweredHighlight(payload)
  } else if (type === 'PASSED') {
    triggerPassedHighlight(payload)
  }
  refreshDept()
}

function startLive() {
  refreshCurrent()
  pollTimer = setInterval(refreshCurrent, 10_000)
  connectSSE()
}

function stopLive() {
  es?.close()
  es = null
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
  stopMockSimulation?.()
  stopMockSimulation = null
}

onMounted(() => {
  clockTimer = setInterval(() => { now.value = formatTime(new Date()) }, 1000)
  pageTimer = setInterval(() => {
    if (isHubView.value || totalPages.value <= 1) return
    tablePage.value = (tablePage.value + 1) % totalPages.value
  }, PAGE_ROTATE_MS)

  if (mockMode.value) initMock()
  else startLive()
})

onUnmounted(() => {
  stopLive()
  if (clockTimer) clearInterval(clockTimer)
  if (pageTimer) clearInterval(pageTimer)
})

watch([isHubView, departmentId, mockMode], () => {
  tablePage.value = 0
  heroCall.value = null
  stopLive()

  if (mockMode.value) {
    initMock()
    return
  }
  startLive()
})
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

.title-wrap { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; }
.title { font-size: 26px; font-weight: 700; }
.zone-label {
  font-size: 16px;
  color: #93c5fd;
  padding: 2px 12px;
  border: 1px solid rgba(147, 197, 253, 0.4);
  border-radius: 4px;
}
.mock-badge {
  font-size: 14px;
  color: #fde68a;
  padding: 2px 10px;
  border: 1px solid rgba(251, 191, 36, 0.6);
  border-radius: 4px;
  background: rgba(251, 191, 36, 0.15);
  animation: pulse 2s infinite;
}
.clock { font-size: 20px; color: #cbd5e1; font-variant-numeric: tabular-nums; }

.conn-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #94a3b8;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.stats { margin-left: 8px; }
.stats strong { color: #cbd5e1; }
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
