<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { medicalApi, type PaymentOrder } from '../../api/medical'
import {
  currentPatient,
  refreshCurrentPatientBalance,
  setPatientBalance,
} from '../../stores/session'

const tabs = [
  { label: '待缴费', status: 0, icon: '/static/icons/calendar.svg' },
  { label: '已支付', status: 1, icon: '/static/icons/compose.svg' },
  { label: '全部', status: null, icon: '/static/icons/list.svg' },
]

const tab = ref(1)
const bills = ref<PaymentOrder[]>([])
const loading = ref(false)

const balance = computed(() => Number(currentPatient.value?.accountBalance || 0).toFixed(2))

function amountOf(item: PaymentOrder) {
  return Number(item.status === 1 ? item.paidAmount : item.pendingAmount || item.totalAmount || 0).toFixed(2)
}

function formatDate(value?: string) {
  if (!value) return '暂无就诊时间'
  return String(value).replace('T', ' ').slice(0, 16)
}

function doctorLine(item: PaymentOrder) {
  const department = item.departmentName || '门诊'
  const doctor = item.doctorName || '门诊医生'
  return `${department} ${doctor}`
}

function iconTone(index: number) {
  return ['blue', 'green', 'purple', 'orange', 'blue'][index % 5]
}

function billIcon(item: PaymentOrder) {
  const department = item.departmentName || ''
  if (department.includes('骨')) return 'bone'
  return 'stethoscope'
}

function statusClass(item: PaymentOrder) {
  if (item.status === 1) return 'paid'
  if (item.status === 0) return 'pending'
  return 'other'
}

async function load() {
  const patient = currentPatient.value
  if (!patient) return
  loading.value = true
  try {
    const status = tabs[tab.value].status
    const data = await medicalApi.paymentOrders(patient.patientId, status)
    bills.value = Array.isArray(data.orders) ? data.orders : []
  } finally {
    loading.value = false
  }
}

async function changeTab(index: number) {
  if (tab.value === index) return
  tab.value = index
  await load()
}

async function showDetail(item: PaymentOrder) {
  try {
    const detail = await medicalApi.paymentOrderDetail(item.registerId)
    const lines = [
      `挂号单号：${detail.registerId}`,
      `科室医生：${doctorLine(detail)}`,
      `就诊时间：${formatDate(detail.visitDate)}`,
      `费用总额：¥${Number(detail.totalAmount || 0).toFixed(2)}`,
      `已支付：¥${Number(detail.paidAmount || 0).toFixed(2)}`,
      `待支付：¥${Number(detail.pendingAmount || 0).toFixed(2)}`,
    ]
    if (detail.items?.length) {
      lines.push(
        '',
        '费用明细：',
        ...detail.items.slice(0, 6).map(row =>
          `${row.itemName || row.categoryName || '费用项目'}  ¥${Number(row.totalAmount || 0).toFixed(2)}  ${row.statusName || ''}`,
        ),
      )
    }
    uni.showModal({
      title: '账单详情',
      content: lines.join('\n'),
      showCancel: false,
      confirmText: '知道了',
    })
  } catch {
    uni.showToast({ title: '详情加载失败', icon: 'none' })
  }
}

async function pay(item: PaymentOrder) {
  const confirmed = await new Promise<boolean>(resolve =>
    uni.showModal({
      title: '确认支付',
      content: `${item.departmentName || '门诊费用'}\n需支付 ¥${Number(item.pendingAmount || 0).toFixed(2)}`,
      confirmText: '立即支付',
      success: r => resolve(r.confirm),
      fail: () => resolve(false),
    }),
  )
  if (!confirmed) return
  const result = await medicalApi.payAll(item.registerId)
  if (typeof result.accountBalance === 'number' && currentPatient.value) {
    setPatientBalance(currentPatient.value.patientId, result.accountBalance)
  } else {
    refreshCurrentPatientBalance()
  }
  uni.showToast({ title: '支付成功', icon: 'success' })
  await load()
}

onShow(() => {
  refreshCurrentPatientBalance()
  void load()
})
</script>

<template>
  <view class="payment-page">
    <PageHeader title="门诊缴费" subtitle="查看账单并完成费用支付" />

    <view class="balance-card">
      <view class="balance-copy">
        <text>账户余额</text>
        <text>¥ {{ balance }}</text>
        <text>余额可用于门诊费用支付</text>
      </view>
      <image class="balance-hero" src="/static/payment/balance-hero.svg" mode="aspectFit" />
    </view>

    <view class="tab-card">
      <view
        v-for="(item, index) in tabs"
        :key="item.label"
        class="tab-item"
        :class="{ active: tab === index }"
        @tap="changeTab(index)"
      >
        <image :src="item.icon" mode="aspectFit" />
        <text>{{ item.label }}</text>
      </view>
    </view>

    <view class="bill-panel">
      <view v-if="loading" class="state-card">
        <view class="loading-dot" />
        <text>正在加载账单…</text>
      </view>

      <view
        v-for="(item, index) in bills"
        :key="item.registerId"
        class="bill-row"
      >
        <view class="row-icon">
          <ServiceIcon :type="billIcon(item)" :tone="iconTone(index)" />
        </view>
        <view class="row-main">
          <view class="row-top">
            <text class="department">{{ item.departmentName || '门诊费用' }}</text>
            <text class="status" :class="statusClass(item)">{{ item.statusName || (item.status === 1 ? '已付清' : '待缴费') }}</text>
          </view>
          <text class="date">{{ formatDate(item.visitDate) }}</text>
          <text class="doctor">{{ doctorLine(item) }}</text>
        </view>
        <view class="row-side">
          <text class="amount">¥{{ amountOf(item) }}</text>
          <button v-if="item.status === 0" class="pay-btn" @tap.stop="pay(item)">立即支付</button>
          <text v-else class="detail" @tap.stop="showDetail(item)">查看详情 ›</text>
        </view>
      </view>

      <view v-if="!loading && !bills.length" class="empty-card">
        <view class="empty-icon">
          <ServiceIcon type="wallet" tone="blue" />
        </view>
        <text>当前没有{{ tabs[tab].label }}账单</text>
        <text>产生新的门诊费用后会自动同步到这里</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.payment-page {
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 34rpx) 28rpx 46rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 78% 7%, rgba(154, 204, 255, 0.42) 0, rgba(154, 204, 255, 0) 240rpx),
    linear-gradient(180deg, #eef7ff 0%, #f8fbff 45%, #f5f8fd 100%);
  color: #0b2862;
}

