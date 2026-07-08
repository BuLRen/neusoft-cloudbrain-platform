<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { subscribeDepartment, type CallingEvent, type CallingSubscription } from '../../api/calling'
import { registrationApi, type Registration } from '../../api/registration'
import { currentPatient } from '../../stores/session'

const loading = ref(false)
const registrations = ref<Registration[]>([])
const current = computed(() =>
  registrations.value.find(item =>
    item.patientId === currentPatient.value?.patientId &&
    item.status !== 3 &&
    item.status !== 4,
  ) || null,
)

interface CurrentCallingSnapshot {
  registerId: number
  patientName?: string
  queueNumber?: number
  callRound?: number
  doctorName?: string
  departmentName?: string
}

const myPosition = ref<{
  queueNumber: number | null
  waitingBefore: number | null
  callStatus: number | null
  callRound: number | null
  checkedIn: boolean
} | null>(null)
const currentCallingSnapshot = ref<CurrentCallingSnapshot | null>(null)
const latestCalling = ref<CallingEvent | null>(null)
const sseStatus = ref<'idle' | 'connecting' | 'connected' | 'error'>('idle')
const positionApiOk = ref(false)

let subscription: CallingSubscription | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT = 3

const checkedIn = computed(() => Boolean(myPosition.value?.checkedIn || current.value?.checkedIn))
const queueNo = computed(() => myPosition.value?.queueNumber ?? null)
const waitingBefore = computed(() => myPosition.value?.waitingBefore ?? null)

const currentCalling = computed(() => {
  if (latestCalling.value) {
    return {
      registerId: latestCalling.value.registerId,
      patientName: latestCalling.value.patientName,
      queueNumber: latestCalling.value.queueNumber,
      callRound: latestCalling.value.callRound,
      doctorName: latestCalling.value.doctorName,
      departmentName: latestCalling.value.departmentName,
      type: latestCalling.value.type,
    }
  }
  if (currentCallingSnapshot.value) return { ...currentCallingSnapshot.value, type: undefined }
  return null
})

const queueBadge = computed<{ text: string; tone: 'idle' | 'waiting' | 'called' | 'passed' }>(() => {
  const myStatus = myPosition.value?.callStatus
  if (myStatus === 3) return { text: '已过号', tone: 'passed' }
  if (myStatus === 2) return { text: '已就诊', tone: 'called' }
  if (myStatus === 1 && current.value) return { text: '请就诊', tone: 'called' }
  if (sseStatus.value === 'connecting') return { text: '连接中', tone: 'idle' }
  if (sseStatus.value === 'error') return { text: '重连中', tone: 'idle' }
  if (!checkedIn.value) return { text: '待报到', tone: 'idle' }
  return { text: '候诊中', tone: 'waiting' }
})

const visitPeriod = computed(() => {
  const text = current.value?.visitTime || ''
  if (/下午|PM|pm/.test(text)) return '下午'
  if (/上午|AM|am/.test(text)) return '上午'
  const hour = Number(String(text).match(/(\d{1,2}):/)?.[1] ?? NaN)
  if (!Number.isNaN(hour)) return hour >= 12 ? '下午' : '上午'
  return text || '时间待定'
})

function maskName(name?: string): string {
  if (!name) return '—'
  return name.charAt(0) + (name.length > 1 ? '×'.repeat(Math.min(name.length - 1, 2)) : '')
}

function formatDate(raw?: string) {
  if (!raw) return '日期待定'
  return String(raw).replace('T', ' ').slice(0, 10)
}

function startSubscription() {
  stopSubscription()
  const deptId = current.value?.departmentId
  if (!deptId) return
  sseStatus.value = 'connecting'
  subscription = subscribeDepartment(
    deptId,
    event => {
      reconnectAttempts = 0
      latestCalling.value = event
      if (event.type === 'CALLED' && current.value && event.registerId === current.value.id) {
        try { uni.vibrateShort?.({ type: 'medium' }) } catch { /* ignore */ }
        uni.showToast({ title: '到您了，请前往就诊', icon: 'none', duration: 3000 })
      }
      if (current.value) void refreshMyPosition(current.value.id)
    },
    () => {
      sseStatus.value = 'connected'
      reconnectAttempts = 0
    },
    error => {
      sseStatus.value = 'error'
      console.warn('[queue SSE]', error.message)
      if (reconnectAttempts < MAX_RECONNECT) {
        reconnectAttempts += 1
        reconnectTimer = setTimeout(() => {
          if (current.value && sseStatus.value === 'error') startSubscription()
        }, 5000)
      }
    },
  )
}

function stopSubscription() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  subscription?.close()
  subscription = null
  if (sseStatus.value !== 'idle') sseStatus.value = 'idle'
  latestCalling.value = null
}

