<script setup lang="ts">
import { computed } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElFormItem,
  ElIcon,
  ElInput,
  ElTag,
} from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import type { PreliminaryAiMeta, SuggestedDiseaseItem } from '@/shared/api/modules/physician'
import MarkdownContent from './MarkdownContent.vue'
const doctorDiagnosis = defineModel<string>('doctorDiagnosis', { required: true })
const aiReasoningText = defineModel<string>('aiReasoningText', { required: true })

const props = defineProps<{
  aiMeta: PreliminaryAiMeta
  hasAiResult?: boolean
}>()

function parseRank(rank: number | string | undefined): number {
  if (rank === undefined || rank === null) return Number.MAX_SAFE_INTEGER
  if (typeof rank === 'number' && !Number.isNaN(rank)) return rank
  const parsed = Number.parseInt(String(rank).trim(), 10)
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed
}

const sortedDiseases = computed(() => {
  const list = [...(props.aiMeta.suggestedDiseases || [])]
  return list.sort((a, b) => parseRank(a.rank) - parseRank(b.rank))
})

const displaySummary = computed(() => {
  const summary = props.aiMeta.clinicalSummary?.trim()
  if (summary) return summary
  const primary = props.aiMeta.primaryDiagnosis?.trim()
  if (primary) return `优先考虑：${primary}`
  const first = sortedDiseases.value[0]?.diseaseName
  if (first) return `AI 建议首要关注：${first}`
  return ''
})

const showResults = computed(
  () =>
    Boolean(props.hasAiResult) &&
    (sortedDiseases.value.length > 0 ||
      displaySummary.value ||
      doctorDiagnosis.value.trim() ||
      aiReasoningText.value.trim()),
)

function confidenceTagType(level: string | undefined): 'success' | 'warning' | 'info' | 'danger' {
  if (!level) return 'info'
  if (level.includes('高')) return 'success'
  if (level.includes('中')) return 'warning'
  if (level.includes('低')) return 'info'
  const numeric = Number.parseInt(level.replace(/%/g, ''), 10)
  if (!Number.isNaN(numeric)) {
    if (numeric >= 70) return 'success'
    if (numeric >= 40) return 'warning'
    return 'info'
  }
  return 'info'
}

function diseaseRationale(item: SuggestedDiseaseItem): string {
  return (
    item.rationale?.trim() ||
    item.diagnosisBasis?.trim() ||
    item.symptoms?.trim() ||
    ''
  )
}

function isPrimaryDisease(item: SuggestedDiseaseItem, index: number): boolean {
  const primaryName = props.aiMeta.primaryDiagnosis?.trim()
  if (primaryName && item.diseaseName?.trim() === primaryName) return true
  if (item.role === 'primary' || parseRank(item.rank) === 1) return true
  if (item.role === 'differential') return false
  return index === 0
}

