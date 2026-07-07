<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { parseQrPayload } from '@/shared/utils/qrProtocol'
import { http } from '@/shared/api/request'
import type { CheckInResult } from '@/shared/types/registration'

type Status = 'idle' | 'calling' | 'success' | 'fail'

const status = ref<Status>('idle')
const input = ref('')
const inputEl = ref<HTMLInputElement | null>(null)
const showHistory = ref(false)

const successResult = ref<CheckInResult | null>(null)
const errorMessage = ref('')

interface HistoryItem {
  time: string
  raw: string
  ok: boolean
  summary: string
}
const history = ref<HistoryItem[]>([])

const currentCalling = ref<{ registerId?: number; patientName?: string; queueNumber?: number } | null>(null)
const callingMe = computed(() => {
  if (!currentCalling.value || !successResult.value) return false
  return currentCalling.value.registerId === successResult.value.registerId
})
const voiceReady = ref(false)
let es: EventSource | null = null

const nowClock = ref('')
const nowDate = ref('')
let clockTimer: ReturnType<typeof setInterval> | null = null

function updateClock() {
  const d = new Date()
  nowClock.value = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  nowDate.value = `${d.getFullYear()}年${String(d.getMonth() + 1).padStart(2, '0')}月${String(d.getDate()).padStart(2, '0')}日 ${weekdays[d.getDay()]}`
}

function startCallingSubscription() {
  const doctorId = successResult.value?.doctorId
  if (!doctorId) return
  es?.close()
  const url = `/api/registration/calling/stream/doctor/${doctorId}`
  es = new EventSource(url)
  es.addEventListener('CALLED', (e: MessageEvent) => {
    try {
      const payload = JSON.parse(e.data)
      currentCalling.value = payload
      if (payload.registerId === successResult.value?.registerId) {
        speak(`请${payload.patientName || ''}到${payload.doctorName || ''}医生处就诊`)
      }
    } catch { /* ignore */ }
  })
  es.addEventListener('ANSWERED', (e: MessageEvent) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.registerId === successResult.value?.registerId) {
        currentCalling.value = null
      }
    } catch { /* ignore */ }
  })
  es.addEventListener('PASSED', () => { /* keep display */ })
}

function stopCallingSubscription() {
  es?.close()
  es = null
  currentCalling.value = null
}

function speak(text: string) {
  if (!voiceReady.value) return
  if (!('speechSynthesis' in window)) return
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

function currentTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function pushHistory(raw: string, ok: boolean, summary: string) {
  history.value.unshift({ time: currentTime(), raw, ok, summary })
  if (history.value.length > 10) history.value.pop()
}

async function handleScanned() {
  const raw = input.value
  if (!raw) return
  input.value = ''

  // 报到机持续扫码：非 idle 态下扫到新码，自动清掉上一个结果再走流程，
  // 不再要求用户点"继续扫码"按钮。SSE 旧订阅也要关，避免串到上一个医生的频道。
  if (status.value !== 'idle') {
    stopCallingSubscription()
    successResult.value = null
    errorMessage.value = ''
  }

  const parsed = parseQrPayload(raw)

  if (!parsed.ok) {
    status.value = 'fail'
    errorMessage.value = parsed.message
    pushHistory(raw, false, parsed.message)
    return
  }

  if (parsed.type !== 'REG') {
    status.value = 'fail'
    errorMessage.value = `暂不支持的业务类型：${parsed.type}`
    pushHistory(raw, false, '不支持的类型')
    return
  }

  status.value = 'calling'
  try {
    const result = await http<CheckInResult>({
      url: `/registration/${parsed.id}/check-in`,
      method: 'POST',
      skipErrorMessage: true,
      skipAuthHandling: true,
    })
    successResult.value = result
    status.value = 'success'
    pushHistory(raw, true, `${result.patientName ?? ''} 第${result.queueNumber}号`)
    startCallingSubscription()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '请求失败'
    status.value = 'fail'
    pushHistory(raw, false, errorMessage.value)
  }
}

function resetToIdle() {
  stopCallingSubscription()
  status.value = 'idle'
  successResult.value = null
  errorMessage.value = ''
  void nextTick(() => inputEl.value?.focus())
}

function focusInput() {
  inputEl.value?.focus()
}

/** calling 态不允许外部点击关闭，避免请求中关掉造成状态悬挂；success/fail 允许点遮罩关闭 */
function closeResultIfNotCalling() {
  if (status.value !== 'calling') resetToIdle()
}

// 状态切换后立刻把焦点拉回输入框，保证扫码枪随时都能扫入下一张码。
// 关键场景：报到成功卡片出现后，用户无需点任何按钮即可扫下一位患者。
watch(status, () => {
  void nextTick(() => inputEl.value?.focus())
})

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  focusInput()
})
onUnmounted(() => {
  stopCallingSubscription()
  if (clockTimer) clearInterval(clockTimer)
})

