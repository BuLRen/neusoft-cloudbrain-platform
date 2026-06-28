<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElIcon, ElSkeleton, ElTag } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import type { AiConsultSummary } from '@/shared/api/modules/physician'

const props = withDefaults(defineProps<{
  summary?: AiConsultSummary | null
  hasAiConsultation?: boolean
  loading?: boolean
}>(), {
  loading: false,
})

const suggestedExamTags = computed(() => {
  const raw = props.summary?.suggestedExam?.trim()
  if (!raw) return []
  return raw.split(/[、,，/|]/).map((item) => item.trim()).filter(Boolean)
})
</script>

<template>
  <section class="ai-consult-summary">
    <ElSkeleton v-if="loading" :rows="4" animated />

    <div v-else-if="summary" class="ai-consult-card">
      <header class="ai-consult-header">
        <div class="ai-consult-header__title">
          <span class="ai-consult-header__icon" aria-hidden="true">
            <ElIcon :size="18"><MagicStick /></ElIcon>
          </span>
          <strong>AI 预问诊摘要</strong>
        </div>
        <ElTag v-if="summary.chiefComplaint" type="success" size="small" effect="light" round>
          已完成
        </ElTag>
      </header>

      <div class="ai-consult-grid">
        <div class="ai-consult-item">
          <label>主诉</label>
          <p>{{ summary.chiefComplaint || '—' }}</p>
        </div>
        <div class="ai-consult-item">
          <label>症状时长</label>
          <p>{{ summary.symptomDuration || '—' }}</p>
        </div>
        <div class="ai-consult-item full">
          <label>现病史</label>
          <p>{{ summary.aiSummary || '—' }}</p>
        </div>
        <div class="ai-consult-item">
          <label>既往史</label>
          <p>{{ summary.historySummary || '—' }}</p>
        </div>
        <div class="ai-consult-item">
          <label>过敏史</label>
          <p>{{ summary.allergySummary || '—' }}</p>
        </div>
        <div class="ai-consult-item">
          <label>用药史</label>
          <p>{{ summary.medicationSummary || '—' }}</p>
        </div>
      </div>

      <div v-if="suggestedExamTags.length" class="ai-consult-exams">
        <span class="ai-consult-exams__label">建议检查</span>
        <div class="ai-consult-exams__tags">
          <ElTag
            v-for="tag in suggestedExamTags"
            :key="tag"
            size="small"
            effect="plain"
            round
          >
            {{ tag }}
          </ElTag>
        </div>
      </div>
    </div>

    <ElAlert
      v-else-if="hasAiConsultation === false"
      class="ai-consult-empty"
      type="info"
      show-icon
      :closable="false"
      title="患者未完成 AI 预问诊"
      description="患者未在就诊前进行 AI 预问诊，请按常规流程接诊。"
    />
  </section>
</template>

<style scoped>
.ai-consult-card {
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: linear-gradient(180deg, rgba(240, 249, 255, 0.95) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.45);
}

.ai-consult-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.ai-consult-header__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 15px;
  color: #0369a1;
}

.ai-consult-header__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: #0284c7;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.5);
}

.ai-consult-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.ai-consult-item {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 0 0 1px rgba(224, 242, 254, 0.9);
}

.ai-consult-item.full {
  grid-column: 1 / -1;
}

.ai-consult-item label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-soft);
}

.ai-consult-item p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text);
  white-space: pre-wrap;
}

.ai-consult-exams {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px dashed rgba(125, 211, 252, 0.55);
}

.ai-consult-exams__label {
  flex-shrink: 0;
  padding-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-soft);
}

.ai-consult-exams__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-consult-empty {
  margin: var(--space-5);
  border-radius: var(--radius-lg);
}

@media (max-width: 720px) {
  .ai-consult-grid {
    grid-template-columns: 1fr;
  }

  .ai-consult-exams {
    flex-direction: column;
  }
}
</style>