function appendDiagnosis(name: string | undefined) {
  const trimmed = name?.trim()
  if (!trimmed) return
  const current = doctorDiagnosis.value
    .split(/[,，、;；\n]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  if (current.includes(trimmed)) return
  doctorDiagnosis.value = current.length ? `${current.join('、')}${'、'}${trimmed}` : trimmed
}

function usePrimaryDiagnosis() {
  const primary =
    props.aiMeta.primaryDiagnosis?.trim() ||
    sortedDiseases.value.find((d, i) => isPrimaryDisease(d, i))?.diseaseName?.trim()
  if (primary) doctorDiagnosis.value = primary
}

const knowledgeBaseRecallDisplay = computed(() => {
  const text = props.aiMeta.knowledgeBaseRecall?.trim()
  if (text) return text
  if (props.aiMeta.isRecalled === true) {
    return '知识库已召回，但工作流未返回召回原文（请在 output_structured 中增加 knowledgeBaseRecall 字符串字段）。'
  }
  if (props.aiMeta.isRecalled === false) {
    return '本次未从知识库召回内容。'
  }
  return ''
})

const showKnowledgeBaseRecall = computed(
  () => Boolean(knowledgeBaseRecallDisplay.value.trim()),
)
</script>

<template>
  <div v-if="showResults" class="prelim-panel">
    <ElAlert
      v-if="displaySummary"
      type="info"
      :closable="false"
      class="prelim-panel__summary"
    >
      <template #title>
        <span class="prelim-panel__summary-label">临床摘要</span>
        {{ displaySummary }}
      </template>
    </ElAlert>

    <section v-if="aiMeta.redFlags?.length" class="prelim-panel__attention">
      <div class="prelim-panel__attention-head">
        <ElIcon class="prelim-panel__attention-icon" :size="18"><Warning /></ElIcon>
        <span class="prelim-panel__attention-title">需关注症状</span>
      </div>
      <ul class="prelim-panel__attention-list">
        <li v-for="(flag, idx) in aiMeta.redFlags" :key="`flag-${idx}`">
          {{ flag }}
        </li>
      </ul>
    </section>

    <section v-if="sortedDiseases.length" class="prelim-panel__section">
      <div class="prelim-panel__section-head">
        <h3 class="prelim-panel__heading">AI 建议诊断</h3>
        <span class="prelim-panel__hint">点击卡片可填入下方医生确认区</span>
      </div>
      <ul class="prelim-panel__disease-list">
        <li
          v-for="(item, index) in sortedDiseases"
          :key="`${item.diseaseName}-${index}`"
          class="prelim-panel__disease-card"
          role="button"
          tabindex="0"
          @click="appendDiagnosis(item.diseaseName)"
          @keydown.enter="appendDiagnosis(item.diseaseName)"
        >
          <div class="prelim-panel__disease-card-head">
            <span class="prelim-panel__disease-rank">
              {{ parseRank(item.rank) === Number.MAX_SAFE_INTEGER ? index + 1 : parseRank(item.rank) }}
            </span>
            <span class="prelim-panel__disease-name">{{ item.diseaseName }}</span>
            <ElTag
              v-if="isPrimaryDisease(item, index)"
              size="small"
              type="primary"
              effect="plain"
            >
              首要
            </ElTag>
            <ElTag
              v-if="item.confidenceLevel"
              size="small"
              :type="confidenceTagType(item.confidenceLevel)"
            >
              {{ item.confidenceLevel }}
            </ElTag>
            <span v-if="item.recommendIcd" class="prelim-panel__icd">{{ item.recommendIcd }}</span>
          </div>
          <p v-if="diseaseRationale(item)" class="prelim-panel__disease-rationale">
            {{ diseaseRationale(item) }}
          </p>
          <ul
            v-if="item.keyEvidence?.length || item.recommendedWorkup?.length"
            class="prelim-panel__disease-meta"
          >
            <li v-if="item.keyEvidence?.length">
              <span class="prelim-panel__meta-label">支持依据</span>
              {{ item.keyEvidence.join('；') }}
            </li>
            <li v-if="item.recommendedWorkup?.length">
              <span class="prelim-panel__meta-label">建议检查</span>
              {{ item.recommendedWorkup.join('；') }}
            </li>
            <li v-if="item.missingOrWeakEvidence?.length">
              <span class="prelim-panel__meta-label">待补充</span>
              {{ item.missingOrWeakEvidence.join('；') }}
            </li>
          </ul>
        </li>
      </ul>
    </section>

    <section class="prelim-panel__section prelim-panel__section--confirm">
      <div class="prelim-panel__section-head">
        <h3 class="prelim-panel__heading">医生确认初步诊断</h3>
        <ElButton
          v-if="sortedDiseases.length"
          size="small"
          link
          type="primary"
          @click="usePrimaryDiagnosis"
        >
          采用首要诊断
        </ElButton>
      </div>
      <ElFormItem label-position="top" class="prelim-panel__confirm-field">
        <ElInput
          v-model="doctorDiagnosis"
          type="textarea"
          :rows="2"
          placeholder="填写或修改将写入病历的初步诊断，多个诊断可用顿号、逗号分隔"
        />
      </ElFormItem>
    </section>

    <ElCollapse class="prelim-panel__collapse">
      <ElCollapseItem
        v-if="aiMeta.excludedDiagnoses?.length"
        title="已排除的诊断"
        name="excluded"
      >
        <ul class="prelim-panel__excluded-list">
          <li v-for="(item, idx) in aiMeta.excludedDiagnoses" :key="idx">
            <strong>{{ item.diseaseName }}</strong>
            <span v-if="item.reason"> — {{ item.reason }}</span>
          </li>
        </ul>
      </ElCollapseItem>

      <ElCollapseItem
        v-if="aiMeta.diagnosisBasis?.trim()"
        title="知识库召回内容"
        name="reasoning"
      >
        <MarkdownContent :source="aiMeta.diagnosisBasis ?? ''" />
      </ElCollapseItem>

      <ElCollapseItem
        v-if="showKnowledgeBaseRecall || aiMeta.confidence != null || aiMeta.llmModel || aiMeta.modelId"
        title="技术与审计信息"
        name="audit"
      >
        <ElDescriptions :column="1" border size="small">
          <ElDescriptionsItem v-if="showKnowledgeBaseRecall" label="知识库召回内容">
            <pre class="prelim-panel__recall-text">{{ knowledgeBaseRecallDisplay }}</pre>
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="aiMeta.confidence != null" label="整体置信度">
            {{ aiMeta.confidence }}%
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="aiMeta.llmModel" label="诊断模型">
            {{ aiMeta.llmModel }}
          </ElDescriptionsItem>
          <ElDescriptionsItem v-else-if="aiMeta.modelId" label="来源">
            {{ aiMeta.modelId }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCollapseItem>
    </ElCollapse>
  </div>

  <p v-else class="prelim-panel__empty">
    生成初步诊断后，将在此展示 AI 建议的疾病列表；完整推理长文默认折叠。
  </p>
</template>

<style scoped>
.prelim-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-block-start: var(--space-2);
}

.prelim-panel__summary :deep(.el-alert__title) {
  font-size: var(--font-size-base);
  line-height: 1.5;
}

.prelim-panel__summary-label {
  font-weight: 600;
  margin-inline-end: var(--space-2);
}

.prelim-panel__attention {
  padding: var(--space-3) var(--space-4);
  border: 1px solid color-mix(in srgb, var(--color-warning) 24%, var(--color-border));
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-warning) 7%, var(--color-surface-strong));
}

