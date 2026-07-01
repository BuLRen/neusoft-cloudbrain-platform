<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { parseQrPayload } from '@/shared/utils/qrProtocol'
import { http } from '@/shared/api/request'
import type { CheckInResult } from '@/shared/types/registration'

type Status = 'idle' | 'calling' | 'success' | 'fail'

const status = ref<Status>('idle')
const input = ref('')
const inputEl = ref<HTMLInputElement | null>(null)

// 成功结果（报到卡片渲染用）
const successResult = ref<CheckInResult | null>(null)
// 失败结果（红色卡片渲染用）
const errorMessage = ref('')

// 扫描历史（报到机本地留痕，方便联调时对照）
interface HistoryItem {
  time: string
  raw: string
  ok: boolean
  summary: string
}
const history = ref<HistoryItem[]>([])

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

  // 第 1 关：本地协议解析
  const parsed = parseQrPayload(raw)
  if (!parsed.ok) {
    status.value = 'fail'
    errorMessage.value = parsed.message
    pushHistory(raw, false, parsed.message)
    return
  }

  // 解析成功，但只有 REG 类型可以报到
  if (parsed.type !== 'REG') {
    status.value = 'fail'
    errorMessage.value = `暂不支持的业务类型：${parsed.type}`
    pushHistory(raw, false, '不支持的类型')
    return
  }

  // 第 2 关：调后端 /check-in
  // 报到机页面自己控制错误展示，不让 ElMessage 默认弹窗
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
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '请求失败'
    status.value = 'fail'
    pushHistory(raw, false, errorMessage.value)
  }
}

/** 点击"继续扫码"按钮：清空状态，回到扫码态 */
function resetToIdle() {
  status.value = 'idle'
  successResult.value = null
  errorMessage.value = ''
  void nextTick(() => inputEl.value?.focus())
}

function focusInput() {
  inputEl.value?.focus()
}

onMounted(focusInput)

/** 把 "2026-07-01T09:30:00.123" 美化成 "09:30" */
function formatClock(iso: string): string {
  if (!iso) return '--:--'
  const m = iso.match(/T(\d{2}:\d{2})/)
  return m ? m[1] : iso
}
</script>

<template>
  <div class="checkin-page" @click="focusInput">
    <!-- 顶部标题 -->
    <header class="page-header">
      <h1>熙康云医院 · 自助报到机</h1>
      <p class="subtitle">请将二维码对准扫描口</p>
    </header>

    <!-- 扫码输入框：永远在但视觉隐藏（除 idle 态），靠 focus 抓扫码枪键盘事件 -->
    <div class="scan-zone" :class="{ hidden: status !== 'idle' }">
      <input
        ref="inputEl"
        v-model="input"
        class="scan-input"
        placeholder="扫到这里…"
        autocomplete="off"
        @keyup.enter="handleScanned"
        @click.stop
      />
      <div class="scan-hint">
        <div class="pulse-ring"></div>
        <span>等待扫码…</span>
      </div>
    </div>

    <!-- 主显示区：根据 status 切换 -->
    <main class="display-area">
      <!-- 处理中 -->
      <div v-if="status === 'calling'" class="card calling">
        <div class="spinner"></div>
        <p>正在处理，请稍候…</p>
      </div>

      <!-- 报到成功（按 B：必须手动按钮才清） -->
      <div v-else-if="status === 'success' && successResult" class="card success">
        <div class="success-icon">✓</div>
        <h2>{{ successResult.alreadyCheckedIn ? '您已报到' : '报到成功' }}</h2>
        <p class="patient-name">{{ successResult.patientName }} 您好</p>

        <div class="info-row">
          <div class="info-cell">
            <div class="label">就诊科室</div>
            <div class="value">{{ successResult.departmentName || '—' }}</div>
          </div>
          <div class="info-cell">
            <div class="label">接诊医生</div>
            <div class="value">{{ successResult.doctorName || '—' }}</div>
          </div>
        </div>
        <div class="info-row">
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

        <button class="action-btn" @click.stop="resetToIdle">继续扫码</button>
      </div>

      <!-- 报到失败（按 B：必须手动按钮才清） -->
      <div v-else-if="status === 'fail'" class="card fail">
        <div class="fail-icon">!</div>
        <h2>无法报到</h2>
        <p class="error-text">{{ errorMessage }}</p>
        <p class="error-hint">如有疑问，请到人工窗口咨询</p>
        <button class="action-btn" @click.stop="resetToIdle">我知道了</button>
      </div>
    </main>

    <!-- 底部历史（仅联调用，正式报到机可隐藏） -->
    <footer class="history-footer">
      <h3>扫描历史（联调用，最多 10 条）</h3>
      <ul v-if="history.length" class="history-list">
        <li v-for="(h, i) in history" :key="i" :class="{ ok: h.ok, fail: !h.ok }">
          <span class="time">{{ h.time }}</span>
          <span class="raw">{{ h.raw }}</span>
          <span class="summary">{{ h.summary }}</span>
        </li>
      </ul>
      <p v-else class="empty">暂无记录</p>
    </footer>
  </div>