function formatClock(iso: string): string {
  if (!iso) return '--:--'
  const m = iso.match(/T(\d{2}:\d{2})/)
  return m ? m[1] : iso
}

const checkinMethods = [
  { icon: 'qr', color: '#3b82f6', title: '就诊二维码', desc: '微信/支付宝二维码' },
  { icon: 'id', color: '#22c55e', title: '身份证', desc: '第二代身份证原件' },
  { icon: 'card', color: '#f59e0b', title: '就诊卡', desc: '医院就诊卡/医保卡' },
]

const flowSteps = [
  { icon: 'scan', title: '扫描二维码/身份证/就诊卡', desc: '将证件对准扫描区' },
  { icon: 'verify', title: '核对就诊信息', desc: '确认个人就诊信息' },
  { icon: 'done', title: '完成报到', desc: '打印或获取报到凭证' },
]

const isNight = computed(() => {
  const h = new Date().getHours()
  return h < 6 || h >= 18
})
</script>

<template>
  <div class="checkin-page" @click="focusInput">
    <!-- 顶部栏 -->
    <header class="top-bar">
      <div class="hospital-brand">
        <div class="hospital-logo" aria-hidden="true">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="48" height="48" rx="12" fill="#2563eb" />
            <rect x="21" y="12" width="6" height="24" rx="1.5" fill="#fff" />
            <rect x="12" y="21" width="24" height="6" rx="1.5" fill="#fff" />
          </svg>
        </div>
        <div class="hospital-text">
          <span class="hospital-name">熙康云医院</span>
          <span class="hospital-slogan">智慧医疗 · 便捷就医</span>
        </div>
      </div>
      <div class="datetime">
        <span class="datetime__clock">{{ nowClock }}</span>
        <span class="datetime__date">{{ nowDate }}</span>
        <span class="datetime__moon" aria-hidden="true">
          <svg v-if="isNight" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 0 1-4.4 2.26 5.403 5.403 0 0 1-3.14-9.8c-.62-.28-1.3-.42-1.96-.5A9.002 9.002 0 0 0 12 3z" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z" />
          </svg>
        </span>
      </div>
    </header>

    <!-- 主标题 -->
    <section class="hero-title">
      <h1>自助报到机</h1>
      <p>请将二维码对准扫描口</p>
    </section>

    <!-- 三栏主体 -->
    <main class="main-layout">
      <!-- 左侧：报到方式 -->
      <aside class="side-panel side-panel--left">
        <div class="side-panel__body">
          <h2 class="panel-title">
            <span class="panel-title__bar"></span>
            支持以下方式报到
          </h2>
          <ul class="method-list">
            <li v-for="m in checkinMethods" :key="m.title" class="method-item">
              <div class="method-icon" :style="{ background: m.color + '14', color: m.color }">
                <svg v-if="m.icon === 'qr'" viewBox="0 0 24 24" fill="currentColor" width="26" height="26">
                  <path d="M3 3h8v8H3V3zm2 2v4h4V5H5zm8-2h8v8h-8V3zm2 2v4h4V5h-4zM3 13h8v8H3v-8zm2 2v4h4v-4H5zm13-2h3v2h-3v-2zm0 4h3v2h-3v-2zm-5 0h2v5h-2v-5zm5-8h2v3h-2V9zm-5 0h2v2h-2V9z" />
                </svg>
                <svg v-else-if="m.icon === 'id'" viewBox="0 0 24 24" fill="currentColor" width="26" height="26">
                  <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12zM6 10h2v2H6v-2zm0 4h8v2H6v-2zm10 0h2v2h-2v-2zm-4-4h8v2h-8v-2z" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="currentColor" width="26" height="26">
                  <path d="M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z" />
                </svg>
              </div>
              <div class="method-text">
                <div class="method-title">{{ m.title }}</div>
                <div class="method-desc">{{ m.desc }}</div>
              </div>
              <svg class="method-arrow" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
              </svg>
            </li>
          </ul>
        </div>
        <div class="side-panel__fill" aria-hidden="true"></div>
        <div class="side-panel__deco" aria-hidden="true">
          <img src="/images/checkin/hospital-relief.png" alt="" class="relief-img relief-img--hospital" />
        </div>
      </aside>

      <!-- 中间：扫码区 + 解析结果 -->
      <section class="center-core">
        <!-- 扫码主区域 -->
        <div class="scan-hero">
          <!-- 花草叶子浮雕背景 -->
          <div class="scan-hero__botanical" aria-hidden="true">
            <svg viewBox="0 0 400 280" fill="none" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid slice">
              <path d="M-10 200c30-40 60-55 90-40s40 50 20 80" fill="#86efac" opacity="0.12" />
              <path d="M350 180c-25-35-55-45-80-30s-35 45-15 70" fill="#6ee7b7" opacity="0.1" />
              <path d="M60 240c15-25 35-35 55-25s25 30 10 50" fill="#a7f3d0" opacity="0.15" />
              <path d="M320 230c-18-22-40-30-58-20s-22 28-8 45" fill="#bbf7d0" opacity="0.12" />
              <ellipse cx="50" cy="60" rx="35" ry="20" fill="#93c5fd" opacity="0.08" transform="rotate(-20 50 60)" />
              <ellipse cx="340" cy="50" rx="40" ry="22" fill="#7dd3fc" opacity="0.07" transform="rotate(15 340 50)" />
              <path d="M20 120 Q40 90 65 110 T90 130" stroke="#86efac" stroke-width="1.5" opacity="0.2" fill="none" />
              <path d="M380 110 Q360 85 335 105 T310 125" stroke="#6ee7b7" stroke-width="1.5" opacity="0.18" fill="none" />
              <circle cx="100" cy="45" r="3" fill="#86efac" opacity="0.25" />
              <circle cx="300" cy="40" r="2.5" fill="#93c5fd" opacity="0.2" />
              <circle cx="200" cy="250" r="4" fill="#a7f3d0" opacity="0.2" />
            </svg>
          </div>

          <!-- 扫描框：四角 + 扫描线 -->
          <div class="scan-frame" aria-hidden="true">
            <div class="scan-frame__corner scan-frame__corner--tl"></div>
            <div class="scan-frame__corner scan-frame__corner--tr"></div>
            <div class="scan-frame__corner scan-frame__corner--bl"></div>
            <div class="scan-frame__corner scan-frame__corner--br"></div>
            <div class="scan-frame__line"></div>
            <div class="scan-frame__glow"></div>
          </div>

          <div class="scan-pill" @click.stop="focusInput">
            <input
              ref="inputEl"
              v-model="input"
              class="scan-input"
              placeholder="扫描二维码 / 身份证 / 就诊卡"
              autocomplete="off"
              @keyup.enter="handleScanned"
              @click.stop
            />
            <div class="scan-hint">
              <span class="pulse-ring"></span>
              <span>等待扫描…</span>
            </div>
          </div>

          <div class="scan-hero__footer">
            <p class="scan-tip">将就诊二维码/身份证/就诊卡对准扫码口，扫码枪扫入上方输入框后回车</p>
            <div v-if="status !== 'idle'" class="scan-again-hint">
              <span class="pulse-ring pulse-ring--sm"></span>
              <span>已显示报到结果，下一位请直接继续扫码</span>
            </div>
            <div v-else class="device-status">
              <span class="pulse-ring pulse-ring--sm"></span>
              <span>设备正常，请扫描…</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：操作流程 -->
      <aside class="side-panel side-panel--right">
        <div class="side-panel__body">
          <h2 class="panel-title">
            <span class="panel-title__bar"></span>
            操作流程
          </h2>
          <ol class="flow-list">
            <li v-for="(step, idx) in flowSteps" :key="step.title" class="flow-item">
              <div class="flow-step-wrap">
                <div class="flow-icon" :class="`flow-icon--${step.icon}`">
                  <svg v-if="step.icon === 'scan'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                    <path d="M9.5 6.5v3h-3v-3h3M11 5H5v6h6V5zm-1.5 9.5v3h-3v-3h3M11 13H5v6h6v-6zm6.5-6.5v3h-3v-3h3M19 5h-6v6h6V5zm-6 8h1.5v1.5H13V13zm1.5 1.5H16V16h-1.5v-1.5zM16 13h1.5v1.5H16V13zm-3 3h1.5v1.5H13V16zm1.5 1.5H16V19h-1.5v-1.5zM16 16h1.5v1.5H16V16zm1.5-3H19v1.5h-1.5V13zm-1.5 3H19V19h-1.5v-1.5zM22 7h-2V4h-3V2h5v5zm0 15v-5h-2v3h-3v2h5zM2 22h5v-2H4v-3H2v5zM2 2v5h2V4h3V2H2z" />
                  </svg>
                  <svg v-else-if="step.icon === 'verify'" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                    <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                    <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
                  </svg>
                </div>
                <div v-if="idx < flowSteps.length - 1" class="flow-line"></div>
              </div>
              <div class="flow-text">
                <div class="flow-title">{{ step.title }}</div>
                <div class="flow-desc">{{ step.desc }}</div>
              </div>
            </li>
          </ol>
        </div>
        <div class="side-panel__fill" aria-hidden="true"></div>
        <div class="side-panel__deco" aria-hidden="true">
          <img src="/images/checkin/clipboard-relief.png" alt="" class="relief-img relief-img--clipboard" />
        </div>
      </aside>

      <!-- 报到结果：弹出层（可关闭） -->
      <!-- calling 态不显示关闭按钮，避免用户在请求中关掉，导致状态悬挂；success/fail 都可 × -->
      <!-- success 态下显示"叫号实时推送"等内容，用户决定何时关 -->
    </main>

    <!-- 报到结果弹层 -->
    <Teleport to="body">
      <div v-if="status !== 'idle'" class="result-overlay" @click.self="closeResultIfNotCalling">
        <div class="result-modal" role="dialog" aria-modal="true">
          <button
            v-if="status !== 'calling'"
            type="button"
            class="result-modal__close"
            aria-label="关闭"
            @click.stop="resetToIdle"
          >×</button>

          <!-- calling -->
          <div v-if="status === 'calling'" class="result-card result-card--calling">
            <div class="spinner"></div>
            <p>正在处理，请稍候…</p>
          </div>

          <!-- success -->
          <div v-if="status === 'success' && successResult" class="result-card result-card--success">
            <div class="success-icon">✓</div>
            <h2>{{ successResult.alreadyCheckedIn ? '您已报到' : '报到成功' }}</h2>
            <p class="patient-name">{{ successResult.patientName }} 您好</p>

            <div class="info-grid">
              <div class="info-cell">
                <div class="label">就诊科室</div>
                <div class="value">{{ successResult.departmentName || '—' }}</div>
              </div>
              <div class="info-cell">
                <div class="label">接诊医生</div>
                <div class="value">{{ successResult.doctorName || '—' }}</div>
              </div>
              <div class="info-cell">
                <div class="label">就诊日期</div>
                <div class="value">{{ successResult.visitDate || '—' }} {{ successResult.noon || '' }}</div>
              </div>
              <div class="info-cell">
                <div class="label">挂号级别</div>
                <div class="value">{{ successResult.registLevelName || '—' }}</div>
              </div>
            </div>

            <div class="queue-box">
              <div class="queue-item">
                <span class="num">{{ successResult.queueNumber }}</span>
                <span class="cap">您的号序</span>
              </div>
              <div class="queue-divider"></div>
              <div class="queue-item">
                <span class="num">{{ successResult.waitingAhead }}</span>
                <span class="cap">前方等待</span>
              </div>
              <div class="queue-divider"></div>
              <div class="queue-item">
                <span class="num">{{ formatClock(successResult.checkInTime) }}</span>
                <span class="cap">报到时间</span>
              </div>
            </div>

            <p class="notice">请到候诊区等待叫号</p>

            <div v-if="currentCalling" class="calling-now" :class="{ 'calling-me': callingMe }">
              <div class="calling-now__label">现在叫号</div>
              <div class="calling-now__num">{{ currentCalling.queueNumber ?? '—' }} 号</div>
              <div class="calling-now__name">{{ currentCalling.patientName || '—' }}</div>
              <div v-if="callingMe" class="calling-now__me">★ 请您就诊 ★</div>
            </div>
            <div v-else class="calling-now calling-now--idle">
              <div class="calling-now__label">当前叫号</div>
              <div class="calling-now__idle-text">等待医生叫号中…</div>
            </div>

            <div class="voice-bar">
              语音提示：
              <button v-if="!voiceReady" class="voice-btn" @click.stop="enableVoice">点击开启</button>
              <span v-else class="voice-on">已开启</span>
            </div>
          </div>

          <!-- fail -->
          <div v-if="status === 'fail'" class="result-card result-card--fail">
            <div class="fail-icon">!</div>
            <h2>无法报到</h2>
            <p class="error-text">{{ errorMessage }}</p>
            <p class="error-hint">如有疑问，请到人工窗口咨询</p>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 底部 -->
    <footer class="page-footer">
      <div class="tip-bar">
        <svg class="tip-icon" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z" />
        </svg>
        <span>温馨提示：如有问题请前往人工窗口或咨询工作人员</span>
      </div>
      <button type="button" class="history-btn" @click.stop="showHistory = true">
        <div class="history-btn__icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
            <path d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z" />
          </svg>
        </div>
        <div class="history-btn__text">
          <span class="history-btn__title">历史记录</span>
          <span class="history-btn__desc">查询您的报到记录</span>
        </div>
        <svg class="history-btn__arrow" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
        </svg>
      </button>
    </footer>

    <!-- 历史记录弹层 -->
    <Teleport to="body">
      <div v-if="showHistory" class="history-overlay" @click.self="showHistory = false">
        <div class="history-modal">
          <header class="history-modal__header">
            <h3>报到历史记录</h3>
            <button type="button" class="history-modal__close" @click="showHistory = false">×</button>
          </header>
          <ul v-if="history.length" class="history-list">
            <li v-for="(h, i) in history" :key="i" :class="{ ok: h.ok, fail: !h.ok }">
              <span class="time">{{ h.time }}</span>
              <span class="summary">{{ h.summary }}</span>
              <span class="raw">{{ h.raw }}</span>
            </li>
          </ul>
          <p v-else class="history-empty">暂无报到记录</p>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.checkin-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(160deg, #dbeafe 0%, #eff6ff 35%, #f0f9ff 65%, #e0f2fe 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  padding: 20px 28px 16px;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;

  /* ===== 侧栏高度 / 浮雕：在此自行调整 ===== */
  /* 左右侧栏总高度。auto=随网格行拉伸；填 px 如 520px 可手动与中间齐平 */
  --checkin-side-panel-height: 520px;
  /* 侧栏底部浮雕区高度 */
  --checkin-side-deco-height: 110px;
  /* 浮雕图最大高度（医院 / 记事簿共用，也可分别改下方两个 class） */
  --checkin-relief-max-height: 130px;
}

