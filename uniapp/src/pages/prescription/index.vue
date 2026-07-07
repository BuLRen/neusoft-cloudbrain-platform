<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { medicalApi, type PrescriptionSummary } from '../../api/medical'
import { currentPatient } from '../../stores/session'

const prescriptions = ref<PrescriptionSummary[]>([])
const loading = ref(false)

const totalCount = computed(() => prescriptions.value.length)

async function load() {
  const patient = currentPatient.value
  if (!patient) return
  loading.value = true
  try {
    const data = await medicalApi.prescriptions(patient.patientId)
    prescriptions.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

function isDispensed(item: PrescriptionSummary) {
  const name = item.dispensationStatusName || ''
  return name.includes('已发') && !name.includes('退')
}

function statusText(item: PrescriptionSummary) {
  return item.dispensationStatusName || (isDispensed(item) ? '已发药' : '待发药')
}

function statusClass(item: PrescriptionSummary) {
  if (isDispensed(item)) return 'success'
  if ((item.dispensationStatusName || '').includes('退')) return 'returned'
  return 'pending'
}

function formatDate(value?: string) {
  if (!value) return '日期待定'
  return String(value).replace('T', ' ').slice(0, 10)
}

function doctorLine(item: PrescriptionSummary) {
  const department = item.departmentName || '内科'
  const doctor = item.physicianName || '门诊医生'
  return `${department}  ${doctor}`
}

function titleOf(item: PrescriptionSummary) {
  return item.diagnosis || '门诊用药处方'
}

function amountOf(item: PrescriptionSummary) {
  return Number(item.totalAmount || 0).toFixed(2)
}

function payText(item: PrescriptionSummary) {
  return item.paid ? '费用已缴清' : '费用待缴纳'
}

function viewGuide(item: PrescriptionSummary) {
  if (!item.registerId) {
    uni.showToast({ title: '缺少挂号编号', icon: 'none' })
    return
  }
  const patient = currentPatient.value?.realName || item.patientName || ''
  const encoded = patient ? encodeURIComponent(patient) : ''
  const url = encoded
    ? `/pages/prescription/guide?registerId=${item.registerId}&patientName=${encoded}`
    : `/pages/prescription/guide?registerId=${item.registerId}`
  uni.navigateTo({ url })
}

onShow(load)
</script>

<template>
  <view class="prescription-page">
    <PageHeader title="我的处方" subtitle="查看处方与取药状态" />

    <view class="hero-card">
      <view>
        <text>处方记录</text>
        <text>{{ totalCount }} 张处方已同步</text>
      </view>
      <image src="/static/prescription/hero.svg" mode="aspectFit" />
    </view>

    <view v-if="loading" class="state-card">
      <view class="loading-dot" />
      <text>正在加载处方…</text>
    </view>

    <view
      v-for="item in prescriptions"
      :key="item.id"
      class="rx-card"
    >
      <view class="rx-head">
        <view class="rx-title-wrap">
          <text class="bar" />
          <view>
            <text class="rx-title">门诊处方</text>
            <view class="rx-meta">
              <image src="/static/icons/calendar.svg" mode="aspectFit" />
              <text>{{ formatDate(item.createTime) }}</text>
              <text>·</text>
              <text>{{ doctorLine(item) }}</text>
            </view>
          </view>
        </view>
        <text class="status-tag" :class="statusClass(item)">{{ statusText(item) }}</text>
      </view>

      <view class="rx-body">
        <view class="rx-icon">Rx</view>
        <view class="rx-info">
          <text class="diagnosis">{{ titleOf(item) }}</text>
          <text>处方金额：￥{{ amountOf(item) }}</text>
          <view class="pay-line" :class="{ paid: item.paid }">
            <text>{{ payText(item) }}</text>
            <ServiceIcon v-if="item.paid" type="heart" tone="green" size="mini" />
          </view>
        </view>
      </view>

      <view class="rx-foot">
        <view>
          <image src="/static/icons/compose.svg" mode="aspectFit" />
          <text>挂号编号 {{ item.registerId || '-' }}</text>
        </view>
        <button
          class="guide-button"
          @tap="viewGuide(item)"
        >
          查看用药指导 ›
        </button>
      </view>
    </view>

    <view v-if="!loading && !prescriptions.length" class="empty-card">
      <view class="empty-icon">Rx</view>
      <text>暂无处方记录</text>
      <text>医生开具处方后会自动同步到这里</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.prescription-page {
  min-height: 100vh;
  padding: calc(var(--status-bar-height) + 34rpx) 28rpx 46rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 74% 7%, rgba(162, 207, 255, 0.48) 0, rgba(162, 207, 255, 0) 260rpx),
    linear-gradient(180deg, #eef7ff 0%, #f8fbff 42%, #f3f7fc 100%);
  color: #0b2862;
}

.hero-card {
  position: relative;
  height: 138rpx;
  margin: 8rpx 0 18rpx;
  overflow: hidden;
}

.hero-card > view {
  position: relative;
  z-index: 2;
  padding-left: 26rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.hero-card text:first-child {
  color: #0b2862;
  font-size: 30rpx;
  font-weight: 780;
}

.hero-card text:last-child {
  color: #7d8ba5;
  font-size: 21rpx;
}

.hero-card image {
  position: absolute;
  right: 30rpx;
  top: -28rpx;
  width: 260rpx;
  height: 180rpx;
}

.rx-card,
.state-card,
.empty-card {
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 17rpx 44rpx rgba(42, 91, 161, 0.08);
  border: 1rpx solid rgba(238, 244, 252, 0.9);
}

.rx-card {
  margin-bottom: 22rpx;
  padding: 28rpx 28rpx 22rpx;
}

.rx-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  padding-bottom: 22rpx;
  border-bottom: 1rpx solid #edf2f8;
}

.rx-title-wrap {
  min-width: 0;
  display: flex;
  gap: 14rpx;
}

.bar {
  width: 6rpx;
  height: 30rpx;
  margin-top: 4rpx;
  flex: none;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #2d86ff, #62adff);
}

.rx-title {
  display: block;
  color: #0b2862;
  font-size: 29rpx;
  line-height: 1.25;
  font-weight: 790;
}

.rx-meta {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 13rpx;
  color: #7b8aa6;
  font-size: 21rpx;
  line-height: 1.25;
}

.rx-meta image {
  width: 24rpx;
  height: 24rpx;
  opacity: 0.68;
}

.status-tag {
  padding: 8rpx 19rpx;
  flex: none;
  border-radius: 999rpx;
  font-size: 20rpx;
  line-height: 1;
}

.status-tag.success {
  background: #ddf8ee;
  color: #17b185;
}

.status-tag.pending {
  background: #fff1dc;
  color: #f39a22;
}

.status-tag.returned {
  background: #eef2f8;
  color: #71809a;
}

.rx-body {
  padding: 25rpx 0 27rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.rx-icon {
  width: 78rpx;
  height: 78rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 21rpx;
  background: linear-gradient(145deg, #ffbd5d, #ff7926);
  box-shadow: 0 10rpx 24rpx rgba(255, 126, 39, 0.22);
  color: #fff;
  font-size: 26rpx;
  font-weight: 800;
}

.rx-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 9rpx;
  color: #71809a;
  font-size: 22rpx;
}

.diagnosis {
  color: #0c2a63;
  font-size: 25rpx;
  line-height: 1.35;
  font-weight: 760;
}

.pay-line {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.pay-line.paid {
  color: #687892;
}

.pay-line :deep(.service-icon) {
  width: 26rpx;
  height: 26rpx;
  border-radius: 50%;
  box-shadow: none;
}

.pay-line :deep(.glyph) {
  width: 16rpx;
  height: 16rpx;
}

.rx-foot {
  padding-top: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  border-top: 1rpx dashed #e3eaf4;
}

.rx-foot > view {
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: #7f8da6;
  font-size: 21rpx;
}

.rx-foot image {
  width: 25rpx;
  height: 25rpx;
  opacity: 0.68;
}

.guide-button {
  height: 51rpx;
  margin: 0;
  padding: 0 22rpx;
  flex: none;
  border-radius: 999rpx;
  border: 1rpx solid rgba(45, 128, 255, 0.22);
  background: #f4f9ff;
  color: #2878ff;
  font-size: 21rpx;
  font-weight: 650;
  line-height: 51rpx;
}

.guide-button::after {
  border: 0;
}

.state-card,
.empty-card {
  min-height: 210rpx;
  margin-top: 22rpx;
  padding: 34rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 13rpx;
  color: #7f8da6;
  font-size: 23rpx;
  box-sizing: border-box;
}

.empty-icon {
  width: 78rpx;
  height: 78rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  background: linear-gradient(145deg, #ffbd5d, #ff7926);
  color: #fff;
  font-size: 25rpx;
  font-weight: 800;
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
