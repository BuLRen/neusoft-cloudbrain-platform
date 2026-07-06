<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { medicalApi, type PrescriptionSummary } from '../../api/medical'
import { currentPatient } from '../../stores/session'

const prescriptions = ref<PrescriptionSummary[]>([])
const loading = ref(false)

async function load() {
  const patient = currentPatient.value
  if (!patient) return
  loading.value = true
  try {
    prescriptions.value = await medicalApi.prescriptions(patient.patientId)
  } finally {
    loading.value = false
  }
}

function isDispensed(item: PrescriptionSummary) {
  const name = item.dispensationStatusName || ''
  return name.includes('已发') && !name.includes('退')
}

function viewGuide(item: PrescriptionSummary) {
  if (!item.registerId) {
    uni.showToast({ title: '缺少挂号编号', icon: 'none' })
    return
  }
  // patientName 仅作为标题前缀展示；为空时不传，guide 页会显示默认标题
  const patient = currentPatient.value?.realName || ''
  const encoded = patient ? encodeURIComponent(patient) : ''
  const url = encoded
    ? `/pages/prescription/guide?registerId=${item.registerId}&patientName=${encoded}`
    : `/pages/prescription/guide?registerId=${item.registerId}`
  uni.navigateTo({ url })
}

onShow(load)
</script>

<template>
  <view class="page-shell">
    <PageHeader title="我的处方" subtitle="查看处方与取药状态" />

    <view v-if="loading" class="prescription card">正在加载处方…</view>

    <view v-for="item in prescriptions" :key="item.id" class="prescription card">
      <view class="head">
        <view>
          <text>门诊处方</text>
          <text>{{ item.createTime?.slice(0, 10) || '' }} · {{ item.physicianName || '门诊医生' }}</text>
        </view>
        <text :class="['status-tag', isDispensed(item) ? 'success' : 'pending']">
          {{ item.dispensationStatusName || '待处理' }}
        </text>
      </view>

      <view class="drug">
        <view class="drug-icon">Rx</view>
        <view>
          <text>{{ item.diagnosis || '门诊用药处方' }}</text>
          <text>处方金额：¥{{ Number(item.totalAmount || 0).toFixed(2) }}</text>
          <text>{{ item.paid ? '费用已缴清' : '费用待缴纳' }}</text>
        </view>
      </view>

      <view class="foot">
        <text>挂号编号 {{ item.registerId || '-' }}</text>
        <text
          v-if="isDispensed(item)"
          class="guide-link"
          @tap="viewGuide(item)"
        >查看用药指导 ›</text>
      </view>
    </view>

    <view v-if="!loading && !prescriptions.length" class="prescription card">暂无处方记录</view>
  </view>
</template>

<style scoped lang="scss">
.prescription {
  margin-bottom: 20rpx;
  padding: 25rpx;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #edf0f5;
}

.head > view {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.head > view text:first-child {
  font-size: 27rpx;
  font-weight: 700;
}

.head > view text:last-child {
  color: #8490a4;
  font-size: 20rpx;
}

.status-tag {
  padding: 5rpx 12rpx;
  border-radius: 14rpx;
  font-size: 19rpx;
}

.status-tag.success {
  background: #e7f8f1;
  color: #13a37d;
}

.status-tag.pending {
  background: #fff5e0;
  color: #c48019;
}

.drug {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f2f6;
}

.drug-icon {
  width: 62rpx;
  height: 62rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #ffbd5d, #ff7926);
  color: #fff;
  font-size: 20rpx;
}

.drug > view:last-child {
  margin-left: 15rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  color: #7f8b9f;
  font-size: 19rpx;
}

.drug > view:last-child text:first-child {
  color: #122650;
  font-size: 23rpx;
  font-weight: 650;
}

.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 18rpx;
  color: #8490a4;
  font-size: 20rpx;
}

.guide-link {
  color: #2878ff;
  font-weight: 600;
  padding: 6rpx 14rpx;
  border-radius: 14rpx;
  background: #eaf3ff;
}
</style>