.checkin-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 60% 40% at 15% 20%, rgba(59, 130, 246, 0.1) 0%, transparent 60%),
    radial-gradient(ellipse 50% 35% at 85% 75%, rgba(59, 130, 246, 0.08) 0%, transparent 55%);
  pointer-events: none;
}

/* 顶部栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  margin-bottom: 8px;
}

.hospital-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hospital-logo {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 8px rgba(37, 99, 235, 0.25));
}

.hospital-logo svg {
  width: 100%;
  height: 100%;
}

.hospital-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hospital-name {
  font-size: 20px;
  font-weight: 800;
  color: #1e3a5f;
  letter-spacing: 0.5px;
}

.hospital-slogan {
  font-size: 12px;
  color: #64748b;
  letter-spacing: 1px;
}

.datetime {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #475569;
}

.datetime__clock {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #1e293b;
}

.datetime__date {
  font-size: 14px;
  color: #64748b;
}

.datetime__moon {
  color: #94a3b8;
  display: flex;
  align-items: center;
}

/* 主标题 */
.hero-title {
  text-align: center;
  position: relative;
  z-index: 1;
  margin-bottom: 20px;
}

.hero-title h1 {
  margin: 0;
  font-size: 38px;
  font-weight: 900;
  background: linear-gradient(135deg, #1d4ed8 0%, #3b82f6 50%, #2563eb 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 6px;
}

.hero-title p {
  margin: 6px 0 0;
  font-size: 15px;
  color: #64748b;
}

/* 三栏布局 */
.main-layout {
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr 280px;
  gap: 14px 20px;
  align-items: stretch;
  position: relative;
  z-index: 1;
  max-width: 1320px;
  width: 100%;
  margin: 0 auto;
}

.center-core {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.side-panel {
  background: rgba(255, 255, 255, 0.94);
  border-radius: 20px;
  padding: 22px 18px 16px;
  box-shadow: 0 4px 24px rgba(37, 99, 235, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: var(--checkin-side-panel-height);
}

.side-panel__body {
  flex: 0 0 auto;
}

.side-panel__fill {
  flex: 1 1 auto;
  min-height: 0;
}

.side-panel__deco {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px 0 0;
  height: var(--checkin-side-deco-height);
  pointer-events: none;
}

.relief-img {
  width: auto;
  height: auto;
  max-width: 100%;
  object-fit: contain;
  opacity: 0.72;
  filter: contrast(1.08) saturate(1.1);
  user-select: none;
}

.relief-img--hospital {
  max-height: var(--checkin-relief-max-height);
}

.relief-img--clipboard {
  max-height: var(--checkin-relief-max-height);
  opacity: 0.68;
}

.panel-title {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title__bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #3b82f6, #2563eb);
  flex-shrink: 0;
}

/* 左侧报到方式 */
.method-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.method-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  cursor: default;
  transition: background 0.15s, box-shadow 0.15s;
}

.method-item:hover {
  background: #f0f7ff;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.08);
}

.method-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.method-text {
  flex: 1;
  min-width: 0;
}

.method-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}

.method-desc {
  font-size: 11px;
  color: #94a3b8;
}

.method-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
}

