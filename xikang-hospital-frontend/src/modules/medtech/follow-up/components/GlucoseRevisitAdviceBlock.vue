<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { GLUCOSE_RISK_LABELS, GLUCOSE_RISK_TONES } from '@/shared/types/glucoseForecast'
import type { GlucoseAdvice } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    advice?: GlucoseAdvice | null
    loading?: boolean
    showApplyButton?: boolean
    compact?: boolean
  }>(),
  {
    advice: null,
    loading: false,
    showApplyButton: false,
    compact: false,
  },
)

const emit = defineEmits<{
  revisit: []
}>()

const riskTone = computed(() => GLUCOSE_RISK_TONES[props.advice?.riskLevel ?? 'unknown'] ?? 'neutral')
const riskLabel = computed(() => GLUCOSE_RISK_LABELS[props.advice?.riskLevel ?? 'unknown'] ?? '未知')

const verdictLabel = computed(() => {
  if (props.advice?.revisitRecommended) return '建议复诊'
  if (props.advice?.riskLevel === 'medium') return '需关注'
  if (props.advice?.recentReportCount != null && props.advice.recentReportCount < 2) return '数据不足'
  return '暂无需复诊'
})

const verdictTone = computed(() => {
  if (props.advice?.revisitRecommended) return 'danger' as const
  if (props.advice?.riskLevel === 'medium') return 'warning' as const
  if (props.advice?.recentReportCount != null && props.advice.recentReportCount < 2) return 'neutral' as const
  return 'success' as const
})

const forecastRangeText = computed(() => {
  const min = props.advice?.forecastMin
  const max = props.advice?.forecastMax
  if (min == null || max == null) return ''
  return `未来 24h 预测区间 ${min.toFixed(1)} ~ ${max.toFixed(1)} mmol/L`
})
</script>

<template>
  <div class="glucose-revisit-advice" :class="{ compact }" v-loading="loading">
    <div class="advice-head">
      <h4>{{ compact ? '复诊判断' : '模型复诊建议' }}</h4>
      <div class="advice-badges">
        <StatusTag v-if="advice?.riskLevel" :tone="riskTone">风险：{{ riskLabel }}</StatusTag>
        <StatusTag :tone="verdictTone">{{ verdictLabel }}</StatusTag>
      </div>
    </div>

    <p class="advice-main">
      {{ advice?.adviceText ?? '录入足够居家血糖并刷新预测后，将在此显示是否需要复诊。' }}
    </p>

    <p v-if="compact" class="advice-criteria-compact">
      判断规则：模型风险为「高」，或预测最低 &lt; 3.9 / 最高 &gt; 10.0 mmol/L 时建议复诊；48h 内录入不足 2 次则暂不判定。
    </p>

    <ul v-if="!compact" class="advice-criteria">
      <li>依据 LSTM+GRU 集成模型对未来 24 小时血糖的预测结果与风险等级综合判断。</li>
      <li>满足以下任一条件时，系统判定<strong>建议复诊</strong>：模型风险等级为「高」；或预测最低值 &lt; 3.9 mmol/L（低血糖风险）；或预测最高值 &gt; 10.0 mmol/L（高血糖风险）。</li>
      <li>48 小时内居家自录血糖不足 2 次时，仅提示继续录入，暂不给出复诊结论。</li>
    </ul>

    <div v-if="forecastRangeText || advice?.modelId" class="advice-meta">
      <span v-if="forecastRangeText">{{ forecastRangeText }}</span>
      <span v-if="advice?.modelId">
        <template v-if="forecastRangeText"> · </template>
        模型 {{ advice.modelId }}
        <template v-if="advice.confidence != null">
          （置信度 {{ (advice.confidence * 100).toFixed(0) }}%）
        </template>
      </span>
    </div>

    <div v-if="showApplyButton && advice?.revisitRecommended" class="advice-actions">
      <ElButton type="primary" @click="emit('revisit')">申请复诊</ElButton>
    </div>
  </div>
</template>

<style scoped>
.glucose-revisit-advice {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  display: grid;
  gap: var(--space-3);
}

.glucose-revisit-advice.compact {
  padding: var(--space-3);
}

.advice-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.advice-head h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.advice-badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.advice-main {
  margin: 0;
  line-height: 1.65;
  font-size: 14px;
}

.advice-criteria-compact {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.advice-criteria {
  margin: 0;
  padding-left: 1.2em;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.advice-criteria strong {
  color: var(--color-text);
  font-weight: 600;
}

.advice-meta {
  font-size: 12px;
  color: var(--color-text-muted);
}

.advice-actions {
  margin-top: var(--space-1);
}
</style>
