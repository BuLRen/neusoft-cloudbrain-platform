<script setup lang="ts">
import ServiceIcon from './ServiceIcon.vue'
import CheckInQrCode from './CheckInQrCode.vue'
import { ref } from 'vue'

type VisitState = 1 | 2 | 3 | 5 | 6 | 7 | number

const props = defineProps<{
  visit: {
    registerId?: number
    visitState?: VisitState
    archived?: boolean
    department: string
    doctor: string
    date: string
    time: string
    location?: string
    patient?: string
    previsitState?: 'none' | 'in_progress' | 'completed'
    payStatus?: number
    payStatusName?: string
    checkedIn?: boolean
    checkInTime?: string
  }
}>()

defineEmits<{
  action: []
  previsit: []
}>()

// 与 web 端 PatientRecords.vue 对齐
function visitStateLabel(state?: number) {
  const map: Record<number, string> = {
    1: '已挂号',
    2: '医生接诊',
    3: '看诊结束',
    5: '检查检验中',
    6: '检查检验完成',
    7: '爽约',
  }
  return map[state ?? 0] || '未知'
}

// 状态语义色调，与 web 端 PatientRecords.visitStateTone 对齐
function visitStateTone(state?: number): 'primary' | 'success' | 'warning' | 'danger' {
  if (state === 3) return 'success'
  if (state === 7) return 'danger'
  if (state === 1 || state === 2 || state === 5 || state === 6) return 'primary'
  return 'warning'
}

const qrVisible = ref(false)

function canShowQr() {
  const visit = props.visit
  if (!visit.registerId) return false
  if (visit.payStatus !== 1) return false
  if (visit.visitState === 4 || visit.visitState === 7) return false
  if (visit.checkedIn) return false
  return true
}

function openQr() {
  if (!canShowQr()) return
  qrVisible.value = true
}
</script>