/* 淡蓝扫码主区域 + 花草浮雕 */
.scan-hero {
  background:
    linear-gradient(155deg, #e0f2fe 0%, #dbeafe 35%, #bfdbfe 70%, #e0f2fe 100%);
  border-radius: 22px;
  padding: 32px 24px 0;
  box-shadow:
    0 8px 32px rgba(59, 130, 246, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
  position: relative;
  overflow: hidden;
}

.scan-hero__botanical {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.scan-hero__botanical svg {
  width: 100%;
  height: 100%;
}

/* 扫描框：四角括号 + 动画扫描线 */
.scan-frame {
  width: 130px;
  height: 130px;
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.scan-frame__corner {
  position: absolute;
  width: 28px;
  height: 28px;
  border-color: #3b82f6;
  border-style: solid;
  opacity: 0.85;
}

.scan-frame__corner--tl { top: 0; left: 0; border-width: 3px 0 0 3px; border-radius: 4px 0 0 0; }
.scan-frame__corner--tr { top: 0; right: 0; border-width: 3px 3px 0 0; border-radius: 0 4px 0 0; }
.scan-frame__corner--bl { bottom: 0; left: 0; border-width: 0 0 3px 3px; border-radius: 0 0 0 4px; }
.scan-frame__corner--br { bottom: 0; right: 0; border-width: 0 3px 3px 0; border-radius: 0 0 4px 0; }

.scan-frame__line {
  position: absolute;
  left: 8px;
  right: 8px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #3b82f6, #60a5fa, #3b82f6, transparent);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.6);
  animation: scan-sweep 2.4s ease-in-out infinite;
}

.scan-frame__glow {
  position: absolute;
  inset: 12px;
  border: 1px dashed rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  pointer-events: none;
}

@keyframes scan-sweep {
  0%, 100% { top: 12px; opacity: 0.6; }
  50%      { top: calc(100% - 14px); opacity: 1; }
}

/* 白色输入胶囊 */
.scan-pill {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  padding: 18px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  box-shadow:
    0 4px 24px rgba(59, 130, 246, 0.1),
    0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.9);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(8px);
}

.scan-input {
  width: 100%;
  padding: 6px 4px;
  font-size: 17px;
  font-weight: 700;
  font-family: inherit;
  border: none;
  outline: none;
  text-align: center;
  background: transparent;
  color: #1e40af;
  letter-spacing: 0.3px;
  caret-color: transparent;
}

.scan-input::placeholder {
  color: #3b82f6;
  font-weight: 700;
  font-size: 17px;
  opacity: 0.85;
}

.scan-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.pulse-ring {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse 1.6s ease-in-out infinite;
  flex-shrink: 0;
}

.pulse-ring--sm {
  width: 7px;
  height: 7px;
}

/* 底部说明区 */
.scan-hero__footer {
  width: 100%;
  background: rgba(255, 255, 255, 0.45);
  border-radius: 0 0 22px 22px;
  padding: 16px 24px 18px;
  margin-top: 4px;
  position: relative;
  z-index: 1;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.5);
}

.scan-tip {
  margin: 0 0 10px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.65;
  text-align: center;
}

.device-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #334155;
  font-weight: 600;
}

/* 报到结果出现后，扫码区的"继续扫码"提示 */
.scan-again-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #16a34a;
  font-weight: 600;
}

