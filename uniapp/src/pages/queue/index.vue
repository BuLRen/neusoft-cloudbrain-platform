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

// ===== 我的号序快照（进页面时主动拉一次）=====
// 来自 /registration/calling/my-position，回答两个问题：
//   1. 我是几号 / 前面还有几人
//   2. 当前医生叫到几号了（currentCallingSnapshot）
// SSE 后续事件会覆盖 latestCalling，保持实时。
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

// ===== 实时叫号（SSE）=====
// 科室订阅句柄
let subscription: CallingSubscription | null = null
// 最新叫号事件（科室视角：医生叫到的最近一个号）
// SSE 推过来后会覆盖快照，让"当前叫号"实时更新
const latestCalling = ref<CallingEvent | null>(null)
// SSE 连接状态：idle | connecting | connected | error
const sseStatus = ref<'idle' | 'connecting' | 'connected' | 'error'>('idle')

// 当前叫号（合并快照 + SSE 实时）：
//   - 若 latestCalling 存在（SSE 已推过事件），用 latestCalling
//   - 否则退化到 my-position 拿到的快照（医生叫过号但 SSE 没推过来时）
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
  if (currentCallingSnapshot.value) {
    return { ...currentCallingSnapshot.value, type: undefined }
  }
  return null
})

// 状态徽章：候诊中 / 请就诊 / 已过号 / 重连中 / 未订阅
const queueBadge = computed<{ text: string; tone: 'idle' | 'waiting' | 'called' | 'passed' }>(() => {
  // 优先看自己的叫号状态（来自 my-position 快照）
  const myStatus = myPosition.value?.callStatus
  if (myStatus === 3) return { text: '已过号', tone: 'passed' }
  if (myStatus === 2) return { text: '已就诊', tone: 'called' }
  if (myStatus === 1 && current.value) return { text: '请就诊', tone: 'called' }

  if (sseStatus.value === 'connecting') return { text: '连接中', tone: 'idle' }
  if (sseStatus.value === 'error') return { text: '重连中', tone: 'idle' }

  // 没报到时不显示"等待叫号"，要引导用户先报到
  if (!myPosition.value?.checkedIn) return { text: '请先报到', tone: 'idle' }
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
    // 收到任何业务事件说明连接肯定已建立，重置重连计数
    reconnectAttempts = 0
    latestCalling.value = event
    // 叫到自己时震动提醒
    if (event.type === 'CALLED' && current.value && event.registerId === current.value.id) {
      try { uni.vibrateShort?.({ type: 'medium' }) } catch { /* ignore */ }
      uni.showToast({ title: '到您了，请前往就诊', icon: 'none', duration: 3000 })
    }
    // 收到事件后，自己的号序/状态也可能变了，重新拉一次快照
    if (current.value) refreshMyPosition(current.value.id)
  }, () => {
    // 收到 READY：连接已建立
    sseStatus.value = 'connected'
    reconnectAttempts = 0
  }, error => {
    sseStatus.value = 'error'
    // 注意：不清 latestCalling（保留上一次的叫号信息，避免 UI 闪烁）
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

// 拉一次"我的号序"快照（进页面 + SSE 事件后调用）
// 失败时退化到 managed 接口的 checkedIn 字段，保证页面不空白
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
    // 快照里的"当前叫号"仅在 SSE 还没推过事件时使用
    if (!latestCalling.value) {
      currentCallingSnapshot.value = data.currentCalling ?? null
    }
    positionApiOk.value = true
  } catch (e) {
    // 接口可能没部署（远程旧版本后端），降级到 managed 接口的 checkedIn
    if (positionApiOk.value) {
      // 之前成功过，这次突然失败 → 静默（可能是网络抖动）
      console.warn('[my-position] 偶发失败', e)
    } else {
      // 一直失败 → 接口大概率不存在，使用 managed 兜底
      console.warn('[my-position] 接口不可用，降级到 managed', e)
      fallbackFromManaged()
    }
  }
}

