<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { followupApi, type PatientFollowUpPlanItem, type PatientMedicationItem } from '../../api/followup'
import { currentPatient } from '../../stores/session'

const loading = ref(false)
const plans = ref<PatientFollowUpPlanItem[]>([])
const medications = ref<PatientMedicationItem[]>([])
const activePlans = computed(() => plans.value.filter(item => item.planStatus !== 'completed'))

function typeName(type?: string) {
  return ({ medication:'用药随访', side_effect:'副作用随访', recovery:'康复随访', revisit:'复诊提醒' } as Record<string,string>)[type || ''] || '随访计划'
}
function statusName(status?: string) {
  return ({ completed:'已完成', pending:'待完成', overdue:'已逾期', cancelled:'已取消' } as Record<string,string>)[status || ''] || '进行中'
}

async function load() {
  const patientId = currentPatient.value?.patientId
  if (!patientId) return
  loading.value = true
  try {
    const [plansRes, medsRes] = await Promise.allSettled([
      followupApi.listPlans(patientId),
      followupApi.listMedications(patientId),
    ])
    plans.value = plansRes.status === 'fulfilled' && Array.isArray(plansRes.value) ? plansRes.value : []
    medications.value = medsRes.status === 'fulfilled' && Array.isArray(medsRes.value) ? medsRes.value : []
  } finally {
    loading.value = false
  }
}

function completePlan(plan: PatientFollowUpPlanItem) {
  if (plan.planStatus === 'completed') return
  uni.showModal({
    title: '标记完成',
    content: `确认将「${typeName(plan.followUpType)}」标记为已完成？`,
    success: async result => {
      if (!result.confirm) return
      try {
        await followupApi.completePlan(plan.id)
        uni.showToast({ title: '已标记完成', icon: 'success' })
        await load()
      } catch { /* 请求层已提示 */ }
    },
  })
}

function openChat() {
  uni.navigateTo({ url: '/pages/followup/chat' })
}

onShow(load)
</script>
<template>
  <view class="page-shell">
    <PageHeader title="随访管理" subtitle="查看随访计划、用药提醒并与医生沟通" />

    <view class="health-card">
      <text class="health-title">今日健康提醒</text>
      <text class="health-desc">请按医嘱用药，如有不适请及时就医</text>
      <view class="health-meta">
        <text>{{ currentPatient?.realName || '就诊人' }}</text>
        <text>{{ activePlans.length }} 项待完成</text>
      </view>
    </view>

    <view class="comm-entry card" @tap="openChat">
      <view class="comm-entry__icon">💬</view>
      <view class="comm-entry__text">
        <text class="comm-entry__title">医患沟通</text>
        <text class="comm-entry__desc">与医生或 AI 助手交流随访与用药问题</text>
      </view>
      <text class="comm-entry__arrow">›</text>
    </view>

    <view class="section card">
      <view class="section-head">
        <text class="section-title">随访计划</text>
        <text class="section-more">共 {{ plans.length }} 项</text>
      </view>
      <view v-if="loading" class="empty">正在加载…</view>
      <view v-else-if="!plans.length" class="empty">暂无随访计划</view>
      <view
        v-for="item in plans"
        :key="item.id"
        class="plan"
        @tap="completePlan(item)"
      >
        <view class="calendar">记</view>
        <view class="plan-main">
          <text class="plan-type">{{ typeName(item.followUpType) }}</text>
          <text class="plan-content">{{ item.contentTemplate || '请按计划提交恢复情况' }}</text>
          <text class="plan-date">{{ item.plannedDate || '日期待定' }}</text>
        </view>
        <text :class="['plan-status', item.planStatus]">{{ statusName(item.planStatus) }}</text>
      </view>
    </view>

    <view class="section card">
      <view class="section-head">
        <text class="section-title">当前用药</text>
        <text class="section-more">{{ medications.length }} 种</text>
      </view>
      <view v-if="loading" class="empty">正在加载…</view>
      <view v-else-if="!medications.length" class="empty">暂无用药提醒</view>
      <view v-for="med in medications" :key="med.id" class="med">
        <view class="med-main">
          <text class="med-name">{{ med.drugName || '药品' }}</text>
          <text class="med-usage">{{ med.drugUsage || '遵医嘱' }}</text>
        </view>
        <text class="med-spec">{{ med.drugNumber || '—' }}</text>
      </view>
    </view>
  </view>
</template>
<style scoped lang="scss">
.health-card {
  padding: 30rpx;
  border-radius: 30rpx;
  display: flex;
  flex-direction: column;
  background: linear-gradient(130deg, #38bda8, #6bddc8);
  color: #fff;
  box-shadow: 0 15rpx 30rpx rgba(30, 174, 149, .2);
}
.health-title { font-size: 29rpx; font-weight: 700; }
.health-desc { margin-top: 10rpx; font-size: 22rpx; opacity: .9; }
.health-meta { display: flex; gap: 15rpx; margin-top: 23rpx; }
.health-meta text { padding: 10rpx 20rpx; border-radius: 20rpx; background: rgba(255, 255, 255, .22); font-size: 20rpx; }

.comm-entry {
  margin-top: 24rpx;
  padding: 26rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(120deg, #eaf3ff, #d4e7ff);
}
.comm-entry__icon { font-size: 40rpx; }
.comm-entry__text { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.comm-entry__title { font-size: 28rpx; font-weight: 650; color: #1e5cae; }
.comm-entry__desc { font-size: 20rpx; color: #5b7494; }
.comm-entry__arrow { font-size: 44rpx; color: #1e5cae; }

.plan {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #edf0f5;
}
.calendar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eaf3ff;
  color: #2878ff;
  font-size: 24rpx;
}
.plan-main { flex: 1; margin-left: 15rpx; display: flex; flex-direction: column; gap: 7rpx; }
.plan-type { font-size: 24rpx; font-weight: 650; }
.plan-content {
  max-width: 390rpx;
  color: #8490a4;
  font-size: 19rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.plan-date { color: #a3adbf; font-size: 19rpx; }
.plan-status { color: #14a37c; font-size: 19rpx; }
.plan-status.overdue { color: #f05a65; }
.plan-status.completed { color: #8a96a8; }

.med {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #edf0f5;
}
.med-main { flex: 1; display: flex; flex-direction: column; gap: 7rpx; }
.med-name { font-size: 24rpx; font-weight: 650; }
.med-usage { color: #8490a4; font-size: 20rpx; }
.med-spec { color: #a3adbf; font-size: 19rpx; }

.empty { padding: 70rpx 0; text-align: center; color: #929caf; font-size: 23rpx; }
</style>