async function refreshMyPosition(registerId: number) {
  try {
    const data = await registrationApi.myPosition(registerId)
    myPosition.value = {
      queueNumber: data.queueNumber ?? null,
      waitingBefore: data.waitingBefore ?? null,
      callStatus: data.callStatus ?? null,
      callRound: data.callRound ?? null,
      checkedIn: data.checkedIn ?? false,
    }
    if (!latestCalling.value) currentCallingSnapshot.value = data.currentCalling ?? null
    positionApiOk.value = true
  } catch (e) {
    if (positionApiOk.value) console.warn('[my-position] 偶发失败', e)
    else {
      console.warn('[my-position] 接口不可用，降级到 managed', e)
      fallbackFromManaged()
    }
  }
}

function fallbackFromManaged() {
  if (!current.value) return
  myPosition.value = {
    queueNumber: null,
    waitingBefore: null,
    callStatus: null,
    callRound: null,
    checkedIn: !!current.value.checkedIn,
  }
}

async function load() {
  loading.value = true
  try {
    registrations.value = await registrationApi.managed() || []
  } finally {
    loading.value = false
  }
  if (current.value) {
    fallbackFromManaged()
    void refreshMyPosition(current.value.id)
    startSubscription()
  }
}

async function checkin() {
  if (!current.value || checkedIn.value) return
  uni.showModal({
    title: '确认报到',
    content: `${current.value.departmentName || '门诊'} · ${formatDate(current.value.visitDate)} ${current.value.visitTime || ''}`,
    success: async r => {
      if (!r.confirm) return
      try {
        await registrationApi.checkIn(current.value!.id)
        uni.showToast({ title: '报到成功', icon: 'success' })
        await load()
      } catch { /* request 已统一提示 */ }
    },
  })
}

onShow(load)
onHide(stopSubscription)
onUnmounted(stopSubscription)
</script>

<template>
  <view class="queue-page">
    <PageHeader title="候诊查询" subtitle="查看挂号安排并完成到院报到" />

    <view v-if="loading" class="state-card">正在加载候诊信息…</view>
    <view v-else-if="!current" class="state-card">当前就诊人暂无待就诊挂号</view>

    <template v-else>
      <view class="queue-hero">
        <view class="hero-building">+</view>
        <view class="hero-status">
          <text>✓</text>
          <text>{{ checkedIn ? '已到院报到' : '待到院报到' }}</text>
        </view>
        <text class="hero-case">{{ current.caseNumber || `BL${current.id}` }}</text>
        <text class="hero-dept">{{ current.departmentName || '门诊科室' }}</text>
        <view class="hero-line"><view :style="{ width: checkedIn ? '100%' : '36%' }" /></view>
        <view class="hero-time">
          <text>▣</text>
          <text>{{ formatDate(current.visitDate) }}　{{ visitPeriod }}</text>
        </view>
      </view>

      <view class="number-card">
        <view class="round-icon">
          <ServiceIcon type="person" tone="blue" />
        </view>
        <view class="number-copy">
          <text>我的号序</text>
          <text v-if="checkedIn && waitingBefore != null">前面还有 {{ waitingBefore }} 人</text>
          <text v-else-if="checkedIn">已报到，号序计算中</text>
          <text v-else>报到后生成候诊号序</text>
        </view>
        <view class="big-number">
          <text v-if="queueNo">第 {{ queueNo }} 号</text>
          <text v-else>待报到</text>
        </view>
      </view>

      <view class="calling-card" :class="queueBadge.tone">
        <view class="card-title-row">
          <text class="block-title">实时叫号</text>
          <text class="pill">{{ queueBadge.text }}</text>
        </view>
        <view class="calling-inner">
          <view class="call-icon">
            <ServiceIcon type="compose" tone="blue" size="large" />
            <text>)))</text>
          </view>
          <view class="call-copy">
            <template v-if="currentCalling">
              <text class="call-now">当前叫号 第 {{ currentCalling.queueNumber || '--' }} 号</text>
              <text>患者 {{ maskName(currentCalling.patientName) }}</text>
              <text>{{ currentCalling.departmentName || current.departmentName || '科室' }} / {{ currentCalling.doctorName || '医生待定' }}</text>
              <text v-if="currentCalling.callRound">第 {{ currentCalling.callRound }} 次叫号</text>
            </template>
            <template v-else>
              <text class="call-now">{{ checkedIn ? '等待医生叫号' : '报到后进入候诊队列' }}</text>
              <text>{{ sseStatus === 'error' ? '叫号服务连接异常，正在重连' : sseStatus === 'connecting' ? '正在连接叫号服务' : '请留意页面提醒和现场叫号' }}</text>
            </template>
          </view>
        </view>
      </view>

      <view class="visit-card">
        <view class="visit-top">
          <text :class="{ checked: checkedIn }">⊙ {{ checkedIn ? '已挂号' : '待报到' }}</text>
          <text>{{ current.payStatusName || '缴费状态未知' }}</text>
        </view>
        <view class="visit-main">
          <view class="visit-icon">
            <ServiceIcon type="compose" tone="blue" />
          </view>
          <view class="visit-copy">
            <text>{{ current.departmentName || '门诊科室' }}</text>
            <text>{{ current.departmentName || '科室' }} / {{ current.physicianName || '医生待定' }}</text>
            <text>{{ current.patientName || currentPatient?.realName || '患者' }}</text>
          </view>
          <view class="shield-mark">✓</view>
        </view>
        <button :disabled="checkedIn" @tap="checkin">
          <text>{{ checkedIn ? '☑ 已完成报到' : '▣ 到院报到' }}</text>
        </button>
      </view>
    </template>

    <view class="tips-card">
      <view class="tips-title">
        <text>🔔</text>
        <text>就诊提示</text>
      </view>
      <view class="tips-body">
        <view class="tips-line" />
        <view v-for="(tip, index) in ['请提前 20 分钟到院', '留意叫号信息', '过号后请联系分诊台']" :key="tip" class="tip-row">
          <text class="tip-num">{{ index + 1 }}</text>
          <view>
            <text>{{ tip }}</text>
            <text>如有疑问请咨询现场工作人员</text>
          </view>
        </view>
        <view class="tip-illustration">
          <text>✓</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.queue-page {
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 34rpx) 38rpx 54rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 72% 4%, rgba(162, 207, 255, 0.45), transparent 270rpx),
    linear-gradient(180deg, #eef7ff 0%, #f8fbff 45%, #f4f8fd 100%);
  color: #0b2862;
}