.scan-again-hint .pulse-ring {
  background: #16a34a;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; transform: scale(0.9); }
  50%      { opacity: 1;   transform: scale(1.15); }
}

/* 右侧操作流程 */
.flow-list {
  list-style: none;
  padding: 0;
  margin: 0;
  position: relative;
  z-index: 1;
}

.flow-item {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}

.flow-step-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.flow-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.1);
}

.flow-icon--scan { background: linear-gradient(135deg, #60a5fa, #3b82f6); }
.flow-icon--verify { background: linear-gradient(135deg, #a78bfa, #7c3aed); }
.flow-icon--done { background: linear-gradient(135deg, #4ade80, #16a34a); }

.flow-line {
  width: 2px;
  flex: 1;
  min-height: 24px;
  margin: 5px 0;
  background: repeating-linear-gradient(180deg, #cbd5e1 0, #cbd5e1 3px, transparent 3px, transparent 7px);
}

.flow-text {
  padding-top: 6px;
  padding-bottom: 16px;
}

.flow-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 3px;
  line-height: 1.4;
}

.flow-desc {
  font-size: 11px;
  color: #94a3b8;
}

/* 报到结果弹层 */
.result-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  backdrop-filter: blur(4px);
  padding: 24px;
}

.result-modal {
  position: relative;
  width: min(560px, 95vw);
  max-height: 92vh;
  overflow-y: auto;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  animation: result-pop 0.22s ease-out;
}

@keyframes result-pop {
  from { opacity: 0; transform: translateY(12px) scale(0.96); }
  to   { opacity: 1; transform: translateY(0)    scale(1);    }
}

.result-modal__close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 36px;
  height: 36px;
  border: none;
  background: #f3f4f6;
  border-radius: 50%;
  font-size: 22px;
  line-height: 1;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s, transform 0.1s;
  z-index: 1;
}

.result-modal__close:hover {
  background: #e5e7eb;
  color: #1f2937;
}

.result-modal__close:active {
  transform: scale(0.92);
}

/* 结果卡片 */
.result-card {
  background: #fff;
  border-radius: 24px;
  padding: 32px 28px;
  box-shadow: 0 8px 40px rgba(59, 130, 246, 0.12);
  text-align: center;
}

.result-card--calling .spinner {
  width: 48px;
  height: 48px;
  margin: 40px auto 16px;
  border: 4px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.result-card--calling p {
  font-size: 18px;
  color: #6b7280;
  margin-bottom: 40px;
}

.success-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #22c55e;
  color: #fff;
  font-size: 40px;
  font-weight: bold;
  line-height: 72px;
}

.result-card--success h2 {
  margin: 0 0 4px;
  font-size: 28px;
  color: #22c55e;
}

.patient-name {
  margin: 0 0 20px;
  font-size: 18px;
  color: #374151;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
  text-align: left;
}

.info-cell {
  background: #f8fafc;
  padding: 12px;
  border-radius: 10px;
}

.info-cell .label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.info-cell .value {
  font-size: 16px;
  color: #1f2937;
  font-weight: 500;
}

.queue-box {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: linear-gradient(135deg, #eff6ff 0%, #f0fdf4 100%);
  border-radius: 14px;
  padding: 20px;
  margin: 16px 0;
}

.queue-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.queue-item .num {
  font-size: 36px;
  font-weight: bold;
  color: #2563eb;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.queue-item .cap {
  font-size: 12px;
  color: #6b7280;
}

.queue-divider {
  width: 1px;
  height: 36px;
  background: #e5e7eb;
}

.notice {
  margin: 0 0 16px;
  font-size: 16px;
  color: #f59e0b;
  font-weight: 500;
}

.fail-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  font-size: 48px;
  font-weight: bold;
  line-height: 72px;
}

.result-card--fail h2 {
  margin: 0 0 12px;
  font-size: 28px;
  color: #ef4444;
}

.error-text {
  margin: 0 0 8px;
  font-size: 18px;
  color: #374151;
  word-break: break-all;
}

.error-hint {
  margin: 0 0 20px;
  font-size: 14px;
  color: #9ca3af;
}

.action-btn {
  display: inline-block;
  padding: 12px 40px;
  font-size: 16px;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 24px;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.1s;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.3);
}

.action-btn:hover {
  opacity: 0.92;
}

.action-btn:active {
  transform: scale(0.98);
}

/* 叫号显示 */
.calling-now {
  margin: 16px 0 8px;
  padding: 16px;
  border-radius: 12px;
  background: #eff6ff;
  border: 2px solid #3b82f6;
  text-align: center;
}

.calling-now--idle {
  background: #f9fafb;
  border-color: #e5e7eb;
}

.calling-now__label {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 4px;
}

.calling-now__num {
  font-size: 40px;
  font-weight: 900;
  color: #2563eb;
  line-height: 1.2;
}

.calling-now__name {
  font-size: 18px;
  color: #374151;
  margin-top: 4px;
}

.calling-now__idle-text {
  font-size: 16px;
  color: #9ca3af;
  padding: 8px 0;
}

.calling-now.calling-me {
  background: #fef2f2;
  border-color: #ef4444;
  animation: callingMeFlash 1s ease-in-out 3;
}

.calling-now.calling-me .calling-now__num,
.calling-now.calling-me .calling-now__name {
  color: #ef4444;
}

.calling-now__me {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 900;
  color: #ef4444;
  letter-spacing: 4px;
}

@keyframes callingMeFlash {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.03); }
}

.voice-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: #9ca3af;
  margin: 8px 0 12px;
}