<template>
  <view class="visit-card">
    <view class="card-head">
      <view class="state-tags">
        <text class="state-tag" :class="visitStateTone(visit.visitState)">
          {{ visitStateLabel(visit.visitState) }}
        </text>
        <text class="state-tag" :class="visit.archived ? 'success' : 'warning'">
          {{ visit.archived ? '已归档' : '待医生归档' }}
        </text>
      </view>
      <text class="order">挂号单号：{{ visit.registerId || '—' }}</text>
    </view>

    <view class="card-main">
      <view class="medical">
        <view class="medical-icon">
          <ServiceIcon type="heart" tone="blue" size="large" />
        </view>
        <view>
          <text class="department">{{ visit.department }}</text>
          <text class="doctor">{{ visit.doctor }}</text>
        </view>
      </view>
      <view class="appointment">
        <view class="cal">📅</view>
        <view>
          <text class="date">{{ visit.date || '日期待定' }}</text>
          <text class="period">{{ visit.time || '时间待定' }}</text>
        </view>
      </view>
    </view>

    <view class="card-foot">
      <text>就诊人：{{ visit.patient || '患者' }}</text>
      <view class="actions">
        <button
          v-if="!visit.archived && visit.previsitState === 'none'"
          class="previsit"
          @tap="$emit('previsit')"
        >AI 预问诊</button>
        <button
          v-if="!visit.archived && visit.previsitState === 'in_progress'"
          class="continue"
          @tap="$emit('previsit')"
        >继续预问诊</button>
        <button
          v-if="visit.previsitState === 'completed'"
          class="summary"
          @tap="$emit('previsit')"
        >查看预问诊详情</button>
        <button
          v-if="canShowQr()"
          class="qr"
          @tap="openQr"
        >出示报到码</button>
        <button class="detail" @tap="$emit('action')">查看详情</button>
      </view>
    </view>

    <view v-if="qrVisible" class="qr-mask" @tap="qrVisible=false">
      <view class="qr-panel" @tap.stop>
        <view class="qr-close" @tap="qrVisible=false">×</view>
        <view class="qr-mark">
          <ServiceIcon type="calendar" tone="blue" />
        </view>
        <text class="qr-title">报到二维码</text>
        <text class="qr-subtitle">到院后请在报到机上扫描此二维码</text>
        <CheckInQrCode :register-id="visit.registerId || 0" />
        <view class="qr-info">
          <text>{{ visit.department }} · {{ visit.doctor }}</text>
          <text>{{ visit.date || '日期待定' }} {{ visit.time || '' }}</text>
          <text>挂号单号：{{ visit.registerId }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.visit-card {
  margin-top: 20rpx;
  padding: 22rpx 24rpx 18rpx;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 12rpx 32rpx rgba(42, 91, 161, 0.075);
  color: #102854;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 14rpx;
}

.state-tags {
  display: flex;
  gap: 10rpx;
  flex-wrap: wrap;
  align-items: center;
}

.state-tag {
  padding: 5rpx 16rpx;
  border-radius: 16rpx;
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;

  &.primary {
    background: #eaf3ff;
    color: #2878ff;
  }
  &.success {
    background: #e4f8f2;
    color: #15a878;
  }
  &.warning {
    background: #fff1df;
    color: #d77921;
  }
  &.danger {
    background: #ffe1de;
    color: #e0383f;
  }
  &.neutral {
    background: #eef1f6;
    color: #6b7a93;
  }
}

.order {
  color: #7f8da6;
  font-size: 18rpx;
}

.card-main {
  margin-top: 14rpx;
  padding-bottom: 17rpx;
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #e9eef5;
}

.medical {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.medical-icon {
  padding: 6rpx;
  border-radius: 27rpx;
  background: #edf5ff;
}

.medical > view:last-child {
  margin-left: 17rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.department {
  font-size: 27rpx;
  font-weight: 700;
}

.doctor {
  color: #8190aa;
  font-size: 19rpx;
}

.appointment {
  width: 42%;
  min-height: 78rpx;
  padding-left: 20rpx;
  border-left: 1rpx solid #e5ebf3;
  display: flex;
  align-items: center;
}

.cal {
  margin-right: 13rpx;
  color: #2878ff;
  font-size: 28rpx;
}

.appointment > view:last-child {
  display: flex;
  flex-direction: column;
  gap: 7rpx;
}

.date {
  color: #2878ff;
  font-size: 27rpx;
  font-weight: 700;
}

.period {
  color: #647696;
  font-size: 20rpx;
}

.card-foot {
  padding-top: 17rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #72819a;
  font-size: 19rpx;
}

.actions {
  display: flex;
  gap: 10rpx;
}

.actions button {
  height: 53rpx;
  margin: 0;
  padding: 0 18rpx;
  border-radius: 16rpx;
  font-size: 18rpx;
  line-height: 51rpx;
}

.actions button::after {
  border: 0;
}

.previsit {
  border: 1rpx solid #8cb8ff;
  background: #f5f9ff;
  color: #2878ff;
}

.continue {
  border: 1rpx solid #77d8c0;
  background: #effbf7;
  color: #15a47e;
}

.summary {
  border: 1rpx solid #a99af7;
  background: #f6f3ff;
  color: #7356db;
}

.qr {
  border: 1rpx solid #77d8c0;
  background: #effbf7;
  color: #15a47e;
}

.detail {
  border: 0;
  background: linear-gradient(135deg, #3f91ff, #1768ef);
  color: #fff;
  box-shadow: 0 7rpx 16rpx rgba(40, 120, 255, 0.2);
}

.qr-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  align-items: flex-end;
  background: rgba(16, 40, 84, 0.35);
  backdrop-filter: blur(5px);
}

.qr-panel {
  position: relative;
  width: 100%;
  padding: 54rpx 32rpx calc(46rpx + env(safe-area-inset-bottom));
  border-radius: 38rpx 38rpx 0 0;
  background:
    radial-gradient(circle at 50% 0, rgba(219, 237, 255, 0.94), transparent 230rpx),
    #fff;
  box-shadow: 0 -18rpx 54rpx rgba(42, 91, 161, 0.13);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-close {
  position: absolute;
  right: 28rpx;
  top: 26rpx;
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eef5ff;
  color: #8ca0bd;
  font-size: 42rpx;
  line-height: 1;
}

.qr-mark {
  width: 64rpx;
  height: 64rpx;
}

.qr-mark :deep(.service-icon) {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
}

.qr-title {
  margin-top: 18rpx;
  color: #0b2862;
  font-size: 34rpx;
  font-weight: 800;
}

.qr-subtitle {
  margin: 12rpx 0 28rpx;
  color: #7e8ca6;
  font-size: 22rpx;
}

.qr-info {
  width: 100%;
  margin-top: 24rpx;
  padding: 22rpx 26rpx;
  border-radius: 22rpx;
  background: #f5f9ff;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  color: #657792;
  font-size: 22rpx;
}

.qr-info text:first-child {
  color: #102854;
  font-size: 25rpx;
  font-weight: 700;
}
</style>