.state-card,
.queue-hero,
.number-card,
.calling-card,
.visit-card,
.tips-card {
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 46rpx rgba(42, 91, 161, 0.08);
  border: 1rpx solid rgba(238, 244, 252, 0.96);
}

.state-card {
  padding: 82rpx 28rpx;
  text-align: center;
  color: #8190aa;
  font-size: 24rpx;
}

.queue-hero {
  position: relative;
  height: 300rpx;
  margin-top: 14rpx;
  padding: 42rpx 38rpx;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  background:
    radial-gradient(circle at 82% 36%, rgba(138, 203, 255, 0.8), transparent 115rpx),
    linear-gradient(135deg, #1774ff 0%, #278eff 52%, #6bc2ff 100%);
  color: #fff;
  box-shadow: 0 18rpx 42rpx rgba(39, 115, 255, 0.24);
}

.hero-building {
  position: absolute;
  right: 46rpx;
  bottom: 10rpx;
  width: 185rpx;
  height: 165rpx;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 18rpx;
  border-radius: 32rpx 32rpx 0 0;
  background: linear-gradient(180deg, rgba(255,255,255,.18), rgba(255,255,255,.06));
  color: rgba(255,255,255,.34);
  font-size: 66rpx;
  font-weight: 900;
}

.hero-status {
  position: relative;
  z-index: 1;
  padding: 9rpx 22rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  font-size: 25rpx;
}

.hero-case {
  position: relative;
  z-index: 1;
  margin-top: 25rpx;
  font-size: 50rpx;
  line-height: 1.05;
  font-weight: 850;
  letter-spacing: 2rpx;
}

.hero-dept {
  position: relative;
  z-index: 1;
  margin-top: 20rpx;
  font-size: 31rpx;
  font-weight: 700;
}

.hero-line {
  position: relative;
  z-index: 1;
  width: 76%;
  height: 11rpx;
  margin: 26rpx 0 22rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: rgba(255,255,255,.36);
}

.hero-line view {
  height: 100%;
  border-radius: inherit;
  background: rgba(255,255,255,.92);
}

.hero-time {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 13rpx;
  align-items: center;
  font-size: 25rpx;
}

.number-card {
  min-height: 142rpx;
  margin-top: 24rpx;
  padding: 28rpx 34rpx;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.round-icon {
  width: 82rpx;
  height: 82rpx;
  padding: 10rpx;
  flex: none;
  border-radius: 50%;
  background: #edf5ff;
  box-sizing: border-box;
}

.number-copy {
  flex: 1;
  min-width: 0;
  margin-left: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.number-copy text:first-child {
  color: #0b2862;
  font-size: 31rpx;
  font-weight: 800;
}

.number-copy text:last-child {
  color: #687b99;
  font-size: 24rpx;
}

.big-number {
  color: #1978ff;
  font-size: 42rpx;
  font-weight: 850;
  white-space: nowrap;
}

.calling-card {
  margin-top: 24rpx;
  padding: 30rpx 28rpx;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.block-title {
  color: #0b2862;
  font-size: 32rpx;
  font-weight: 850;
}

.pill {
  padding: 11rpx 22rpx;
  border-radius: 999rpx;
  background: #fff1dc;
  color: #f08a22;
  font-size: 23rpx;
}

.calling-card.called .pill { background: #ddf8ee; color: #16ad83; }
.calling-card.passed .pill { background: #ffe1de; color: #e0383f; }

.calling-inner {
  min-height: 154rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #edf7ff, #f8fbff);
}

.call-icon {
  position: relative;
  width: 95rpx;
  height: 95rpx;
  padding: 10rpx;
  flex: none;
  border-radius: 24rpx;
  background: #eaf4ff;
  box-sizing: border-box;
}

.call-icon > text {
  position: absolute;
  right: -11rpx;
  bottom: -8rpx;
  width: 46rpx;
  height: 46rpx;
  border-radius: 50%;
  background: #fff;
  color: #4b93ff;
  font-size: 16rpx;
  line-height: 46rpx;
  text-align: center;
  box-shadow: 0 8rpx 18rpx rgba(42, 91, 161, 0.12);
}

.call-copy {
  flex: 1;
  min-width: 0;
  margin-left: 34rpx;
  display: flex;
  flex-direction: column;
  gap: 13rpx;
  color: #697b99;
  font-size: 24rpx;
}

.call-now {
  color: #1978ff;
  font-size: 32rpx;
  font-weight: 850;
}

.visit-card {
  position: relative;
  overflow: hidden;
  margin-top: 24rpx;
  padding: 30rpx 28rpx;
}

.visit-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #0fae83;
  font-size: 24rpx;
  font-weight: 700;
}

.visit-top text:last-child {
  color: #0b2862;
  font-weight: 650;
}

.visit-main {
  position: relative;
  z-index: 1;
  padding: 34rpx 0 30rpx;
  display: flex;
  align-items: center;
}

.visit-icon {
  width: 82rpx;
  height: 82rpx;
  padding: 10rpx;
  flex: none;
  border-radius: 24rpx;
  background: #eef6ff;
  box-sizing: border-box;
}

.visit-copy {
  margin-left: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  color: #687b99;
  font-size: 23rpx;
}

.visit-copy text:first-child {
  color: #0b2862;
  font-size: 31rpx;
  font-weight: 850;
}

.shield-mark {
  position: absolute;
  right: 26rpx;
  bottom: 30rpx;
  width: 130rpx;
  height: 130rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 30rpx;
  border: 8rpx solid rgba(65, 142, 255, .11);
  color: rgba(65, 142, 255, .13);
  font-size: 78rpx;
  font-weight: 900;
}

.visit-card button {
  position: relative;
  z-index: 1;
  height: 88rpx;
  margin: 0;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #1676ff, #0066ef);
  color: #fff;
  font-size: 31rpx;
  font-weight: 800;
  line-height: 88rpx;
  box-shadow: 0 12rpx 28rpx rgba(40, 120, 255, 0.24);
}

.visit-card button[disabled] {
  opacity: 1;
  background: linear-gradient(135deg, #1676ff, #0066ef);
  color: #fff;
}

.visit-card button::after {
  border: 0;
}

.tips-card {
  position: relative;
  margin-top: 24rpx;
  padding: 32rpx 34rpx 34rpx;
  overflow: hidden;
}

.tips-title {
  display: flex;
  align-items: center;
  gap: 14rpx;
  color: #0b2862;
  font-size: 32rpx;
  font-weight: 850;
}

.tips-body {
  position: relative;
  margin-top: 28rpx;
  padding-left: 12rpx;
}

.tips-line {
  position: absolute;
  left: 31rpx;
  top: 30rpx;
  bottom: 42rpx;
  width: 3rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #7fb8ff, #2d82ff);
  opacity: .45;
}

.tip-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  margin-bottom: 30rpx;
}

.tip-row:last-child {
  margin-bottom: 0;
}

.tip-num {
  width: 44rpx;
  height: 44rpx;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(145deg, #65b9ff, #126dff);
  color: #fff;
  font-size: 25rpx;
  font-weight: 800;
  line-height: 44rpx;
  text-align: center;
}

.tip-row view {
  margin-left: 34rpx;
  display: flex;
  flex-direction: column;
  gap: 9rpx;
}

.tip-row view text:first-child {
  color: #0b2862;
  font-size: 25rpx;
  font-weight: 760;
}

.tip-row view text:last-child {
  color: #72829c;
  font-size: 22rpx;
}

.tip-illustration {
  position: absolute;
  right: 8rpx;
  bottom: -8rpx;
  width: 170rpx;
  height: 150rpx;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 14rpx;
  border-radius: 28rpx;
  background: linear-gradient(180deg, rgba(191, 222, 255, .46), rgba(219, 236, 255, .16));
  color: rgba(40, 120, 255, .38);
  font-size: 70rpx;
  font-weight: 900;
}
</style>
