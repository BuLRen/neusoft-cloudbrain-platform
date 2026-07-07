<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { aiApi, type FollowupPlan } from '../../api/ai'
import { currentPatient } from '../../stores/session'

const loading = ref(false)
const plans = ref<FollowupPlan[]>([])
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
  try { plans.value = await aiApi.followups(patientId) || [] } finally { loading.value = false }
}
function feedback(plan: FollowupPlan) {
  uni.showModal({ title:'随访反馈', editable:true, placeholderText:'请输入近期症状和恢复情况', success:async result => {
    if (!result.confirm || !result.content?.trim()) return
    try {
      await aiApi.feedback(plan.id, { symptomFeedback:result.content.trim(), symptomRelief:'partial', sideEffects:'' })
      uni.showToast({ title:'反馈已提交', icon:'success' })
      await load()
    } catch { /* 统一请求层已提示 */ }
  }})
}
onShow(load)
</script>
<template><view class="page-shell"><PageHeader title="随访管理" subtitle="查看真实随访计划与提交恢复反馈"/><view class="health-card"><text>今日健康提醒</text><text>请按医嘱用药，如有不适请及时就医</text><view><text>{{currentPatient?.realName || '就诊人'}}</text><text>{{activePlans.length}} 项待完成</text></view></view><view class="section card"><view class="section-head"><text class="section-title">随访计划</text><text class="section-more">共 {{plans.length}} 项</text></view><view v-if="loading" class="empty">正在加载…</view><view v-else-if="!plans.length" class="empty">暂无随访计划</view><view v-for="item in plans" :key="item.id" class="plan" @tap="item.planStatus !== 'completed' && feedback(item)"><view class="calendar">记</view><view class="plan-main"><text>{{typeName(item.followUpType)}}</text><text>{{item.contentTemplate || '请按计划提交恢复情况'}}</text><text>{{item.plannedDate || '日期待定'}}</text></view><text :class="item.planStatus">{{statusName(item.planStatus)}}</text></view></view></view></template>
<style scoped lang="scss">.health-card{padding:30rpx;border-radius:30rpx;display:flex;flex-direction:column;background:linear-gradient(130deg,#38bda8,#6bddc8);color:#fff;box-shadow:0 15rpx 30rpx rgba(30,174,149,.2)}.health-card>text:first-child{font-size:29rpx;font-weight:700}.health-card>text:nth-child(2){margin-top:10rpx;font-size:22rpx;opacity:.9}.health-card>view{display:flex;gap:15rpx;margin-top:23rpx}.health-card>view text{padding:10rpx 20rpx;border-radius:20rpx;background:rgba(255,255,255,.22);font-size:20rpx}.plan{display:flex;align-items:center;padding:22rpx 0;border-bottom:1rpx solid #edf0f5}.calendar{width:64rpx;height:64rpx;border-radius:18rpx;display:flex;align-items:center;justify-content:center;background:#eaf3ff;color:#2878ff}.plan-main{flex:1;margin-left:15rpx;display:flex;flex-direction:column;gap:7rpx}.plan-main text:first-child{font-size:24rpx;font-weight:650}.plan-main text:not(:first-child){max-width:390rpx;color:#8490a4;font-size:19rpx;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.plan>text{color:#14a37c;font-size:19rpx}.plan>text.overdue{color:#f05a65}.plan>text.completed{color:#8a96a8}.empty{padding:70rpx 0;text-align:center;color:#929caf;font-size:23rpx}</style>
