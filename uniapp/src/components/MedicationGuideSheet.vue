<script setup lang="ts">
import { computed } from 'vue'
import type { MedicationGuideRecord, MedicationGuideContent, MedicationGuideItem } from '../api/medical'

const props = defineProps<{
  record: MedicationGuideRecord | null
  loading?: boolean
}>()

const content = computed<MedicationGuideContent | null>(() => {
  const raw = props.record?.guideContent
  if (!raw) return null
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as MedicationGuideContent
    } catch {
      return null
    }
  }
  return raw as MedicationGuideContent
})

const items = computed<MedicationGuideItem[]>(() => content.value?.items || [])
const isFailed = computed(() => props.record?.status === 'failed')
const sourceLabel = computed(() => {
  const s = props.record?.source
  if (s === 'ai') return 'AI 生成'
  if (s === 'fallback') return 'AI 生成（降级）'
  if (s === 'manual') return '人工'
  return '—'
})

function formatTime(t?: string) {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 16)
}

function valueOrDash(v?: string | null) {
  if (v == null || v === '' || v === 'null') return '详见药品说明书或咨询药师'
  return v
}
</script>

<template>
  <view class="mg-sheet">
    <!-- 顶部医院头部 -->
    <view class="mg-header">
      <view class="mg-hospital-mark">+</view>
      <view class="mg-header-title">
        <text class="mg-hospital">熙康云医院</text>
        <text class="mg-doc-title">用药指导单</text>
      </view>
    </view>

    <!-- 患者摘要 -->
    <view class="mg-info">
      <view class="mg-info-row">
        <text class="mg-info-label">患者</text>
        <text class="mg-info-value">{{ record?.patientName || '—' }}</text>
      </view>
      <view class="mg-info-row">
        <text class="mg-info-label">挂号号</text>
        <text class="mg-info-value">{{ record?.registerId ?? '—' }}</text>
      </view>
      <view class="mg-info-row">
        <text class="mg-info-label">生成时间</text>
        <text class="mg-info-value">{{ formatTime(content?.generatedAt || record?.createTime) }}</text>
      </view>
      <view class="mg-info-row">
        <text class="mg-info-label">来源</text>
        <text class="mg-info-value mg-source-tag">{{ sourceLabel }}</text>
      </view>
    </view>

    <!-- 加载态 -->
    <view v-if="loading" class="mg-state">正在生成用药指导…</view>

    <!-- 失败态 -->
    <view v-else-if="isFailed" class="mg-empty">
      <text class="mg-empty-title">用药指导生成失败</text>
      <text class="mg-empty-hint">{{ record?.errorMessage || '请稍后查看或联系药师' }}</text>
    </view>

    <!-- 成功态 -->
    <template v-else-if="record && content">
      <!-- 用药总提示 -->
      <view v-if="content.generalAdvice" class="mg-advice">
        <text class="mg-block-label">用药总提示</text>
        <text class="mg-block-text">{{ content.generalAdvice }}</text>
      </view>

      <!-- 药品列表 -->
      <view v-if="items.length" class="mg-drug-list">
        <view v-for="(item, idx) in items" :key="idx" class="mg-drug">
          <view class="mg-drug-head">
            <text class="mg-drug-idx">{{ idx + 1 }}</text>
            <view class="mg-drug-title">
              <text class="mg-drug-name">{{ item.drugName || '药品' }}</text>
              <text v-if="item.drugFormat" class="mg-drug-spec">{{ item.drugFormat }}</text>
            </view>
            <text v-if="item.quantity !== undefined && item.quantity !== null && item.quantity !== ''" class="mg-drug-qty">× {{ item.quantity }}</text>
          </view>
          <view class="mg-field-list">
            <view v-if="item.usageText" class="mg-field">
              <text class="mg-field-label">医嘱用法</text>
              <text class="mg-field-value">{{ valueOrDash(item.usageText) }}</text>
            </view>
            <view v-if="item.howToTake" class="mg-field">
              <text class="mg-field-label">服药建议</text>
              <text class="mg-field-value">{{ valueOrDash(item.howToTake) }}</text>
            </view>
            <view v-if="item.takeWithFood" class="mg-field">
              <text class="mg-field-label">服药时机</text>
              <text class="mg-field-value">{{ valueOrDash(item.takeWithFood) }}</text>
            </view>
            <view v-if="item.precautions" class="mg-field">
              <text class="mg-field-label">注意事项</text>
              <text class="mg-field-value">{{ valueOrDash(item.precautions) }}</text>
            </view>
            <view v-if="item.sideEffects" class="mg-field">
              <text class="mg-field-label">可能的不良反应</text>
              <text class="mg-field-value">{{ valueOrDash(item.sideEffects) }}</text>
            </view>
            <view v-if="item.storage" class="mg-field">
              <text class="mg-field-label">储存条件</text>
              <text class="mg-field-value">{{ valueOrDash(item.storage) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 联合用药提示 -->
      <view v-if="content.interactionsNote" class="mg-interactions">
        <text class="mg-block-label">联合用药提示</text>
        <text class="mg-block-text">{{ content.interactionsNote }}</text>
      </view>

      <!-- 页脚 -->
      <view class="mg-footer">
        <text>本指导单由 AI 辅助生成，仅供患者参考；具体用药请以医嘱为准。</text>
        <text>如有不适，请及时联系医生或药师。</text>
      </view>
    </template>

    <!-- 空态 -->
    <view v-else class="mg-state">暂无用药指导</view>
  </view>
</template>

<style scoped lang="scss">
.mg-sheet {
  padding: 30rpx 25rpx;
  background: #fff;
  border-radius: 26rpx;
  box-shadow: 0 9rpx 25rpx rgba(42, 91, 155, 0.065);
  border: 1rpx solid #e8eff8;
  color: #102d5c;
}

/* 头部 */
.mg-header {
  display: flex;
  align-items: center;
  padding-bottom: 22rpx;
  border-bottom: 2rpx solid #2878ff;
  margin-bottom: 22rpx;
}

.mg-hospital-mark {
  width: 64rpx;
  height: 64rpx;
  border-radius: 19rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #65b9ff, #3477ef);
  color: #fff;
  font-size: 46rpx;
  margin-right: 18rpx;
  box-shadow: 0 8rpx 20rpx rgba(46, 119, 232, 0.2);
}

.mg-header-title {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.mg-hospital {
  font-size: 33rpx;
  font-weight: 750;
  color: #2878ff;
  letter-spacing: 2rpx;
}

.mg-doc-title {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #7285a2;
}

/* 患者摘要 */
.mg-info {
  background: #f7fbff;
  border-radius: 14rpx;
  padding: 18rpx 20rpx;
  margin-bottom: 22rpx;
}

.mg-info-row {
  display: flex;
  align-items: center;
  padding: 6rpx 0;
}

.mg-info-label {
  width: 130rpx;
  color: #8a96a8;
  font-size: 18rpx;
  flex: none;
}

.mg-info-value {
  flex: 1;
  color: #263c62;
  font-size: 20rpx;
}

.mg-source-tag {
  color: #2878ff;
  font-weight: 600;
}

/* 状态 */
.mg-state {
  padding: 60rpx 15rpx;
  text-align: center;
  color: #8b97a9;
  font-size: 20rpx;
}

.mg-empty {
  padding: 35rpx 15rpx;
  text-align: center;
}

.mg-empty-title {
  display: block;
  color: #e0383f;
  font-size: 20rpx;
  font-weight: 600;
}

.mg-empty-hint {
  display: block;
  margin-top: 8rpx;
  color: #a3aec0;
  font-size: 17rpx;
}

/* 总提示 */
.mg-advice {
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: linear-gradient(100deg, #eaf7f1, #f4fbf8);
  border-left: 6rpx solid #1f9a82;
  margin-bottom: 18rpx;
}

.mg-block-label {
  display: block;
  color: #1f9a82;
  font-size: 17rpx;
  font-weight: 600;
  letter-spacing: 0.5rpx;
}

.mg-advice .mg-block-text {
  display: block;
  margin-top: 7rpx;
  color: #1f3b35;
  font-size: 20rpx;
  line-height: 1.6;
}

/* 药品列表 */
.mg-drug-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.mg-drug {
  padding: 16rpx;
  border: 1rpx solid #e3ece8;
  border-radius: 14rpx;
  background: #fbfdfc;
}

.mg-drug-head {
  display: flex;
  align-items: center;
  padding-bottom: 11rpx;
  border-bottom: 1rpx dashed #e3ece8;
}

.mg-drug-idx {
  width: 40rpx;
  height: 40rpx;
  flex: none;
  border-radius: 50%;
  background: #1f9a82;
  color: #fff;
  font-size: 18rpx;
  font-weight: 600;
  text-align: center;
  line-height: 40rpx;
  margin-right: 12rpx;
}

.mg-drug-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.mg-drug-name {
  font-size: 23rpx;
  font-weight: 650;
  color: #1f3b35;
}

.mg-drug-spec {
  margin-top: 4rpx;
  color: #7a8b85;
  font-size: 16rpx;
}

.mg-drug-qty {
  color: #1f9a82;
  font-size: 19rpx;
  font-weight: 500;
}

.mg-field-list {
  margin-top: 9rpx;
  display: flex;
  flex-direction: column;
}

.mg-field {
  display: flex;
  flex-direction: column;
  padding: 7rpx 0;
}

.mg-field-label {
  color: #7a8b85;
  font-size: 16rpx;
  letter-spacing: 0.3rpx;
}

.mg-field-value {
  margin-top: 3rpx;
  color: #263b5e;
  font-size: 19rpx;
  line-height: 1.55;
  white-space: pre-wrap;
}

/* 联合用药提示 */
.mg-interactions {
  margin-top: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 14rpx;
  background: #fff8e6;
  border-left: 6rpx solid #ff8a2c;
}

.mg-interactions .mg-block-label {
  color: #c66e00;
}

.mg-interactions .mg-block-text {
  display: block;
  margin-top: 7rpx;
  color: #6b4a18;
  font-size: 20rpx;
  line-height: 1.6;
}

/* 页脚 */
.mg-footer {
  margin-top: 18rpx;
  padding: 14rpx 12rpx 4rpx;
  border-top: 1rpx dashed #cdd5dd;
  text-align: center;
}

.mg-footer text {
  display: block;
  color: #9aa5b0;
  font-size: 15rpx;
  line-height: 1.7;
}
</style>