// 降级：从 managed 接口已经拿到的 current（Registration）里取 checkedIn
// 这种情况下号序/前面几人/叫号状态都无法显示，但至少不会让"已报到"的患者看到"尚未报到"
function fallbackFromManaged() {
  if (!current.value) return
  // managed 返回的 Registration 里有 checkedIn 字段（后端 toMap 已计算）
  const checkedIn = !!(current.value as Registration & { checkedIn?: boolean }).checkedIn
  myPosition.value = {
    queueNumber: null,
    waitingBefore: null,
    callStatus: null,
    callRound: null,
    checkedIn,
  }
}

// 标记 my-position 接口是否曾成功过（用于区分"接口不存在"vs"偶发失败"）
// ref 是为了 onShow 重新进页面时能正确重置；切页面后状态不污染
const positionApiOk = ref(false)

async function load() {
  loading.value = true
  try {
    registrations.value = await registrationApi.managed() || []
  } finally {
    loading.value = false
  }
  // 拉到挂号后立即查我的号序快照（回答"现在叫到几号 / 我是几号"）
  if (current.value) {
    // 先用 managed 的 checkedIn 兜底，避免 my-position 接口失败时显示"尚未报到"
    fallbackFromManaged()
    void refreshMyPosition(current.value.id)
    startSubscription()
  }
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

      <!-- 我的号序卡（回答：我是几号 / 前面还有几人）-->
      <view class="mypos card">
        <view class="mypos-head">
          <text class="mypos-title">我的号序</text>
          <text v-if="myPosition?.queueNumber" class="mypos-num">第 {{ myPosition.queueNumber }} 号</text>
        </view>
        <view v-if="myPosition?.checkedIn" class="mypos-body">
          <text v-if="myPosition.callStatus === 1" class="mypos-hint mypos-hint--called">医生正在叫您，请进诊室</text>
          <text v-else-if="myPosition.callStatus === 2" class="mypos-hint mypos-hint--done">已就诊</text>
          <text v-else-if="myPosition.callStatus === 3" class="mypos-hint mypos-hint--passed">已过号，请联系分诊台</text>
          <text v-else-if="myPosition.waitingBefore != null" class="mypos-hint">前面还有 {{ myPosition.waitingBefore }} 人</text>
          <text v-else-if="myPosition.queueNumber != null" class="mypos-hint">已报到，排队中</text>
          <text v-else class="mypos-hint">已报到，号序计算中…</text>
        </view>
        <view v-else class="mypos-body">
          <text class="mypos-hint mypos-hint--warn">尚未报到，号序待报到后生成</text>
        </view>
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
            <template v-if="currentCalling">
              <text class="calling-now">当前叫号 第 {{ currentCalling.queueNumber || '--' }} 号</text>
              <text class="calling-patient">患者 {{ maskName(currentCalling.patientName) }}</text>
              <text class="calling-doctor">{{ currentCalling.doctorName || '医生待定' }} · {{ currentCalling.departmentName || current.departmentName || '' }}</text>
              <text v-if="currentCalling.callRound" class="calling-round">第 {{ currentCalling.callRound }} 次叫号</text>
            </template>
            <text v-else-if="sseStatus === 'connecting'" class="calling-empty">正在连接叫号服务…</text>
            <text v-else-if="sseStatus === 'error'" class="calling-empty">叫号服务连接异常，正在重连…</text>
            <text v-else-if="myPosition?.checkedIn" class="calling-empty">医生尚未开始叫号，请耐心等待</text>
            <text v-else-if="sseStatus === 'connected'" class="calling-empty">已连接叫号服务，报到后将显示叫号信息</text>
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

/* 我的号序卡 */
.mypos {
  margin-top: 22rpx;
  padding: 26rpx;
}
.mypos-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16rpx;
}
.mypos-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #112650;
}
.mypos-num {
  font-size: 42rpx;
  font-weight: 800;
  color: #2878ff;
}
.mypos-body {
  padding: 10rpx 0;
}
.mypos-hint {
  font-size: 24rpx;
  color: #718099;
}
.mypos-hint--called { color: #12a67d; font-weight: 600; }
.mypos-hint--done { color: #8894a7; }
.mypos-hint--passed { color: #c84545; }
.mypos-hint--warn { color: #c47b00; }

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