.voice-btn {
  padding: 4px 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.voice-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.voice-on {
  color: #22c55e;
  font-weight: 600;
}

/* 底部 */
.page-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-top: 24px;
  position: relative;
  z-index: 1;
}

.tip-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 24px;
  font-size: 14px;
  color: #6b7280;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  flex: 1;
  max-width: 680px;
}

.tip-icon {
  color: #3b82f6;
  flex-shrink: 0;
}

.history-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: none;
  border-radius: 14px;
  padding: 14px 18px;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.15s, transform 0.1s;
  flex-shrink: 0;
}

.history-btn:hover {
  box-shadow: 0 4px 20px rgba(59, 130, 246, 0.15);
}

.history-btn:active {
  transform: scale(0.98);
}

.history-btn__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #eff6ff;
  color: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-btn__text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}

.history-btn__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.history-btn__desc {
  font-size: 12px;
  color: #9ca3af;
}

.history-btn__arrow {
  color: #9ca3af;
}

/* 历史弹层 */
.history-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.history-modal {
  background: #fff;
  border-radius: 20px;
  width: min(520px, 90vw);
  max-height: 70vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.history-modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.history-modal__header h3 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
}

.history-modal__close {
  width: 32px;
  height: 32px;
  border: none;
  background: #f3f4f6;
  border-radius: 8px;
  font-size: 20px;
  color: #6b7280;
  cursor: pointer;
  line-height: 1;
}

.history-modal__close:hover {
  background: #e5e7eb;
}

.history-list {
  list-style: none;
  padding: 12px 24px 20px;
  margin: 0;
  overflow-y: auto;
}

.history-list li {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 4px 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
  font-size: 13px;
}

.history-list li.ok .summary { color: #22c55e; font-weight: 500; }
.history-list li.fail .summary { color: #ef4444; font-weight: 500; }

.history-list .time {
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
  grid-row: span 2;
}

.history-list .raw {
  grid-column: 2;
  font-size: 11px;
  color: #9ca3af;
  word-break: break-all;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.history-empty {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  margin: 0;
}

/* 响应式 */
@media (max-width: 960px) {
  .main-layout {
    grid-template-columns: 1fr;
  }

  .side-panel--left,
  .side-panel--right {
    display: none;
  }

  .hero-title h1 {
    font-size: 32px;
  }

  .page-footer {
    flex-direction: column;
  }

  .tip-bar {
    max-width: 100%;
  }
}
</style>
