<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { registrationApi, type Registration } from '../../api/registration'
import { subscribeDepartment, type CallingEvent, type CallingSubscription } from '../../api/calling'
import { currentPatient } from '../../stores/session'

const loading = ref(false)
const registrations = ref<Registration[]>([])
const current = computed(() => registrations.value.find(item => item.patientId === currentPatient.value?.patientId && item.status !== 3 && item.status !== 4) || null)

// ===== 实时叫号 =====
// 科室订阅句柄
let subscription: CallingSubscription | null = null
// 当前叫号事件（科室视角：医生叫到的最近一个号）
const latestCalling = ref<CallingEvent | null>(null)
// SSE 连接状态：idle | connecting | connected | error
const sseStatus = ref<'idle' | 'connecting' | 'connected' | 'error'>('idle')

// 状态徽章：候诊中 / 请就诊 / 已过号 / 重连中 / 未订阅
const queueBadge = computed<{ text: string; tone: 'idle' | 'waiting' | 'called' | 'passed' }>(() => {
  const calling = latestCalling.value
  if (sseStatus.value === 'connecting') return { text: '连接中', tone: 'idle' }
  if (sseStatus.value === 'error') return { text: '重连中', tone: 'idle' }
  if (sseStatus.value !== 'connected') return { text: '未连接叫号', tone: 'idle' }
  if (!calling) return { text: '等待叫号', tone: 'waiting' }
  if (calling.type === 'PASSED') return { text: '已过号', tone: 'passed' }
  // 检查是否叫到自己（registerId 匹配）
  if (current.value && calling.registerId === current.value.id) return { text: '请就诊', tone: 'called' }
  return { text: '候诊中', tone: 'waiting' }
})

// 脱敏姓氏："张三" → "张"
function maskName(name?: string): string {
  if (!name) return ''
  return name.charAt(0) + (name.length > 1 ? '×'.repeat(Math.min(name.length - 1, 2)) : '')
}

// 重连：error 后退避重试，最多 3 次
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT = 3

function startSubscription() {
  stopSubscription()
  const deptId = current.value?.departmentId
  if (!deptId) return
  sseStatus.value = 'connecting'
  subscription = subscribeDepartment(deptId, event => {
    sseStatus.value = 'connected'
    // 连接成功后重置重连计数
    reconnectAttempts = 0
    latestCalling.value = event
    // 叫到自己时震动提醒
    if (event.type === 'CALLED' && current.value && event.registerId === current.value.id) {
      try { uni.vibrateShort?.({ type: 'medium' }) } catch { /* ignore */ }
      uni.showToast({ title: '到您了，请前往就诊', icon: 'none', duration: 3000 })
    }
  }, error => {
    sseStatus.value = 'error'
    latestCalling.value = null
    console.warn('[queue SSE]', error.message)
    // 自动重连（最多 MAX_RECONNECT 次，每次间隔 5s）
    if (reconnectAttempts < MAX_RECONNECT) {
      reconnectAttempts += 1
      reconnectTimer = setTimeout(() => {
        if (current.value && sseStatus.value === 'error') startSubscription()
      }, 5000)
    }
  })
}

function stopSubscription() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  subscription?.close()
  subscription = null
  if (sseStatus.value !== 'idle') sseStatus.value = 'idle'
  latestCalling.value = null
}

async function load() {
  loading.value = true
  try {
    registrations.value = await registrationApi.managed() || []
  } finally {
    loading.value = false
  }
  // 加载完成后（重新）订阅当前挂号的科室叫号
  if (current.value) startSubscription()
}

async function checkin() {
  if (!current.value) return
  uni.showModal({
    title: '确认报到',
    content: `${current.value.departmentName || '门诊'} · ${current.value.visitDate || ''} ${current.value.visitTime || ''}`,
    success: async r => {
      if (!r.confirm) return
      try {
        await registrationApi.checkIn(current.value!.id)
        uni.showToast({ title: '报到成功', icon: 'success' })
        await load()
      } catch { /* request 已统一提示 */ }
    }
  })
}

onShow(load)
onHide(stopSubscription)
onUnmounted(stopSubscription)
</script>

