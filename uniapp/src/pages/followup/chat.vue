<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { medicalApi, type VisitSummary } from '../../api/medical'
import { followupApi, type CommunicationSenderType, type FollowUpCommunicationMessage, type FollowUpCommunicationSession } from '../../api/followup'
import { currentPatient } from '../../stores/session'

const loading = ref(false)
const sending = ref(false)
const visits = ref<VisitSummary[]>([])
const registerId = ref<number | undefined>()
const session = ref<FollowUpCommunicationSession | null>(null)
const messages = ref<FollowUpCommunicationMessage[]>([])
const draft = ref('')
const scrollTop = ref(0)

const patientId = computed(() => currentPatient.value?.patientId)

const selectedVisit = computed(() => visits.value.find(item => item.registerId === registerId.value))

const visitOptions = computed(() =>
  visits.value.map(item => ({
    value: item.registerId,
    label: `${item.departmentName ?? '就诊记录'} · ${formatDate(item.visitDate) || `挂号 ${item.registerId}`}`,
  })),
)

const visitPickerRange = computed(() => visitOptions.value.map(item => item.label))
const visitPickerIndex = computed(() => {
  const idx = visitOptions.value.findIndex(item => item.value === registerId.value)
  return idx >= 0 ? idx : 0
})

const visitTitle = computed(() => {
  const visit = selectedVisit.value
  if (!visit) return loading.value ? '正在加载就诊记录' : '暂无就诊记录'
  return `${visit.departmentName ?? '就诊记录'} · ${formatDate(visit.visitDate) || `挂号 ${visit.registerId}`}`
})

const visitDesc = computed(() => {
  const visit = selectedVisit.value
  if (!visit) return visits.value.length ? '请选择一次就诊后查看沟通记录。' : '完成挂号或就诊后，可在这里与医生沟通。'
  const doctor = visit.physicianName ? `${visit.physicianName}医生` : '医生'
  return `随访沟通会话已建立，${doctor}将在此与您跟进康复情况。`
})

function formatDate(time?: string): string {
  if (!time) return ''
  return String(time).slice(0, 10)
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time.replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return ''
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

function senderLabel(senderType: CommunicationSenderType): string {
  if (senderType === 'patient') return '我'
  if (senderType === 'doctor') return selectedVisit.value?.physicianName ? `${selectedVisit.value.physicianName}医生` : '医生'
  if (senderType === 'ai') return 'AI 助手'
  return '系统'
}

function avatarText(senderType: CommunicationSenderType): string {
  if (senderType === 'patient') return '我'
  if (senderType === 'doctor') return '医'
  if (senderType === 'ai') return 'AI'
  return '讯'
}

function scrollToBottom() {
  nextTick(() => {
    scrollTop.value = scrollTop.value === 999999 ? 1000000 : 999999
  })
}

async function loadVisits() {
  if (!patientId.value) return
  loading.value = true
  try {
    visits.value = await medicalApi.visits(patientId.value)
    if (!registerId.value && visits.value.length) {
      registerId.value = visits.value[0]!.registerId
    }
  } finally {
    loading.value = false
  }
}

async function loadConversation() {
  if (!registerId.value || !patientId.value) return
  loading.value = true
  try {
    const sessionRes = await followupApi.getCommunicationSession(registerId.value, patientId.value)
    session.value = sessionRes
    const page = await followupApi.listMessages(registerId.value, patientId.value, 100)
    messages.value = page.items ?? []
    scrollToBottom()
    followupApi.markRead(registerId.value, patientId.value).catch(() => {})
  } catch {
    session.value = null
    messages.value = []
  } finally {
    loading.value = false
  }
}

function onVisitPick(event: { detail: { value: number } }) {
  const idx = Number(event.detail.value)
  const picked = visitOptions.value[idx]
  if (picked) registerId.value = picked.value
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!text || !session.value || sending.value) return
  sending.value = true
  const now = new Date()
  const patientMsg: FollowUpCommunicationMessage = {
    id: -Date.now(),
    sessionId: session.value.id,
    senderType: 'patient',
    messageType: 'text',
    content: text,
    creationTime: now.toISOString(),
  }
  messages.value.push(patientMsg)
  draft.value = ''
  scrollToBottom()
  try {
    const aiMsg = await followupApi.sendPatientMessage(session.value.id, text, true)
    if (aiMsg) {
      messages.value.push(aiMsg)
      scrollToBottom()
    }
  } catch {
    // request 层已提示；保留患者消息，避免用户输入丢失
  } finally {
    sending.value = false
  }
}

watch(registerId, () => {
  if (registerId.value) void loadConversation()
})

// ===================== 5 秒轮询：感知医生/AI 新消息 =====================
const POLL_INTERVAL = 5000
let pollTimer: ReturnType<typeof setInterval> | null = null