.balance-card {
  position: relative;
  height: 230rpx;
  margin-top: 8rpx;
  padding: 42rpx 36rpx;
  overflow: hidden;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at 82% 36%, rgba(143, 200, 255, 0.6), transparent 96rpx),
    linear-gradient(135deg, #2f75ff 0%, #357fff 44%, #68b9ff 100%);
  box-shadow: 0 18rpx 42rpx rgba(39, 115, 255, 0.24);
  box-sizing: border-box;
}

.balance-card::after {
  content: '';
  position: absolute;
  right: -75rpx;
  bottom: -116rpx;
  width: 290rpx;
  height: 290rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.11);
}

.balance-copy {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  color: #fff;
}

.balance-copy text:first-child {
  font-size: 23rpx;
  opacity: 0.9;
}

.balance-copy text:nth-child(2) {
  margin-top: 14rpx;
  font-size: 54rpx;
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: 1rpx;
}

.balance-copy text:last-child {
  margin-top: 20rpx;
  font-size: 23rpx;
  opacity: 0.82;
}

.balance-hero {
  position: absolute;
  z-index: 1;
  right: 24rpx;
  bottom: 10rpx;
  width: 250rpx;
  height: 185rpx;
}

.tab-card {
  height: 92rpx;
  margin-top: 32rpx;
  padding: 0 18rpx;
  display: flex;
  align-items: center;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 15rpx 38rpx rgba(42, 91, 161, 0.08);
}

.tab-item {
  position: relative;
  flex: 1;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 13rpx;
  color: #7b879e;
  font-size: 25rpx;
}

.tab-item image {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.62;
}

.tab-item.active {
  color: #2878ff;
  font-weight: 700;
}

.tab-item.active image {
  opacity: 1;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 34rpx;
  right: 34rpx;
  bottom: 0;
  height: 5rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #2c82ff, #66aaff);
}

.bill-panel {
  margin-top: 28rpx;
  padding: 22rpx 28rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 46rpx rgba(42, 91, 161, 0.08);
}

.bill-row {
  min-height: 156rpx;
  padding: 27rpx 0;
  display: flex;
  align-items: center;
  gap: 22rpx;
  border-bottom: 1rpx solid #edf2f8;
  box-sizing: border-box;
}

.bill-row:last-child {
  border-bottom: 0;
}

.row-icon {
  width: 80rpx;
  height: 80rpx;
  padding: 10rpx;
  flex: none;
  border-radius: 27rpx;
  background: #f4f8ff;
  box-sizing: border-box;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-top {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.department {
  flex: 1;
  color: #0c2a63;
  font-size: 29rpx;
  font-weight: 780;
}

.status {
  padding: 7rpx 17rpx;
  border-radius: 999rpx;
  font-size: 19rpx;
  line-height: 1;
  white-space: nowrap;
}

.status.paid {
  background: #dff8ef;
  color: #1bb587;
}

.status.pending {
  background: #fff0dc;
  color: #ff8a1f;
}

.status.other {
  background: #eef2f8;
  color: #74829a;
}

.date,
.doctor {
  display: block;
  margin-top: 10rpx;
  color: #7b8aa6;
  font-size: 22rpx;
  line-height: 1.25;
}

.row-side {
  width: 150rpx;
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.amount {
  color: #ff7926;
  font-size: 31rpx;
  font-weight: 800;
}

.detail {
  margin-top: 25rpx;
  color: #2878ff;
  font-size: 21rpx;
}

.pay-btn {
  height: 48rpx;
  margin: 18rpx 0 0;
  padding: 0 22rpx;
  border: 0;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #2f83ff, #1768ef);
  color: #fff;
  font-size: 20rpx;
  line-height: 48rpx;
  box-shadow: 0 8rpx 18rpx rgba(39, 120, 255, 0.22);
}

.pay-btn::after {
  border: 0;
}

.state-card,
.empty-card {
  min-height: 190rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  color: #7f8da6;
  font-size: 23rpx;
}

.empty-icon {
  width: 82rpx;
  height: 82rpx;
  padding: 12rpx;
  border-radius: 28rpx;
  background: #f2f7ff;
  box-sizing: border-box;
}

.empty-card text:first-of-type {
  color: #0b2862;
  font-size: 28rpx;
  font-weight: 730;
}

.loading-dot {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2d86ff, #65c7db);
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.82);
    opacity: 0.55;
  }
  50% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