</template>

<style scoped>
.checkin-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(180deg, #eef5ff 0%, #f5f7fa 100%);
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  padding: 20px;
  box-sizing: border-box;
}

.page-header {
  text-align: center;
  margin-bottom: 20px;
}
.page-header h1 {
  margin: 0;
  font-size: 28px;
  color: #1f2d3d;
  letter-spacing: 2px;
}
.subtitle {
  margin: 6px 0 0;
  font-size: 16px;
  color: #606266;
}

/* 扫码区 */
.scan-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  transition: opacity 0.2s;
}
.scan-zone.hidden {
  opacity: 0;
  pointer-events: none;
  height: 0;
  overflow: hidden;
  margin: 0;
}
.scan-input {
  width: 320px;
  padding: 10px 14px;
  font-size: 16px;
  border: 2px dashed #c0c4cc;
  border-radius: 8px;
  outline: none;
  text-align: center;
  background: #fff;
}
.scan-input:focus {
  border-color: #409eff;
  border-style: solid;
}
.scan-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #606266;
  font-size: 14px;
}
.pulse-ring {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #67c23a;
  animation: pulse 1.6s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.9); }
  50%      { opacity: 1;   transform: scale(1.2); }
}

/* 主显示区 */
.display-area {
  flex: 1;
  width: 100%;
  max-width: 640px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 卡片通用 */
.card {
  width: 100%;
  background: #fff;
  border-radius: 16px;
  padding: 32px 36px;
  box-shadow: 0 8px 32px rgba(15, 30, 60, 0.1);
  text-align: center;
}

/* 处理中 */
.calling .spinner {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border: 4px solid #e0e0e0;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.calling p {
  font-size: 18px;
  color: #606266;
}

/* 成功 */
.success-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #67c23a;
  color: #fff;
  font-size: 40px;
  font-weight: bold;
  line-height: 72px;
}
.success h2 {
  margin: 0 0 4px;
  font-size: 28px;
  color: #67c23a;
}
.patient-name {
  margin: 0 0 20px;
  font-size: 18px;
  color: #303133;
}
.info-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.info-cell {
  background: #f7fafd;
  padding: 12px;
  border-radius: 8px;
}
.info-cell .label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.info-cell .value {
  font-size: 18px;
  color: #1f2d3d;
  font-weight: 500;
}
.queue-box {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9eb 100%);
  border-radius: 12px;
  padding: 20px;
  margin: 20px 0;
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
  color: #409eff;
  font-family: 'SFMono-Regular', Consolas, monospace;
  line-height: 1;
}
.queue-item .cap {
  font-size: 12px;
  color: #606266;
}
.queue-divider {
  width: 1px;
  height: 36px;
  background: #dcdfe6;
}
.notice {
  margin: 0 0 16px;
  font-size: 16px;
  color: #e6a23c;
  font-weight: 500;
}

/* 失败 */
.fail-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: #f56c6c;
  color: #fff;
  font-size: 48px;
  font-weight: bold;
  line-height: 72px;
}
.fail h2 {
  margin: 0 0 12px;
  font-size: 28px;
  color: #f56c6c;
}
.error-text {
  margin: 0 0 8px;
  font-size: 18px;
  color: #303133;
  word-break: break-all;
}
.error-hint {
  margin: 0 0 20px;
  font-size: 14px;
  color: #909399;
}

/* 主按钮 */
.action-btn {
  display: inline-block;
  padding: 12px 36px;
  font-size: 16px;
  color: #fff;
  background: #409eff;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  transition: background 0.15s;
}
.action-btn:hover {
  background: #66b1ff;
}
.action-btn:active {
  background: #3a8ee6;
}

/* 历史 */
.history-footer {
  width: 100%;
  max-width: 640px;
  margin-top: 20px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  padding: 12px 16px;
}
.history-footer h3 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
  font-weight: normal;
}
.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.history-list li {
  display: grid;
  grid-template-columns: 70px 1fr 1fr;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.history-list li.ok { color: #67c23a; }
.history-list li.fail { color: #f56c6c; }
.history-list .time {
  color: #909399;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
.history-list .raw {
  font-family: 'SFMono-Regular', Consolas, monospace;
  color: #303133;
  word-break: break-all;
}
.empty {
  color: #909399;
  text-align: center;
  padding: 8px;
}
</style>
