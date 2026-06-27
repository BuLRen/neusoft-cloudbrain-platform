<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElIcon, ElTag, ElTooltip } from 'element-plus'
import { QuestionFilled, StarFilled } from '@element-plus/icons-vue'
import type { W4FallbackSuggestion, W4Output, W4Suggestion } from '@/shared/api/modules/physician'
import {
  formatW4Probability,
  hasW4PanelContent,
  isSimilarToPrimary,
  sortSuggestions,
  splitW4BulletText,
  suggestionDisplayName,
  w4RiskLabel,
  w4RiskTone,
} from '@/shared/types/w4Result'

const props = defineProps<{
  liveOutput?: W4Output | null
  savedSuggestions?: W4Suggestion[]
}>()

const emit = defineEmits<{
  adoptPrimary: [item: W4Suggestion | W4FallbackSuggestion]
  adoptDifferential: [item: W4Suggestion | W4FallbackSuggestion]
}>()

const hasContent = computed(() => hasW4PanelContent(props.liveOutput, props.savedSuggestions))

const status = computed(() => props.liveOutput?.status)

const displaySuggestions = computed(() => {
  if (props.liveOutput && status.value !== 'fallback') {
    return sortSuggestions(props.liveOutput.suggestions)
  }
  return sortSuggestions(props.savedSuggestions)
})

const primarySuggestion = computed(() => displaySuggestions.value[0])

const secondarySuggestions = computed(() => displaySuggestions.value.slice(1))

const showFallback = computed(() => props.liveOutput?.status === 'fallback')

const hasSimilarSecondary = computed(() =>
  secondarySuggestions.value.some((item) => isSimilarToPrimary(primarySuggestion.value, item)),
)

function basisItems(item: W4Suggestion | W4FallbackSuggestion): string[] {
  return splitW4BulletText(item.diagnosisBasis)
}

function treatmentItems(item: W4Suggestion): string[] {
  return splitW4BulletText(item.treatmentDirection)
}
</script>