// 本地最大的「真实」消息 id（患者乐观消息用负 id，需排除）
function lastRealMessageId(): number {
  let max = 0
  for (const m of messages.value) {
    if (m.id > max) max = m.id
  }
  return max
}

async function pollNewMessages() {
  // 没有会话、正在加载、或正在发送时跳过，避免与主流程冲突
  if (!registerId.value || !patientId.value || !session.value || loading.value) return
  try {
    const page = await followupApi.listMessages(registerId.value, patientId.value, 100)
    const remote = page.items ?? []
    const localMax = lastRealMessageId()
    // 只 append id 大于本地最大真实 id 的消息（即医生/AI 新发的）
    const fresh = remote.filter(m => m.id > localMax)
    if (fresh.length) {
      messages.value.push(...fresh)
      scrollToBottom()
      // 有新消息时静默标记已读
      followupApi.markRead(registerId.value, patientId.value!).catch(() => {})
    }
  } catch {
    // 轮询失败静默，不打扰用户
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => { void pollNewMessages() }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onLoad(() => {
  void loadVisits().then(() => {
    if (registerId.value) void loadConversation()
  })
})

onShow(() => { startPolling() })
onHide(() => { stopPolling() })
onUnload(() => { stopPolling() })
</script>

<template>
  <view class="page-shell chat-page">
    <view class="bg-orb bg-orb--one" />
    <view class="bg-orb bg-orb--two" />

    <PageHeader title="医患沟通" subtitle="与医生或 AI 助手交流随访与用药问题" />

    <picker
      v-if="visitOptions.length"
      mode="selector"
      :range="visitPickerRange"
      :value="visitPickerIndex"
      @change="onVisitPick"
    >
      <view class="visit-card">
        <view class="visit-illustration">
          <image class="visit-icon" src="/static/records/visit.svg" mode="aspectFit" />
        </view>
        <view class="visit-main">
          <view class="visit-title-line">
            <text class="visit-prefix">就诊记录</text>
            <text class="visit-title">{{ visitTitle }}</text>
          </view>
          <text class="visit-desc">{{ visitDesc }}</text>
        </view>
        <view class="visit-arrow">⌄</view>
      </view>
    </picker>

    <view v-else class="visit-card">
      <view class="visit-illustration">
        <image class="visit-icon" src="/static/records/visit.svg" mode="aspectFit" />
      </view>
      <view class="visit-main">
        <view class="visit-title-line">
          <text class="visit-prefix">就诊记录</text>
          <text class="visit-title">{{ visitTitle }}</text>
        </view>
        <text class="visit-desc">{{ visitDesc }}</text>
      </view>
    </view>

    <view class="chat-panel">
      <view v-if="loading && !messages.length" class="state-card">
        <view class="state-pulse" />
        <text>正在加载沟通记录…</text>
      </view>

      <view v-else-if="!session" class="state-card">
        <view class="state-icon">!</view>
        <text>{{ visits.length ? '该就诊暂未建立沟通会话' : '暂无可用就诊记录' }}</text>
      </view>

      <scroll-view
        v-else
        class="msg-scroll"
        scroll-y
        :scroll-top="scrollTop"
        :scroll-with-animation="true"
      >
        <view v-if="!messages.length" class="empty-chat">
          <view class="empty-icon">医</view>
          <text class="empty-title">暂无沟通记录</text>
          <text class="empty-desc">可以在下方输入随访、症状或用药相关问题。</text>
        </view>

        <view
          v-for="msg in messages"
          :key="msg.id"
          :class="['msg-row', msg.senderType === 'patient' ? 'msg-row--patient' : `msg-row--${msg.senderType}`]"
        >
          <template v-if="msg.senderType === 'system'">
            <view class="msg-system">{{ msg.content }}</view>
          </template>

          <template v-else-if="msg.senderType === 'patient'">
            <view class="msg-self">
              <view class="msg-bubble msg-bubble--self">
                <text class="msg-content">{{ msg.content }}</text>
              </view>
              <view class="msg-meta msg-meta--self">
                <text>{{ formatTime(msg.creationTime) }}</text>
                <text class="read-check">✓✓</text>
              </view>
            </view>
          </template>

          <template v-else>
            <view :class="['avatar', msg.senderType === 'ai' ? 'avatar--ai' : 'avatar--doctor']">
              {{ avatarText(msg.senderType) }}
            </view>
            <view class="msg-other">
              <view class="msg-meta">
                <text class="sender-name">{{ senderLabel(msg.senderType) }}</text>
                <text>{{ formatTime(msg.creationTime) }}</text>
              </view>
              <view class="msg-bubble msg-bubble--other">
                <text class="msg-content">{{ msg.content }}</text>
              </view>
              <view v-if="msg.senderType === 'doctor'" class="doctor-like">♡</view>
            </view>
          </template>
        </view>
      </scroll-view>

      <view class="composer">
        <view class="composer-plus">+</view>
        <textarea
          v-model="draft"
          class="composer-input"
          placeholder="描述症状、用药疑问等随访相关问题…"
          :auto-height="true"
          :maxlength="500"
          :show-confirm-bar="false"
          :adjust-position="false"
          confirm-type="send"
          @confirm="sendMessage"
        />
        <button
          class="composer-send"
          :disabled="!draft.trim() || sending || !session"
          :loading="sending"
          @tap="sendMessage"
        >
          发送
        </button>
      </view>

      <view class="safe-tip">
        <text class="safe-shield">♡</text>
        <text>内容仅供参考，不能替代医生诊断，如有不适请及时就医</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.chat-page {
  position: relative;
  height: 100vh;
  min-height: 100vh;
  box-sizing: border-box;
  padding: calc(var(--status-bar-height) + 34rpx) 32rpx calc(20rpx + env(safe-area-inset-bottom));
  overflow: hidden;
  color: #0f2d63;
  background:
    radial-gradient(circle at 78% 2%, rgba(145, 198, 255, 0.56) 0, rgba(145, 198, 255, 0) 258rpx),
    radial-gradient(circle at 46% 0%, rgba(255, 255, 255, 0.95) 0, rgba(255, 255, 255, 0) 310rpx),
    linear-gradient(180deg, #eaf5ff 0%, #f8fbff 45%, #f4f9ff 100%);
}

.bg-orb {
  position: absolute;
  z-index: 0;
  border-radius: 999rpx;
  pointer-events: none;
}

.bg-orb--one {
  top: 118rpx;
  right: 48rpx;
  width: 210rpx;
  height: 210rpx;
  background: rgba(96, 168, 255, 0.12);
  filter: blur(2rpx);
}

.bg-orb--two {
  right: -150rpx;
  bottom: -120rpx;
  width: 430rpx;
  height: 430rpx;
  background: rgba(220, 235, 255, 0.72);
}

.chat-page :deep(.header),
.visit-card,
.chat-panel {
  position: relative;
  z-index: 1;
}

.chat-page :deep(.header) {
  min-height: 84rpx;
  margin-bottom: 24rpx;
}

.chat-page :deep(.title) {
  color: #102d62;
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: .2rpx;
}

.chat-page :deep(.subtitle) {
  margin-top: 7rpx;
  color: #7a8baa;
  font-size: 21rpx;
}

.chat-page :deep(.back) {
  width: 66rpx;
  height: 66rpx;
  border-radius: 18rpx;
  color: #2d7cff;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 12rpx 26rpx rgba(53, 104, 176, 0.09);
}

.visit-card {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 136rpx;
  box-sizing: border-box;
  margin: 0 0 24rpx;
  padding: 18rpx 22rpx;
  border: 1rpx solid rgba(235, 242, 252, 0.9);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18rpx 38rpx rgba(71, 125, 199, 0.09);
}

.visit-illustration {
  position: relative;
  flex: none;
  width: 98rpx;
  height: 98rpx;
  margin-right: 22rpx;
  border-radius: 22rpx;
  background: linear-gradient(160deg, #eaf5ff 0%, #d9ecff 100%);
  overflow: hidden;
}

.visit-illustration::before {
  content: '';
  position: absolute;
  left: -28rpx;
  bottom: -20rpx;
  width: 100rpx;
  height: 60rpx;
  border-radius: 100rpx 100rpx 0 0;
  background: rgba(80, 154, 255, 0.16);
}

.visit-icon {
  position: relative;
  z-index: 1;
  width: 98rpx;
  height: 98rpx;
}

.visit-main {
  flex: 1;
  min-width: 0;
}

.visit-title-line {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-bottom: 12rpx;
  font-size: 27rpx;
  line-height: 1.25;
}

.visit-prefix {
  flex: none;
  margin-right: 18rpx;
  color: #0f2d63;
  font-weight: 800;
}

.visit-title {
  min-width: 0;
  color: #0f2d63;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.visit-desc {
  color: #7586a5;
  font-size: 21rpx;
  line-height: 1.45;
}

.visit-arrow {
  flex: none;
  width: 52rpx;
  height: 52rpx;
  margin-left: 14rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2c77ff;
  font-size: 34rpx;
  background: rgba(238, 245, 255, 0.95);
}

.chat-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: calc(100vh - var(--status-bar-height) - 306rpx - env(safe-area-inset-bottom));
  min-height: 0;
  box-sizing: border-box;
  margin: 0;
  padding: 24rpx 22rpx 18rpx;
  border: 1rpx solid rgba(238, 244, 252, 0.92);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 24rpx 58rpx rgba(72, 128, 209, 0.1);
  overflow: hidden;
}

.msg-scroll {
  flex: 1;
  min-height: 0;
  height: 100%;
  box-sizing: border-box;
}

.state-card,
.empty-chat {
  min-height: 360rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8390aa;
  font-size: 24rpx;
  text-align: center;
}

.state-pulse,
.state-icon,
.empty-icon {
  width: 76rpx;
  height: 76rpx;
  margin-bottom: 18rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(145deg, #65cfe0, #2d78ff);
  box-shadow: 0 12rpx 26rpx rgba(43, 120, 255, 0.2);
}

.state-pulse::after {
  content: '';
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: #fff;
}

.empty-title {
  color: #16366f;
  font-size: 30rpx;
  font-weight: 800;
}

.empty-desc {
  margin-top: 10rpx;
  color: #8895ad;
  font-size: 23rpx;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  margin: 20rpx 0 32rpx;
}

.msg-row--patient {
  justify-content: flex-end;
}

.msg-row--system {
  justify-content: center;
  margin: 18rpx 0;
}

.msg-system {
  max-width: 78%;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  color: #8a96ad;
  font-size: 22rpx;
  line-height: 1.45;
  background: rgba(232, 240, 251, 0.84);
}

.avatar {
  flex: none;
  width: 58rpx;
  height: 58rpx;
  margin-right: 14rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20rpx;
  font-weight: 850;
  border: 4rpx solid #fff;
  box-shadow: 0 10rpx 22rpx rgba(43, 120, 255, 0.14);
}

.avatar--doctor {
  background: linear-gradient(145deg, #89c4ff, #2c7bff);
}

.avatar--ai {
  background: linear-gradient(145deg, #5ed4d3, #2d78ff);
}

.msg-other {
  max-width: 76%;
  min-width: 0;
}

.msg-self {
  max-width: 68%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 0 4rpx 8rpx;
  color: #8c98ad;
  font-size: 20rpx;
}

.msg-meta--self {
  justify-content: flex-end;
  margin: 8rpx 4rpx 0;
}

.sender-name {
  color: #3d83ff;
  font-weight: 750;
}

.read-check {
  color: #75a7ff;
  letter-spacing: -4rpx;
}

.msg-bubble {
  box-sizing: border-box;
  max-width: 100%;
  padding: 17rpx 24rpx;
  box-shadow: 0 10rpx 22rpx rgba(67, 116, 188, 0.08);
  word-break: break-word;
}

.msg-bubble--other {
  color: #1f376d;
  border-radius: 0 18rpx 18rpx 18rpx;
  background: #fff;
}

.msg-bubble--self {
  color: #1f57a5;
  border-radius: 22rpx 22rpx 0 22rpx;
  background: #dcecff;
}

.msg-content {
  font-size: 24rpx;
  line-height: 1.62;
}

.doctor-like {
  margin-top: 9rpx;
  color: #9aa8bf;
  font-size: 24rpx;
  line-height: 1;
}

.composer {
  flex: none;
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-height: 100rpx;
  box-sizing: border-box;
  margin-top: 16rpx;
  padding: 16rpx 0 6rpx;
  border-top: 1rpx solid rgba(238, 244, 252, 0.72);
}

.composer-plus {
  flex: none;
  width: 54rpx;
  height: 54rpx;
  border-radius: 15rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(145deg, #87c9ff, #2c73ff);
  box-shadow: 0 12rpx 24rpx rgba(44, 115, 255, 0.22);
}

.composer-input {
  flex: 1;
  min-width: 0;
  min-height: 64rpx;
  max-height: 116rpx;
  box-sizing: border-box;
  padding: 17rpx 20rpx;
  border-radius: 16rpx;
  color: #18356b;
  font-size: 23rpx;
  line-height: 1.42;
  background: rgba(248, 250, 255, 0.92);
}

.composer-send {
  flex: none;
  width: 96rpx;
  height: 68rpx;
  padding: 0;
  border-radius: 17rpx;
  color: #fff;
  font-size: 25rpx;
  font-weight: 800;
  line-height: 68rpx;
  background: linear-gradient(145deg, #73a8ff, #386dff);
  box-shadow: 0 12rpx 24rpx rgba(43, 120, 255, 0.24);
}

.composer-send::after {
  border: none;
}

.composer-send[disabled] {
  color: rgba(255, 255, 255, 0.88);
  background: linear-gradient(145deg, #b9cced, #8ba8d9);
  box-shadow: none;
}

.safe-tip {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  min-height: 38rpx;
  padding-top: 6rpx;
  color: #96a3ba;
  font-size: 18rpx;
  white-space: nowrap;
}

.safe-shield {
  color: #7894c1;
  font-weight: 800;
}
</style>
