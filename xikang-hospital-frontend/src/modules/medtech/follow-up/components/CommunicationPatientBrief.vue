<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElSwitch, ElTag } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import FollowUpPatientDetailDialog from '@/modules/medtech/follow-up/components/FollowUpPatientDetailDialog.vue'
import { metricLabel } from '@/shared/constants/outcomeCharts'
import type { FollowUpCommunicationPatientBrief, FollowUpCommunicationSession, FollowUpHealthMetric } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  brief: FollowUpCommunicationPatientBrief | null
  session: FollowUpCommunicationSession | null
  generatingSummary?: boolean
}>()

const emit = defineEmits<{
  generateSummary: []
  openOutcome: []
  toggleAi: [enabled: boolean]
}>()

const patientDetailVisible = ref(false)

const aiEnabled = computed({
  get: () => Boolean(props.session?.aiEscalationEnabled ?? true),
  set: (value: boolean) => emit('toggleAi', value),
})

function formatMetricLine(metric: FollowUpHealthMetric) {
  const label = metricLabel(metric.metricKey)
  const unit = metric.unit?.trim() ?? ''
  const valueText = String(metric.metricValue ?? '—')
  const withUnit = unit && !valueText.includes(unit) ? `${valueText} ${unit}` : valueText
  return `${label}：${withUnit}（${metric.recordDate ?? '—'}）`
}
</script>

<template>
  <GlassCard class="comm-brief comm-brief--standalone">
    <template v-if="brief">
      <div class="comm-brief__head">
        <div>
          <h3>{{ brief.realName ?? '患者' }}</h3>
          <p class="comm-brief__meta">{{ brief.caseNumber }} · {{ brief.gender }} · {{ brief.age }}岁</p>
        </div>
        <ElButton size="small" @click="patientDetailVisible = true">查看患者信息</ElButton>
      </div>
      <p v-if="brief.diagnosis" class="comm-brief__line"><strong>诊断</strong> {{ brief.diagnosis }}</p>
      <p v-if="brief.chiefComplaint" class="comm-brief__line"><strong>主诉</strong> {{ brief.chiefComplaint }}</p>
      <div class="comm-brief__tags">
        <ElTag v-if="brief.observedToday" type="success" effect="plain">今日已观察</ElTag>
        <ElTag v-else type="warning" effect="plain">待观察</ElTag>
        <ElTag v-if="brief.interviewScheduledToday" type="primary" effect="plain">今日访谈</ElTag>
      </div>
      <div v-if="brief.recentMetrics?.length" class="comm-brief__metrics">
        <p class="comm-brief__label">近期指标</p>
        <ul>
          <li v-for="m in brief.recentMetrics.slice(0, 4)" :key="`${m.metricKey}-${m.recordDate}`">
            {{ formatMetricLine(m) }}
          </li>
        </ul>
      </div>
      <div class="comm-brief__actions">
        <ElButton type="primary" :loading="generatingSummary" @click="emit('generateSummary')">
          生成 AI 病例总结
        </ElButton>
        <ElButton @click="emit('openOutcome')">疗效评估</ElButton>
      </div>
      <div class="comm-brief__ai">
        <span>AI 托管代答</span>
        <ElSwitch v-model="aiEnabled" />
      </div>
    </template>
    <p v-else class="comm-brief__empty">请选择患者查看摘要</p>

    <FollowUpPatientDetailDialog
      v-model:visible="patientDetailVisible"
      :register-id="brief?.registerId"
      :fallback-name="brief?.realName"
    />
  </GlassCard>
</template>

<style scoped>
.comm-brief {
  padding: var(--space-4);
}

.comm-brief--standalone {
  align-self: start;
  width: 100%;
}

.comm-brief__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-2);
}

.comm-brief h3 {
  margin: 0;
}

.comm-brief__meta {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.comm-brief__line {
  margin: 0 0 var(--space-2);
  font-size: 13px;
  line-height: 1.5;
}

.comm-brief__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block: var(--space-3);
}

.comm-brief__metrics ul {
  margin: var(--space-1) 0 0;
  padding-inline-start: 1.1em;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.comm-brief__label {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
}

.comm-brief__actions {
  display: grid;
  gap: var(--space-2);
  margin-block: var(--space-4);
}

.comm-brief__ai {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-block-start: var(--space-3);
  border-block-start: 1px solid var(--color-border);
  font-size: 13px;
}

.comm-brief__empty {
  margin: 0;
  color: var(--color-text-muted);
}
</style>
