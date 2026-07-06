<script setup lang="ts">
import ServiceIcon from './ServiceIcon.vue'
import { computed, ref } from 'vue'
import QRCode from 'qrcode'
import { buildRegQrPayload } from '../utils/qrProtocol'

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
const qrPayload = computed(() => props.visit.registerId ? buildRegQrPayload(props.visit.registerId) : '')
const qrMatrix = computed<boolean[][]>(() => {
  if (!qrPayload.value) return []
  const qr = QRCode.create(qrPayload.value, { errorCorrectionLevel: 'M', margin: 1 })
  const size = qr.modules.size
  const data = qr.modules.data
  const rows: boolean[][] = []
  for (let y = 0; y < size; y++) {
    const row: boolean[] = []
    for (let x = 0; x < size; x++) row.push(Boolean(data[y * size + x]))
    rows.push(row)
  }
  return rows
})
const qrCellSize = computed(() => Math.max(6, Math.floor(300 / (qrMatrix.value.length || 1))))
const qrActualSize = computed(() => qrCellSize.value * (qrMatrix.value.length || 1))

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
        <view class="qr-top-glow" />
        <view class="qr-mark">▣</view>
        <text class="qr-title">报到二维码</text>
        <text class="qr-subtitle">到院后请在报到机上扫描此二维码</text>
        <view class="qr-frame">
          <view class="corner lt" />
          <view class="corner rt" />
          <view class="corner lb" />
          <view class="corner rb" />
          <view
            v-if="qrMatrix.length"
            class="qr-code"
            :style="{ width: `${qrActualSize}rpx`, height: `${qrActualSize}rpx` }"
          >
            <view
              v-for="(row, y) in qrMatrix"
              :key="y"
              class="qr-code-row"
              :style="{ height: `${qrCellSize}rpx` }"
            >
              <view
                v-for="(dark, x) in row"
                :key="x"
                class="qr-code-cell"
                :class="{ dark }"
                :style="{ width: `${qrCellSize}rpx`, height: `${qrCellSize}rpx` }"
              />
            </view>
          </view>
          <view v-else class="qr-code-fallback">二维码生成失败</view>
        </view>
        <view class="qr-code-label">
          <text />
          <text>挂号单号 {{ visit.registerId }}</text>
          <text />
        </view>
        <view class="qr-info">
          <view class="qr-avatar">●</view>
          <view class="qr-info-main">
            <text>{{ visit.department }}{{ visit.doctor ? ` · ${visit.doctor}` : '' }}</text>
            <view>
              <text>▣</text>
              <text>{{ visit.date || '日期待定' }} {{ visit.time || '' }}</text>
            </view>
            <view>
              <text>▤</text>
              <text>挂号单号：{{ visit.registerId }}</text>
            </view>
          </view>
          <view class="hospital-silhouette">+</view>
        </view>
        <view class="qr-wave" />
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
  overflow: hidden;
  padding: 58rpx 42rpx calc(54rpx + env(safe-area-inset-bottom));
  border-radius: 38rpx 38rpx 0 0;
  background:
    radial-gradient(circle at 50% 0, rgba(218, 235, 255, 0.96), transparent 260rpx),
    linear-gradient(180deg, rgba(255,255,255,.98), #f8fbff 100%);
  box-shadow: 0 -18rpx 54rpx rgba(42, 91, 161, 0.13);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-top-glow {
  position: absolute;
  top: -145rpx;
  left: 50%;
  width: 470rpx;
  height: 280rpx;
  transform: translateX(-50%);
  border-radius: 50%;
  background: rgba(220, 237, 255, 0.72);
  pointer-events: none;
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
  position: relative;
  z-index: 1;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  background: linear-gradient(145deg, #7ec7ff, #2f74ff);
  box-shadow: 0 14rpx 28rpx rgba(47, 116, 255, 0.28);
  color: #fff;
  font-size: 34rpx;
  font-weight: 800;
}

.qr-title {
  position: relative;
  z-index: 1;
  margin-top: 18rpx;
  color: #0b2862;
  font-size: 40rpx;
  font-weight: 800;
}

.qr-subtitle {
  position: relative;
  z-index: 1;
  margin: 12rpx 0 28rpx;
  color: #7e8ca6;
  font-size: 24rpx;
}

.qr-frame {
  position: relative;
  z-index: 1;
  padding: 26rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 1rpx solid #e0ebf8;
  box-shadow: 0 16rpx 42rpx rgba(42, 91, 161, 0.12);
}

.corner {
  position: absolute;
  width: 32rpx;
  height: 32rpx;
  border-color: #5aa2ff;
  z-index: 2;
}

.corner.lt { left: 17rpx; top: 17rpx; border-left: 5rpx solid #5aa2ff; border-top: 5rpx solid #5aa2ff; border-radius: 8rpx 0 0 0; }
.corner.rt { right: 17rpx; top: 17rpx; border-right: 5rpx solid #5aa2ff; border-top: 5rpx solid #5aa2ff; border-radius: 0 8rpx 0 0; }
.corner.lb { left: 17rpx; bottom: 17rpx; border-left: 5rpx solid #5aa2ff; border-bottom: 5rpx solid #5aa2ff; border-radius: 0 0 0 8rpx; }
.corner.rb { right: 17rpx; bottom: 17rpx; border-right: 5rpx solid #5aa2ff; border-bottom: 5rpx solid #5aa2ff; border-radius: 0 0 8rpx 0; }

.qr-code-label {
  position: relative;
  z-index: 1;
  margin-top: 22rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
  color: #2878ff;
  font-size: 23rpx;
  font-weight: 750;
  letter-spacing: .6rpx;
}

.qr-code-label text:first-child,
.qr-code-label text:last-child {
  width: 48rpx;
  height: 2rpx;
  background: linear-gradient(90deg, transparent, #b7d6ff);
}

.qr-code-label text:last-child {
  background: linear-gradient(90deg, #b7d6ff, transparent);
}

.qr-code {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.qr-code-row {
  display: flex;
}

.qr-code-cell {
  background: #fff;
}

.qr-code-cell.dark {
  background: #09275c;
}

.qr-code-fallback {
  width: 300rpx;
  height: 300rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8190aa;
  font-size: 22rpx;
}

.qr-info {
  position: relative;
  z-index: 1;
  width: 100%;
  min-height: 132rpx;
  margin-top: 28rpx;
  padding: 28rpx 28rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, rgba(241, 248, 255, .96), rgba(232, 243, 255, .9));
  border: 1rpx solid rgba(227, 239, 253, .95);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 20rpx;
  color: #657792;
  font-size: 22rpx;
}

.qr-avatar {
  width: 68rpx;
  height: 68rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(145deg, #7ec7ff, #2f74ff);
  box-shadow: 0 10rpx 24rpx rgba(47, 116, 255, .23);
  color: #fff;
  font-size: 26rpx;
}

.qr-info-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 13rpx;
}

.qr-info-main > text {
  color: #102854;
  font-size: 27rpx;
  font-weight: 800;
}

.qr-info-main view {
  display: flex;
  align-items: center;
  gap: 13rpx;
  color: #7787a2;
  font-size: 22rpx;
}

.qr-info-main view text:first-child {
  width: 24rpx;
  color: #4e92ff;
  font-size: 21rpx;
  text-align: center;
}

.hospital-silhouette {
  position: absolute;
  right: 8rpx;
  bottom: -8rpx;
  width: 160rpx;
  height: 94rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,.85);
  font-size: 62rpx;
  font-weight: 800;
  border-radius: 22rpx 22rpx 0 0;
  background: linear-gradient(180deg, rgba(184, 217, 255, .38), rgba(184, 217, 255, .14));
  pointer-events: none;
}

.qr-wave {
  position: absolute;
  left: -40rpx;
  right: -40rpx;
  bottom: -42rpx;
  height: 120rpx;
  background:
    radial-gradient(ellipse at 20% 10%, rgba(209, 230, 255, .9) 0, transparent 60%),
    radial-gradient(ellipse at 80% 25%, rgba(225, 239, 255, .8) 0, transparent 58%);
  opacity: .78;
  pointer-events: none;
}
</style>
