<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
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
import { CircleCheck, Warning } from '@element-plus/icons-vue'
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

function parseConfidencePercent(level: string | undefined, index: number, total: number): number {
  if (level) {
    const numeric = Number.parseFloat(level.replace(/%/g, '').trim())
    if (!Number.isNaN(numeric)) {
      return Math.min(100, Math.max(0, numeric <= 1 ? numeric * 100 : numeric))
    }
    if (level.includes('高')) return 75
    if (level.includes('中')) return 50
    if (level.includes('低')) return 25
  }
  const fallback = [68, 22, 10, 6, 4]
  if (index < fallback.length) return fallback[index]
  return Math.max(5, Math.round(100 / Math.max(total, 1)))
}

const nextSteps = computed(() => {
  const steps = new Set<string>()
  for (const item of sortedDiseases.value) {
    for (const step of item.recommendedWorkup || []) {
      const trimmed = step.trim()
      if (trimmed) steps.add(trimmed)
    }
  }
  if (steps.size) return [...steps]
  if (props.aiMeta.redFlags?.length) {
    return props.aiMeta.redFlags.map((flag) => `关注：${flag}`)
  }
  return ['完善体格检查', '必要时行影像学检查', '根据检查结果调整诊断']
})

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

const RATIONALE_COLLAPSE_THRESHOLD = 100
const expandedRationales = ref<Set<number>>(new Set())

function isRationaleExpanded(index: number): boolean {
  return expandedRationales.value.has(index)
}

function shouldCollapseRationale(text: string): boolean {
  return text.length > RATIONALE_COLLAPSE_THRESHOLD
}

function toggleRationale(index: number) {
  const next = new Set(expandedRationales.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedRationales.value = next
}

watch(
  () => props.aiMeta.aiDiagnosis,
  () => {
    expandedRationales.value = new Set()
  },
)
</script>

<template>
  <div v-if="showResults" class="prelim-panel">
    <div class="prelim-panel__scroll">
      <div v-if="displaySummary" class="prelim-panel__summary-card">
        <p class="prelim-panel__summary-title">AI 诊断建议摘要</p>
        <p class="prelim-panel__summary-text">{{ displaySummary }}</p>
      </div>

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
        <h3 class="prelim-panel__heading">AI 建议诊断</h3>
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
              <div class="prelim-panel__disease-main">
                <div class="prelim-panel__disease-top">
                  <span class="prelim-panel__disease-name">{{ item.diseaseName }}</span>
                  <span class="prelim-panel__disease-pct">
                    {{ parseConfidencePercent(item.confidenceLevel, index, sortedDiseases.length) }}%
                  </span>
                </div>
                <div class="prelim-panel__progress" aria-hidden="true">
                  <div
                    class="prelim-panel__progress-fill"
                    :style="{
                      width: `${parseConfidencePercent(item.confidenceLevel, index, sortedDiseases.length)}%`,
                    }"
                  />
                </div>
                <template v-if="diseaseRationale(item)">
                  <p
                    class="prelim-panel__disease-rationale"
                    :class="{
                      'is-collapsed':
                        shouldCollapseRationale(diseaseRationale(item)) && !isRationaleExpanded(index),
                    }"
                  >
                    <span class="prelim-panel__basis-label">依据：</span>{{ diseaseRationale(item) }}
                  </p>
                  <button
                    v-if="shouldCollapseRationale(diseaseRationale(item))"
                    type="button"
                    class="prelim-panel__expand-btn"
                    @click.stop="toggleRationale(index)"
                  >
                    {{ isRationaleExpanded(index) ? '收起' : '展开' }}
                  </button>
                </template>
              </div>
              <ElTag
                v-if="isPrimaryDisease(item, index)"
                size="small"
                type="primary"
                effect="plain"
                class="prelim-panel__primary-tag"
              >
                首要
              </ElTag>
            </div>
          </li>
        </ul>
      </section>

      <section class="prelim-panel__next-steps">
        <h3 class="prelim-panel__next-steps-title">建议下一步</h3>
        <ul class="prelim-panel__next-steps-list">
          <li v-for="(step, idx) in nextSteps" :key="`step-${idx}`">
            <ElIcon class="prelim-panel__next-steps-icon" :size="16"><CircleCheck /></ElIcon>
            <span>{{ step }}</span>
          </li>
        </ul>
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

    <section class="prelim-panel__confirm">
      <div class="prelim-panel__section-head">
        <h3 class="prelim-panel__heading">医生确认初步诊断</h3>
        <ElButton
          v-if="sortedDiseases.length"
          size="small"
          link
          type="primary"
          @click.stop="usePrimaryDiagnosis"
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
          @click.stop
        />
      </ElFormItem>
    </section>
  </div>

  <p v-else class="prelim-panel__empty">
    生成初步诊断后，将在此展示 AI 建议的疾病列表；完整推理长文默认折叠。
  </p>
