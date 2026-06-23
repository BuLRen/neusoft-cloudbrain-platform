<script setup lang="ts">
import { ElIcon, ElTag } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import type { ClinicalTimelineEntry } from '@/shared/api/modules/clinicalRecord'

withDefaults(defineProps<{
  timeline: ClinicalTimelineEntry[]
  loading?: boolean
  archived?: boolean
  emptyText?: string
  detailMode?: 'compact' | 'full'
}>(), {
  loading: false,
  archived: false,
  emptyText: '暂无记录，各环节保存后将自动出现在此处。',
  detailMode: 'compact',
})

const eventTypeLabel: Record<string, string> = {
  visit_start: '挂号',
  triage: 'AI 导诊',
  pre_consultation: '预问诊',
  medical_record: '病历',
  preliminary_diagnosis: '初步诊断',
  check_order: '检查申请',
  check_result: '检查结果',
  inspection_order: '检验申请',
  inspection_result: '检验结果',
  disposal_order: '处置申请',
  disposal_result: '处置结果',
  diagnosis: '确诊',
  prescription: '处方',
  visit_archived: '归档',
}

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusTag(entry: ClinicalTimelineEntry) {
  if (entry.status === 'completed') return { type: 'success' as const, text: '已完成' }
  return { type: 'warning' as const, text: '进行中' }
}

function detailText(entry: ClinicalTimelineEntry, mode: 'compact' | 'full') {
  if (!entry.detail) return ''
  const detail = entry.detail

  if (mode === 'full') {
    if (entry.eventType === 'medical_record') {
      return [
        detail.readme ? `主诉：${detail.readme}` : '',
        detail.present ? `现病史：${detail.present}` : '',
        detail.history ? `既往史：${detail.history}` : '',
        detail.allergy ? `过敏史：${detail.allergy}` : '',
        detail.proposal ? `检查建议：${detail.proposal}` : '',
      ].filter(Boolean).join('\n')
    }
    if (entry.eventType === 'pre_consultation') {
      return [
        detail.chiefComplaint ? `主诉：${detail.chiefComplaint}` : '',
        detail.historySummary ? `病史：${detail.historySummary}` : '',
        detail.allergySummary ? `过敏：${detail.allergySummary}` : '',
        detail.aiSummary ? `AI 摘要：${detail.aiSummary}` : '',
      ].filter(Boolean).join('\n')
    }
    if (entry.eventType === 'diagnosis') {
      return [
        detail.diagnosis ? `诊断：${detail.diagnosis}` : '',
        detail.cure ? `治疗：${detail.cure}` : '',
        detail.careful ? `注意事项：${detail.careful}` : '',
      ].filter(Boolean).join('\n')
    }
    if (entry.eventType === 'check_result' && detail.checkResult) {
      return String(detail.checkResult)
    }
    if (entry.eventType === 'inspection_result' && detail.inspectionResult) {
      return String(detail.inspectionResult)
    }
    if (entry.eventType === 'prescription' && Array.isArray(detail.items)) {
      return detail.items.map((item: Record<string, unknown>) =>
        `${item.drugName} ${item.drugUsage || ''} ×${item.drugNumber || ''}`
      ).join('\n')
    }
  }

  if (entry.eventType === 'medical_record') {
    return [detail.readme, detail.present, detail.history].filter(Boolean).join(' · ')
  }
  if (entry.eventType === 'check_result' || entry.eventType === 'inspection_result') {
    const result = detail.checkResult || detail.inspectionResult
    if (typeof result === 'string') return result.slice(0, mode === 'full' ? undefined : 120)
  }
  if (entry.eventType === 'prescription' && Array.isArray(detail.items)) {
    return detail.items.map((item: Record<string, unknown>) => item.drugName).filter(Boolean).join('、')
  }
  return entry.summary
}
</script>

<template>
  <div class="clinical-timeline-content">
    <div v-if="loading" class="clinical-timeline-content__empty">加载中...</div>
    <div v-else-if="!timeline.length" class="clinical-timeline-content__empty">{{ emptyText }}</div>
    <ol v-else class="clinical-timeline-content__list">
      <li
        v-for="(entry, index) in timeline"
        :key="`${entry.eventType}-${entry.sourceId}-${index}`"
        class="clinical-timeline-content__item"
      >
        <div class="clinical-timeline-content__dot" />
        <div class="clinical-timeline-content__body">
          <div class="clinical-timeline-content__head">
            <strong>{{ entry.title }}</strong>
            <ElTag size="small" :type="statusTag(entry).type">{{ statusTag(entry).text }}</ElTag>
          </div>
          <p class="clinical-timeline-content__type">{{ eventTypeLabel[entry.eventType] || entry.eventType }}</p>
          <p class="clinical-timeline-content__summary">{{ entry.summary }}</p>
          <pre v-if="detailText(entry, detailMode)" class="clinical-timeline-content__detail">{{ detailText(entry, detailMode) }}</pre>
          <p class="clinical-timeline-content__time">
            <ElIcon><Clock /></ElIcon>
            {{ formatTime(entry.occurredAt) }}
          </p>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.clinical-timeline-content__empty {
  color: var(--color-text-muted);
  font-size: 14px;
  padding: var(--space-3) 0;
}

.clinical-timeline-content__list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.clinical-timeline-content__item {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: var(--space-3);
  padding-block-end: var(--space-4);
  position: relative;
}

.clinical-timeline-content__item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: var(--color-border);
}

.clinical-timeline-content__dot {
  width: 12px;
  height: 12px;
  margin-block-start: 4px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(31, 140, 255, 0.15);
}

.clinical-timeline-content__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.clinical-timeline-content__type {
  margin: 2px 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.clinical-timeline-content__summary {
  margin: var(--space-2) 0 0;
  color: var(--color-text);
  line-height: 1.6;
}

.clinical-timeline-content__detail {
  margin: var(--space-2) 0 0;
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: rgba(31, 140, 255, 0.06);
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  font-family: inherit;
}

.clinical-timeline-content__time {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: var(--space-2) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}
</style>
