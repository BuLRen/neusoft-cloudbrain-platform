<script setup lang="ts">
import { computed } from 'vue'
import type { MedicationGuideContent, MedicationGuideItem, MedicationGuideRecord } from '../api/medical'

const props = defineProps<{
  record: MedicationGuideRecord | null
  loading?: boolean
  patientName?: string
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
const displayPatient = computed(() => props.record?.patientName || props.patientName || '—')
const sourceLabel = computed(() => {
  const s = props.record?.source
  if (s === 'ai') return 'AI 生成'
  if (s === 'fallback') return 'AI 生成'
  if (s === 'manual') return '人工录入'
  return '—'
})

function formatTime(t?: string) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

function valueOrDash(v?: string | null) {
  if (v == null || v === '' || v === 'null') return '请遵医嘱用药，如有疑问请咨询医生或药师'
  return v
}
</script>

<template>
  <view class="mg-sheet">
    <view class="hospital-card">
      <view class="hospital-mark">+</view>
      <view class="hospital-copy">
        <text>熙康云医院</text>
        <text>用药指导单</text>
      </view>
      <image class="hospital-hero" src="/static/medication/guide-hero.svg" mode="aspectFit" />
    </view>

    <view class="info-card">
      <view class="info-row">
        <text class="info-icon">♙</text>
        <text class="info-label">患者</text>
        <text class="info-value">{{ displayPatient }}</text>
      </view>
      <view class="info-row">
        <text class="info-icon">▧</text>
        <text class="info-label">挂号号</text>
        <text class="info-value">{{ record?.registerId ?? '—' }}</text>
      </view>
      <view class="info-row">
        <text class="info-icon">▣</text>
        <text class="info-label">生成时间</text>
        <text class="info-value">{{ formatTime(content?.generatedAt || record?.createTime) }}</text>
      </view>
      <view class="info-row">
        <text class="info-icon">AI</text>
        <text class="info-label">来源</text>
        <text class="info-value source">{{ sourceLabel }}</text>
      </view>
    </view>

    <view v-if="loading" class="state-card">
      <view class="loading-dot" />
      <text>正在生成用药指导…</text>
    </view>

    <view v-else-if="isFailed" class="state-card error">
      <text class="error-title">用药指导生成失败</text>
      <text>{{ record?.errorMessage || '请稍后查看或联系药师' }}</text>
    </view>

    <template v-else-if="record && content">
      <view v-if="content.generalAdvice" class="advice-card">
        <view class="advice-icon">♧</view>
        <view>
          <text class="section-label">用药总提示</text>
          <text class="section-text">{{ content.generalAdvice }}</text>
        </view>
      </view>

      <view v-if="items.length" class="drug-list">
        <view v-for="(item, idx) in items" :key="idx" class="drug-card">
          <view class="drug-head">
            <text class="drug-index">{{ idx + 1 }}</text>
            <view class="drug-title">
              <text>{{ item.drugName || '药品' }}</text>
              <text v-if="item.drugFormat">{{ item.drugFormat }}</text>
            </view>
            <text v-if="item.quantity !== undefined && item.quantity !== null && item.quantity !== ''" class="drug-qty">× {{ item.quantity }}</text>
          </view>

          <view class="field-list">
            <view v-if="item.usageText" class="field">
              <text class="field-icon">◇</text>
              <view>
                <text class="field-label">医嘱用法</text>
                <text class="field-value">{{ valueOrDash(item.usageText) }}</text>
              </view>
            </view>
            <view v-if="item.howToTake" class="field">
              <text class="field-icon">▦</text>
              <view>
                <text class="field-label">服药建议</text>
                <text class="field-value">{{ valueOrDash(item.howToTake) }}</text>
              </view>
            </view>
            <view v-if="item.takeWithFood" class="field">
              <text class="field-icon">◷</text>
              <view>
                <text class="field-label">服药时机</text>
                <text class="field-value">{{ valueOrDash(item.takeWithFood) }}</text>
              </view>
            </view>
            <view v-if="item.precautions" class="field">
              <text class="field-icon">△</text>
              <view>
                <text class="field-label">注意事项</text>
                <text class="field-value">{{ valueOrDash(item.precautions) }}</text>
              </view>
            </view>
            <view v-if="item.sideEffects" class="field">
              <text class="field-icon">♡</text>
              <view>
                <text class="field-label">可能的不良反应</text>
                <text class="field-value">{{ valueOrDash(item.sideEffects) }}</text>
              </view>
            </view>
            <view v-if="item.storage" class="field">
              <text class="field-icon">▤</text>
              <view>
                <text class="field-label">储存条件</text>
                <text class="field-value">{{ valueOrDash(item.storage) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="content.interactionsNote" class="advice-card interactions">
        <view class="advice-icon">!</view>
        <view>
          <text class="section-label">联合用药提示</text>
          <text class="section-text">{{ content.interactionsNote }}</text>
        </view>
      </view>

      <view class="footer-note">
        <text>♢ 本指导单由 AI 辅助生成，仅供患者参考；具体用药请以医嘱为准。</text>
        <text>如有不适，请及时联系医生或药师。</text>
      </view>
    </template>

    <view v-else class="state-card">
      <text>暂无用药指导</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.mg-sheet {
  padding-bottom: 54rpx;
  color: #102d5c;
}

.hospital-card,
.info-card,
.advice-card,
.drug-card,
.state-card,
.footer-note {
  border: 1rpx solid rgba(232, 241, 250, 0.96);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 16rpx 42rpx rgba(42, 91, 161, 0.07);
}

.hospital-card {
  position: relative;
  min-height: 150rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 22rpx;
  overflow: hidden;
  border-radius: 28rpx;
}

.hospital-mark {
  width: 78rpx;
  height: 78rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  background: linear-gradient(145deg, #7ec7ff, #2f74ff);
  color: #fff;
  font-size: 54rpx;
  font-weight: 700;
  box-shadow: 0 10rpx 26rpx rgba(47, 116, 255, 0.22);
}

.hospital-copy {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.hospital-copy text:first-child {
  color: #0b2862;
  font-size: 33rpx;
  font-weight: 800;
}

.hospital-copy text:last-child {
  color: #7c8ba6;
  font-size: 23rpx;
}

.hospital-hero {
  position: absolute;
  right: 10rpx;
  bottom: -8rpx;
  width: 245rpx;
  height: 155rpx;
  opacity: 0.95;
}

.info-card {
  margin-top: 18rpx;
  padding: 23rpx 30rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(240, 247, 255, 0.96));
}

.info-row {
  min-height: 48rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.info-icon {
  width: 34rpx;
  flex: none;
  color: #4b91ff;
  font-size: 22rpx;
  text-align: center;
  font-weight: 700;
}

.info-label {
  width: 120rpx;
  flex: none;
  color: #7585a1;
  font-size: 22rpx;
}

.info-value {
  flex: 1;
  min-width: 0;
  color: #1d355f;
  font-size: 23rpx;
}

.info-value.source {
  color: #2878ff;
  font-weight: 700;
}

.advice-card {
  margin-top: 24rpx;
  padding: 24rpx;
  display: flex;
  gap: 20rpx;
  border-radius: 22rpx;
  border-color: rgba(45, 191, 156, 0.22);
  background: linear-gradient(135deg, rgba(241, 255, 250, 0.96), rgba(255, 255, 255, 0.96));
}

.advice-card.interactions {
  border-color: rgba(255, 153, 56, 0.26);
  background: linear-gradient(135deg, rgba(255, 249, 238, 0.96), rgba(255, 255, 255, 0.96));
}

.advice-icon {
  width: 52rpx;
  height: 52rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(145deg, #5bd9bd, #23b794);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.interactions .advice-icon {
  background: linear-gradient(145deg, #ffc06a, #ff8a2c);
}

.section-label {
  display: block;
  color: #18ae88;
  font-size: 23rpx;
  font-weight: 780;
}

.interactions .section-label {
  color: #e37d21;
}

.section-text {
  display: block;
  margin-top: 12rpx;
  color: #1e345f;
  font-size: 21rpx;
  line-height: 1.75;
}

.drug-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.drug-card {
  overflow: hidden;
  border-radius: 22rpx;
  border-color: rgba(45, 191, 156, 0.24);
}

.drug-head {
  padding: 28rpx 26rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  border-bottom: 1rpx solid #e8f1f0;
}

.drug-index {
  width: 52rpx;
  height: 52rpx;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(145deg, #5bd9bd, #23b794);
  color: #fff;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 52rpx;
  text-align: center;
}

.drug-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.drug-title text:first-child {
  color: #102d5c;
  font-size: 27rpx;
  font-weight: 780;
  line-height: 1.35;
}

.drug-title text:last-child {
  color: #71809a;
  font-size: 21rpx;
}

.drug-qty {
  color: #1bb58d;
  font-size: 24rpx;
  font-weight: 800;
}

.field-list {
  padding: 4rpx 26rpx 4rpx;
}

.field {
  padding: 24rpx 0;
  display: flex;
  gap: 18rpx;
  border-bottom: 1rpx dashed #e3ece8;
}

.field:last-child {
  border-bottom: 0;
}

.field-icon {
  width: 34rpx;
  flex: none;
  color: #18ae88;
  font-size: 25rpx;
  text-align: center;
  line-height: 1.2;
}

.field > view {
  flex: 1;
  min-width: 0;
}

.field-label {
  display: block;
  color: #16a884;
  font-size: 22rpx;
  font-weight: 760;
}

.field-value {
  display: block;
  margin-top: 13rpx;
  color: #243a63;
  font-size: 21rpx;
  line-height: 1.75;
  white-space: pre-wrap;
}

.footer-note {
  margin-top: 28rpx;
  padding: 27rpx 30rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.74);
  text-align: center;
}

.footer-note text {
  display: block;
  color: #8996ab;
  font-size: 20rpx;
  line-height: 1.7;
}

.state-card {
  min-height: 220rpx;
  margin-top: 24rpx;
  padding: 34rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  border-radius: 24rpx;
  color: #7e8ca4;
  font-size: 23rpx;
}

.state-card.error .error-title {
  color: #e54b55;
  font-size: 26rpx;
  font-weight: 760;
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
