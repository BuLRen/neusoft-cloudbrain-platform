<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { GLUCOSE_RISK_LABELS, GLUCOSE_RISK_TONES } from '@/shared/types/glucoseForecast'
import {
  buildDoctorGlucoseActions,
  buildDoctorGlucoseBrief,
} from '@/shared/utils/glucoseForecastCopy'
import type { GlucoseAdvice } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    advice?: GlucoseAdvice | null
    loading?: boolean
    showRegistrationLink?: boolean
    compact?: boolean
    mode?: 'doctor' | 'patient'
  }>(),
  {
    advice: null,
    loading: false,
    showRegistrationLink: false,
    compact: false,
    mode: 'doctor',
  },
)

const emit = defineEmits<{
  goRegistration: []
}>()

const isPatient = computed(() => props.mode === 'patient')

const riskTone = computed(() => GLUCOSE_RISK_TONES[props.advice?.riskLevel ?? 'unknown'] ?? 'neutral')
const riskLabel = computed(() => GLUCOSE_RISK_LABELS[props.advice?.riskLevel ?? 'unknown'] ?? '未知')

const verdictLabel = computed(() => {
  if (isPatient.value) {
    if (props.advice?.revisitRecommended) return '建议尽快复诊'
    if (props.advice?.riskLevel === 'medium') return '需要关注'
    if (props.advice?.recentReportCount != null && props.advice.recentReportCount < 2) return '请继续录入'
    return '状态良好'
  }
  if (props.advice?.revisitRecommended) return '建议安排复诊'
  if (props.advice?.riskLevel === 'medium') return '需加强随访'
  if (props.advice?.recentReportCount != null && props.advice.recentReportCount < 2) return '数据不足'
  return '暂无需复诊'
})

const verdictTone = computed(() => {
  if (props.advice?.revisitRecommended) return 'danger' as const
  if (props.advice?.riskLevel === 'medium') return 'warning' as const
  if (props.advice?.recentReportCount != null && props.advice.recentReportCount < 2) return 'neutral' as const
  return 'success' as const
})

const sectionTitle = computed(() => {
  if (isPatient.value) return props.compact ? '健康建议' : '血糖健康建议'
  return props.compact ? '预测解读' : '预测解读与随访建议'
})

const patientMainText = computed(() => {
  if (props.advice?.adviceText) return props.advice.adviceText
  return '录入居家血糖并刷新后，将在此显示个性化健康建议。'
})

const doctorBrief = computed(() => buildDoctorGlucoseBrief(props.advice))
const doctorActions = computed(() => buildDoctorGlucoseActions(props.advice))

const showRiskBadge = computed(() => !isPatient.value && props.advice?.riskLevel)
</script>

<template>
  <div class="glucose-revisit-advice" :class="{ compact }" v-loading="loading">
    <div class="advice-head">
      <h4>{{ sectionTitle }}</h4>
      <div class="advice-badges">
        <StatusTag v-if="showRiskBadge" :tone="riskTone">风险：{{ riskLabel }}</StatusTag>
        <StatusTag :tone="verdictTone">{{ verdictLabel }}</StatusTag>
      </div>
    </div>

    <template v-if="isPatient">
      <p class="advice-main">{{ patientMainText }}</p>
      <p v-if="showRegistrationLink" class="advice-registration-hint">
        如需到院复诊，请前往「我的挂号」自行预约。
      </p>
    </template>

    <template v-else>
      <p v-if="doctorBrief" class="advice-main">{{ doctorBrief }}</p>
      <p v-else class="advice-main advice-main--muted">
        刷新预测后，将在此展示未来 24 小时血糖走势解读与随访处置建议。
      </p>
      <ul v-if="doctorActions.length" class="advice-actions-list">
        <li v-for="(item, index) in doctorActions" :key="index">{{ item }}</li>
      </ul>
      <p class="advice-staff-note">
        灰色虚线为预测趋势，实线为患者居家实测；若建议复诊，请通过随访沟通引导患者自行挂号。
      </p>
    </template>

    <div v-if="showRegistrationLink && advice?.revisitRecommended" class="advice-actions">
      <ElButton type="primary" @click="emit('goRegistration')">前往预约挂号</ElButton>
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

.advice-main--muted {
  color: var(--color-text-muted);
}

.advice-registration-hint {
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(59, 130, 246, 0.08);
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.advice-actions-list {
  margin: 0;
  padding-left: 1.2em;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text);
}

.advice-staff-note {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.advice-actions {
  margin-top: var(--space-1);
}
</style>