<template>
  <section class="w4-panel">
    <div class="w4-panel__head">
      <div>
        <div class="w4-panel__title-row">
          <h3 class="w4-panel__title">AI 诊断建议</h3>
          <ElTooltip content="以下为 AI 根据病历与检查结果生成的诊断参考，供医生采纳或鉴别。" placement="top">
            <ElIcon class="w4-panel__help" aria-label="说明"><QuestionFilled /></ElIcon>
          </ElTooltip>
        </div>
        <p class="w4-panel__subtitle">以下为历史 AI 推荐，供医生采纳或鉴别</p>
      </div>
    </div>

    <ElEmpty v-if="!hasContent" description="暂无 W4 输出，可运行 W4 获取疾病诊断建议。" />

    <template v-else>
      <ElAlert
        v-if="showFallback"
        class="w4-panel__alert"
        type="warning"
        :closable="false"
        show-icon
        title="疾病库未匹配到候选，以下为 AI 兜底建议"
        :description="liveOutput?.searchAdvice || '请手动搜索疾病库并确认诊断。'"
      />

      <div v-if="liveOutput?.clinicalSummaryForDoctor" class="w4-panel__summary">
        <strong>临床摘要</strong>
        <p>{{ liveOutput.clinicalSummaryForDoctor }}</p>
      </div>

      <div v-if="liveOutput?.warningSigns?.length" class="w4-panel__warnings">
        <ElAlert
          v-for="(sign, idx) in liveOutput.warningSigns"
          :key="`warn-${idx}`"
          type="error"
          :closable="false"
          show-icon
          :title="sign"
        />
      </div>

      <article v-if="primarySuggestion" class="w4-primary-card">
        <span class="w4-primary-card__badge">首选诊断（AI 推荐）</span>
        <div class="w4-primary-card__layout">
          <div class="w4-primary-card__main">
            <div class="w4-primary-card__header">
              <span class="w4-primary-card__rank">#{{ primarySuggestion.sortOrder ?? 1 }}</span>
              <h4 class="w4-primary-card__name">{{ suggestionDisplayName(primarySuggestion) }}</h4>
              <ElTag v-if="primarySuggestion.riskLevel" size="small" :type="w4RiskTone(primarySuggestion.riskLevel)" class="w4-primary-card__risk">
                {{ w4RiskLabel(primarySuggestion.riskLevel) }}
              </ElTag>
            </div>
            <div class="w4-primary-card__meta">
              <span v-if="primarySuggestion.recommendIcd">ICD：{{ primarySuggestion.recommendIcd }}</span>
              <span>概率：{{ formatW4Probability(primarySuggestion.probability) }}</span>
            </div>
            <div class="w4-primary-card__columns">
              <div class="w4-primary-card__col">
                <strong class="w4-primary-card__col-title">主要依据</strong>
                <ul v-if="basisItems(primarySuggestion).length" class="w4-primary-card__list">
                  <li v-for="(line, idx) in basisItems(primarySuggestion)" :key="`basis-${idx}`">{{ line }}</li>
                </ul>
                <p v-else class="w4-primary-card__empty">暂无依据说明</p>
              </div>
              <div class="w4-primary-card__col">
                <strong class="w4-primary-card__col-title">治疗建议</strong>
                <ul v-if="treatmentItems(primarySuggestion).length" class="w4-primary-card__list">
                  <li v-for="(line, idx) in treatmentItems(primarySuggestion)" :key="`treat-${idx}`">{{ line }}</li>
                </ul>
                <p v-else class="w4-primary-card__empty">暂无治疗建议</p>
              </div>
            </div>
          </div>
          <aside class="w4-primary-card__cta">
            <p class="w4-primary-card__cta-text">
              <ElIcon class="w4-primary-card__star"><StarFilled /></ElIcon>
              建议优先考虑。如临床不符，可选择下方鉴别诊断
            </p>
            <ElButton type="primary" @click="emit('adoptPrimary', primarySuggestion)">采纳为主诊断</ElButton>
          </aside>
        </div>
      </article>

      <div v-if="secondarySuggestions.length" class="w4-secondary-section">
        <p v-if="hasSimilarSecondary" class="w4-secondary-section__note">#2 与主诊断高度相近，建议二选一</p>
        <div class="w4-secondary-grid">
          <article
            v-for="(item, idx) in secondarySuggestions"
            :key="`secondary-${item.id ?? idx}`"
            class="w4-secondary-card"
          >
            <div class="w4-secondary-card__header">
              <span class="w4-secondary-card__rank">#{{ item.sortOrder ?? idx + 2 }}</span>
              <h4 class="w4-secondary-card__name">{{ suggestionDisplayName(item) }}</h4>
              <ElTag v-if="item.riskLevel" size="small" :type="w4RiskTone(item.riskLevel)">
                {{ w4RiskLabel(item.riskLevel) }}
              </ElTag>
            </div>
            <div class="w4-secondary-card__meta">
              <span v-if="item.recommendIcd">ICD：{{ item.recommendIcd }}</span>
              <span>概率：{{ formatW4Probability(item.probability) }}</span>
            </div>
            <div
              v-if="isSimilarToPrimary(primarySuggestion, item)"
              class="w4-secondary-card__similar"
            >
              与主诊断高度相近，临床意义相似，建议二选一。
            </div>
            <div class="w4-secondary-card__columns">
              <div class="w4-secondary-card__col">
                <strong>主要依据</strong>
                <ul v-if="basisItems(item).length" class="w4-secondary-card__list">
                  <li v-for="(line, bIdx) in basisItems(item)" :key="`s-basis-${bIdx}`">{{ line }}</li>
                </ul>
                <p v-else class="w4-secondary-card__empty">—</p>
              </div>
              <div class="w4-secondary-card__col">
                <strong>治疗建议</strong>
                <ul v-if="treatmentItems(item).length" class="w4-secondary-card__list">
                  <li v-for="(line, tIdx) in treatmentItems(item)" :key="`s-treat-${tIdx}`">{{ line }}</li>
                </ul>
                <p v-else class="w4-secondary-card__empty">—</p>
              </div>
            </div>
            <ElButton class="w4-secondary-card__action" @click="emit('adoptDifferential', item)">加入鉴别诊断</ElButton>
          </article>
        </div>
      </div>

      <div v-if="showFallback && liveOutput?.fallbackSuggestions?.length" class="w4-fallback-list">
        <p class="w4-fallback-list__title">兜底建议</p>
        <article
          v-for="(item, idx) in liveOutput.fallbackSuggestions"
          :key="`fb-${idx}`"
          class="w4-secondary-card w4-secondary-card--fallback"
        >
          <div class="w4-secondary-card__header">
            <h4 class="w4-secondary-card__name">{{ suggestionDisplayName(item) }}</h4>
            <ElTag size="small" type="warning">兜底</ElTag>
          </div>
          <div class="w4-secondary-card__meta">
            <span v-if="item.estimatedIcdPrefix">ICD 前缀：{{ item.estimatedIcdPrefix }}</span>
            <span>概率：{{ formatW4Probability(item.probability) }}</span>
          </div>
          <p v-if="item.note" class="w4-secondary-card__note">{{ item.note }}</p>
          <ElButton class="w4-secondary-card__action" @click="emit('adoptPrimary', item)">采纳为主诊断</ElButton>
        </article>
      </div>

      <div v-if="liveOutput?.differentialDiagnosis?.length" class="w4-panel__ddx-block">
        <strong>AI 鉴别提示</strong>
        <ul class="w4-panel__ddx">
          <li v-for="(item, idx) in liveOutput.differentialDiagnosis" :key="`ddx-${idx}`">
            <span>{{ item.diagnosisName || '-' }}</span>
            <span v-if="item.reason" class="w4-panel__ddx-reason"> — {{ item.reason }}</span>
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.w4-panel {
  margin-block-end: var(--space-4);
}