<template>
  <view class="page-shell">
    <PageHeader title="候诊查询" subtitle="查看挂号安排并完成到院报到" />

    <view v-if="loading" class="empty card">正在加载…</view>
    <view v-else-if="!current" class="empty card">当前就诊人暂无待就诊挂号</view>
    <template v-else>
      <!-- 顶部状态卡 -->
      <view class="queue-hero card">
        <text class="hero-status">{{ current.checkedIn ? '已到院报到' : '待到院报到' }}</text>
        <text class="hero-case">{{ current.caseNumber || `挂号 ${current.id}` }}</text>
        <text class="hero-dept">{{ current.departmentName || '门诊科室' }}</text>
        <view class="progress">
          <view :style="{ width: current.checkedIn ? '100%' : '35%' }" />
        </view>
        <text class="hero-time">{{ current.visitDate }} {{ current.visitTime }}</text>
      </view>

      <!-- 实时叫号卡 -->
      <view class="calling card" :class="queueBadge.tone">
        <view class="calling-head">
          <text class="calling-title">实时叫号</text>
          <text class="calling-tag">{{ queueBadge.text }}</text>
        </view>
        <view class="calling-body">
          <view class="calling-icon">
            <ServiceIcon type="registration" tone="blue" size="large" />
          </view>
          <view class="calling-info">
            <template v-if="latestCalling">
              <text class="calling-now">当前叫号 第 {{ latestCalling.queueNumber || '--' }} 号</text>
              <text class="calling-patient">患者 {{ maskName(latestCalling.patientName) }}</text>
              <text class="calling-doctor">{{ latestCalling.doctorName || '医生待定' }} · {{ latestCalling.departmentName || current.departmentName || '' }}</text>
              <text v-if="latestCalling.callRound" class="calling-round">第 {{ latestCalling.callRound }} 次叫号</text>
            </template>
            <text v-else-if="sseStatus === 'connecting'" class="calling-empty">正在连接叫号服务…</text>
            <text v-else-if="sseStatus === 'error'" class="calling-empty">叫号服务连接中，请稍候…</text>
            <text v-else class="calling-empty">等待叫号开始…</text>
          </view>
        </view>
      </view>

      <!-- 就诊信息 -->
      <view class="visit card">
        <view class="visit-head">
          <text>{{ current.statusName || '待就诊' }}</text>
          <text>{{ current.payStatusName || '' }}</text>
        </view>
        <view class="visit-body">
          <view class="queue-icon-platform">
            <ServiceIcon type="registration" tone="blue" size="large" />
          </view>
          <view>
            <text class="visit-dept">{{ current.departmentName || '门诊科室' }}</text>
            <text class="visit-doc">{{ current.physicianName || '医生待定' }}</text>
            <text class="visit-patient">{{ current.patientName || currentPatient?.realName }}</text>
          </view>
        </view>
        <button :disabled="current.checkedIn" @tap="checkin">
          {{ current.checkedIn ? '已完成报到' : '到院报到' }}
        </button>
      </view>
    </template>

    <view class="section card">
      <view class="section-title">就诊提示</view>
      <view v-for="(tip, index) in ['请提前 20 分钟到院', '留意叫号信息', '过号后请联系分诊台']" :key="tip" class="tips">
        <text class="tip-num">{{ index + 1 }}</text>
        <view>
          <text class="tip-text">{{ tip }}</text>
          <text class="tip-sub">如有疑问请咨询现场工作人员</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.queue-hero {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(135deg, #4289f7, #70b7ff);
  color: #fff;
}
.queue-hero .hero-status { font-size: 22rpx; opacity: .85; }
.queue-hero .hero-case { margin-top: 12rpx; font-size: 38rpx; font-weight: 800; }
.queue-hero .hero-dept { margin-top: 9rpx; font-size: 26rpx; }
.queue-hero .hero-time { font-size: 20rpx; opacity: .9; }
.progress {
  width: 80%;
  height: 10rpx;
  margin: 25rpx 0 12rpx;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, .3);
}
.progress view {
  height: 100%;
  border-radius: 8rpx;
  background: #fff;
}

/* 实时叫号卡 */
.calling {
  margin-top: 22rpx;
  padding: 26rpx;
}
.calling-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}
.calling-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #112650;
}
.calling-tag {
  font-size: 22rpx;
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
  background: #eef2f7;
  color: #718099;
}
.calling.waiting .calling-tag { background: #fff4e0; color: #c47b00; }
.calling.called .calling-tag { background: #e3f7ee; color: #12a67d; }
.calling.passed .calling-tag { background: #fde8e8; color: #c84545; }
.calling-body {
  display: flex;
  align-items: center;
  padding: 10rpx 0;
}
.calling-icon {
  padding: 7rpx;
  border-radius: 25rpx;
  background: #f4f8ff;
}
.calling-info {
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.calling-now { color: #2878ff; font-size: 32rpx; font-weight: 800; }
.calling-patient { color: #112650; font-size: 24rpx; }
.calling-doctor { color: #7d899c; font-size: 22rpx; }
.calling-round { color: #8894a7; font-size: 20rpx; }
.calling-empty { color: #8b97aa; font-size: 24rpx; }
.calling.called .calling-now { color: #12a67d; }
.calling.called { box-shadow: inset 0 0 0 2rpx #b6ebd3; }

.visit { margin-top: 22rpx; padding: 26rpx; }
.visit-head {
  display: flex;
  justify-content: space-between;
  color: #718099;
  font-size: 21rpx;
}
.visit-head text:first-child { color: #12a67d; }
.visit-body {
  display: flex;
  align-items: center;
  padding: 25rpx 0;
}
.visit-body > view:last-child {
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  color: #7d899c;
  font-size: 21rpx;
}
.visit-body > view:last-child text:first-child {
  color: #112650;
  font-size: 28rpx;
  font-weight: 700;
}
.visit button { background: #2878ff; color: #fff; }
.queue-icon-platform { padding: 7rpx; border-radius: 25rpx; background: #f4f8ff; }

.tips {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
}
.tip-num {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eaf3ff;
  color: #2878ff;
}
.tips view {
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.tip-text { font-size: 24rpx; }
.tip-sub { color: #8894a7; font-size: 20rpx; }

.empty { padding: 80rpx 20rpx; text-align: center; color: #8b97aa; font-size: 23rpx; }
</style>
