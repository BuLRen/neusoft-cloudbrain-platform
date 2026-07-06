<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import MedicationGuideSheet from '../../components/MedicationGuideSheet.vue'
import { medicalApi, type MedicationGuideRecord } from '../../api/medical'

const registerId = ref(0)
const patientName = ref('')
const record = ref<MedicationGuideRecord | null>(null)
const loading = ref(true)
const errorMessage = ref('')

async function load() {
  if (!registerId.value) {
    uni.showToast({ title: '缺少挂号编号', icon: 'none' })
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    record.value = await medicalApi.medicationGuide(registerId.value)
  } catch (e) {
    record.value = null
    errorMessage.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

function refresh() {
  void load()
}

onLoad((options) => {
  registerId.value = Number(options?.registerId || 0)
  patientName.value = decodeURIComponent(options?.patientName || '')
  void load()
})
</script>

<template>
  <view class="guide-page">
    <PageHeader
      :title="patientName ? `${patientName} · 用药指导` : '用药指导单'"
      subtitle="熙康云医院 · AI 辅助生成"
    />

    <scroll-view class="content" scroll-y>
      <view v-if="loading" class="state-card">
        <text>正在加载用药指导…</text>
      </view>

      <view v-else-if="errorMessage" class="state-card error">
        <text class="err-title">{{ errorMessage }}</text>
        <text class="err-hint">请稍后重试，或联系药师</text>
        <view class="retry-btn" @tap="refresh">重新加载</view>
      </view>

      <MedicationGuideSheet v-else :record="record" :loading="false" />

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.guide-page {
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(180deg, #e6f2ff, #f4f8fd 330rpx);
  color: #102d5c;
  padding: 0 24rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.content {
  flex: 1;
  min-height: 0;
  padding-top: 8rpx;
}

.state-card {
  padding: 80rpx 30rpx;
  text-align: center;
  background: #fff;
  border-radius: 26rpx;
  color: #8b97a9;
  font-size: 22rpx;
  border: 1rpx solid #e8eff8;
}

.state-card.error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.err-title {
  color: #e0383f;
  font-size: 22rpx;
  font-weight: 600;
}

.err-hint {
  color: #a3aec0;
  font-size: 18rpx;
}

.retry-btn {
  margin-top: 12rpx;
  padding: 14rpx 38rpx;
  border-radius: 18rpx;
  background: linear-gradient(145deg, #65b9ff, #3477ef);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  box-shadow: 0 8rpx 20rpx rgba(46, 119, 232, 0.2);
}

.bottom-spacer {
  height: 40rpx;
}
</style>