.w4-panel__head {
  margin-block-end: var(--space-4);
}

.w4-panel__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.w4-panel__title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.w4-panel__help {
  color: var(--color-text-soft);
  cursor: help;
}

.w4-panel__subtitle {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.w4-panel__alert {
  margin-block-end: var(--space-4);
}

.w4-panel__summary {
  margin-block-end: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.w4-panel__summary p {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.w4-panel__warnings {
  display: grid;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.w4-primary-card {
  position: relative;
  margin-block-end: var(--space-4);
  padding: var(--space-5);
  border: 2px solid rgba(31, 140, 255, 0.35);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 251, 255, 0.92));
  box-shadow: var(--shadow-sm);
}

.w4-primary-card__badge {
  position: absolute;
  inset-block-start: 0;
  inset-inline-start: var(--space-4);
  transform: translateY(-50%);
  padding: 4px 12px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  background: var(--color-primary);
}

.w4-primary-card__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: var(--space-5);
  align-items: start;
}

.w4-primary-card__header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.w4-primary-card__rank {
  color: var(--color-primary-strong);
  font-size: 28px;
  font-weight: 800;
  line-height: 1;
}

.w4-primary-card__name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}

.w4-primary-card__risk {
  flex-shrink: 0;
}

.w4-primary-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  font-size: 13px;
}

.w4-primary-card__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-block-start: var(--space-4);
}

.w4-primary-card__col-title {
  display: block;
  margin-block-end: var(--space-2);
  font-size: 14px;
}

.w4-primary-card__list {
  margin: 0;
  padding-inline-start: 1.1rem;
  color: var(--color-text-muted);
  line-height: 1.75;
  font-size: 13px;
}

.w4-primary-card__empty {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 13px;
}

.w4-primary-card__cta {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(31, 140, 255, 0.06);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.w4-primary-card__cta-text {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.w4-primary-card__star {
  flex-shrink: 0;
  margin-block-start: 2px;
  color: var(--color-warning);
}

.w4-secondary-section {
  margin-block-end: var(--space-4);
}

.w4-secondary-section__note {
  margin: 0 0 var(--space-3);
  color: var(--color-warning-strong);
  font-size: 13px;
  text-align: end;
}

.w4-secondary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w4-secondary-card {
  display: flex;
  flex-direction: column;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
}

.w4-secondary-card--fallback {
  border-color: rgba(245, 159, 0, 0.35);
}

.w4-secondary-card__header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.w4-secondary-card__rank {
  color: var(--color-primary);
  font-size: 18px;
  font-weight: 800;
}

.w4-secondary-card__name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.w4-secondary-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  font-size: 12px;
}

.w4-secondary-card__similar {
  margin-block-start: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--color-warning-strong);
  font-size: 12px;
  line-height: 1.6;
  background: var(--color-warning-soft);
}

.w4-secondary-card__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-block-start: var(--space-3);
  flex: 1;
}

.w4-secondary-card__col strong {
  display: block;
  margin-block-end: var(--space-1);
  font-size: 13px;
}

.w4-secondary-card__list {
  margin: 0;
  padding-inline-start: 1rem;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.65;
}

.w4-secondary-card__empty {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.w4-secondary-card__note {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.w4-secondary-card__action {
  margin-block-start: var(--space-3);
  width: 100%;
}

.w4-fallback-list {
  display: grid;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.w4-fallback-list__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.w4-panel__ddx-block {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.w4-panel__ddx {
  margin: var(--space-2) 0 0;
  padding-inline-start: 1.2rem;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w4-panel__ddx-reason {
  color: var(--color-text-soft);
}

@media (max-width: 960px) {
  .w4-primary-card__layout {
    grid-template-columns: 1fr;
  }

  .w4-primary-card__columns,
  .w4-secondary-grid,
  .w4-secondary-card__columns {
    grid-template-columns: 1fr;
  }
}
</style>