</template>

<style scoped>
.prelim-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  margin-block-start: var(--space-2);
}

.prelim-panel__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-inline-end: 2px;
}

.prelim-panel__confirm {
  flex-shrink: 0;
  margin-block-start: var(--space-3);
  padding-block-start: var(--space-3);
  padding-inline: var(--space-1);
  border-block-start: 1px solid #e8edf3;
  background: #fff;
}

.prelim-panel__section-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: baseline;
  justify-content: space-between;
  margin-block-end: var(--space-3);
}

.prelim-panel__summary-card {
  padding: var(--space-4);
  border-radius: 10px;
  background: #f0f7ff;
  border: 1px solid #dbeafe;
}

.prelim-panel__summary-title {
  margin: 0 0 var(--space-2);
  font-size: 0.8125rem;
  font-weight: 700;
  color: #2f73f6;
}

.prelim-panel__summary-text {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.65;
  color: var(--color-text);
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

.prelim-panel__heading {
  margin: 0 0 var(--space-3);
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--color-text);
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
  border: 1px solid #e8edf3;
  border-radius: 10px;
  background: #fafbfc;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.prelim-panel__disease-card:hover,
.prelim-panel__disease-card:focus-visible {
  border-color: #93c5fd;
  box-shadow: 0 2px 8px rgba(47, 115, 246, 0.08);
  outline: none;
}

.prelim-panel__disease-card-head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.prelim-panel__disease-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 1.5rem;
  height: 1.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: #fff;
  background: #2f73f6;
  border-radius: 6px;
}

.prelim-panel__disease-main {
  flex: 1;
  min-width: 0;
}

.prelim-panel__disease-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
}

.prelim-panel__disease-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-text);
}

.prelim-panel__disease-pct {
  flex-shrink: 0;
  font-size: 0.8125rem;
  font-weight: 700;
  color: #2f73f6;
}

.prelim-panel__progress {
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
  margin-block-end: var(--space-2);
}

.prelim-panel__progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #60a5fa, #2f73f6);
  transition: width 0.4s var(--ease-standard);
}

.prelim-panel__disease-rationale {
  margin: 0;
  font-size: 0.75rem;
  line-height: 1.55;
  color: var(--color-text-muted);
}

.prelim-panel__basis-label {
  font-weight: 600;
  color: var(--color-text);
}

.prelim-panel__disease-rationale.is-collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.prelim-panel__expand-btn {
  margin-block-start: var(--space-1);
  padding: 0;
  border: none;
  background: none;
  color: #2f73f6;
  font-size: 0.75rem;
  cursor: pointer;
}

.prelim-panel__expand-btn:hover {
  text-decoration: underline;
}

.prelim-panel__primary-tag {
  flex-shrink: 0;
}

.prelim-panel__next-steps {
  padding: var(--space-4);
  border-radius: 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.prelim-panel__next-steps-title {
  margin: 0 0 var(--space-3);
  font-size: 0.875rem;
  font-weight: 700;
  color: #15803d;
}

.prelim-panel__next-steps-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.prelim-panel__next-steps-list li {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  font-size: 0.8125rem;
  line-height: 1.55;
  color: #166534;
}

.prelim-panel__next-steps-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  color: #52c41a;
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