.prelim-panel__attention-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
}

.prelim-panel__attention-icon {
  flex-shrink: 0;
  color: var(--color-warning);
}

.prelim-panel__attention-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text);
}

.prelim-panel__attention-list {
  margin: 0;
  padding-inline-start: 1.25rem;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text-muted);
}

.prelim-panel__attention-list li + li {
  margin-block-start: var(--space-1);
}

.prelim-panel__section-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: baseline;
  justify-content: space-between;
  margin-block-end: var(--space-3);
}

.prelim-panel__heading {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.prelim-panel__hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.prelim-panel__disease-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.prelim-panel__disease-card {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.prelim-panel__disease-card:hover,
.prelim-panel__disease-card:focus-visible {
  border-color: var(--el-color-primary);
  box-shadow: var(--shadow-sm);
  outline: none;
}

.prelim-panel__disease-card-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.prelim-panel__disease-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.5rem;
  height: 1.5rem;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--color-primary-soft);
  border-radius: var(--radius-sm);
}

.prelim-panel__disease-name {
  font-size: var(--font-size-base);
  font-weight: 600;
}

.prelim-panel__icd {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.prelim-panel__disease-rationale {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-muted);
}

.prelim-panel__disease-meta {
  margin: var(--space-2) 0 0;
  padding-inline-start: 1.1rem;
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: var(--color-text-muted);
}

.prelim-panel__meta-label {
  font-weight: 600;
  color: var(--color-text);
  margin-inline-end: var(--space-1);
}

.prelim-panel__section--confirm {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: var(--radius-lg);
  background: var(--color-primary-soft);
}

.prelim-panel__confirm-field {
  margin: 0;
}

.prelim-panel__confirm-field :deep(.el-form-item__label) {
  display: none;
}

.prelim-panel__collapse {
  border: 0;
}

.prelim-panel__collapse :deep(.el-collapse-item__header) {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  border-bottom-color: var(--color-border);
}

.prelim-panel__recall-text {
  margin: 0;
  font-family: inherit;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-muted);
}

.prelim-panel__recall-text {
  color: var(--color-text);
}

.prelim-panel__excluded-list {
  margin: 0;
  padding-inline-start: 1.25rem;
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.prelim-panel__empty {
  margin: var(--space-4) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
