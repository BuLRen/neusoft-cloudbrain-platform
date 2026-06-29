<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElIcon, ElTag, ElTooltip } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import type { W5Output, W5Suggestion } from '@/shared/api/modules/physician'
import {
  displayDrugName,
  formatW5Confidence,
  hasW5PanelContent,
  sortW5Suggestions,
  w5StatusLabel,
} from '@/shared/types/w5Result'

const props = defineProps<{
  liveOutput?: W5Output | null
  savedSuggestions?: W5Suggestion[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  adopt: [item: W5Suggestion]
}>()

const hasContent = computed(() => hasW5PanelContent(props.liveOutput, props.savedSuggestions))

const status = computed(() => props.liveOutput?.status)

const displaySuggestions = computed(() => {
  if (props.liveOutput && status.value !== 'fallback') {
    return sortW5Suggestions(props.liveOutput.suggestions)
  }
  return sortW5Suggestions(props.savedSuggestions)
})

const showFallback = computed(() => props.liveOutput?.status === 'fallback')

const fallbackItems = computed(() => props.liveOutput?.fallbackSuggestions ?? [])
</script>

<template>
  <section class="w5-panel">
    <div class="w5-panel__head">
      <div>
        <div class="w5-panel__title-row">
          <h3 class="w5-panel__title">AI 用药推荐</h3>
          <ElTooltip content="根据确诊病名与病历上下文推荐药品，供医生采纳后加入处方篮。" placement="top">
            <ElIcon class="w5-panel__help" aria-label="说明"><QuestionFilled /></ElIcon>
          </ElTooltip>
        </div>
        <p class="w5-panel__subtitle">{{ w5StatusLabel(liveOutput?.status) }}</p>
      </div>
    </div>

    <ElEmpty v-if="!hasContent" description="暂无 W5 输出，可运行 W5 获取用药建议。" />

    <template v-else>
      <ElAlert
        v-if="showFallback"
        class="w5-panel__alert"
        type="warning"
        :closable="false"
        show-icon
        title="药品库未匹配到候选，以下为 AI 兜底建议"
        :description="liveOutput?.searchAdvice || '请手动搜索药品库并选药。'"
      />

      <div v-if="liveOutput?.clinicalSummaryForDoctor" class="w5-panel__summary">
        <strong>用药摘要</strong>
        <p>{{ liveOutput.clinicalSummaryForDoctor }}</p>
      </div>

      <div v-if="liveOutput?.allergyWarnings?.length" class="w5-panel__warnings">
        <ElAlert
          v-for="(warning, idx) in liveOutput.allergyWarnings"
          :key="`allergy-${idx}`"
          type="error"
          :closable="false"
          show-icon
          :title="warning"
        />
      </div>

      <article
        v-for="(item, index) in displaySuggestions"
        :key="`w5-${item.drugId ?? item.id ?? index}`"
        class="w5-card"
      >
        <div class="w5-card__head">
          <div>
            <h4 class="w5-card__name">{{ displayDrugName(item) }}</h4>
            <p v-if="item.drugCode" class="w5-card__meta">{{ item.drugCode }}</p>
          </div>
          <ElTag v-if="item.confidence != null" type="success" size="small">
            置信度 {{ formatW5Confidence(item.confidence) }}
          </ElTag>
        </div>
        <p v-if="item.recommendUsage" class="w5-card__line"><strong>用法：</strong>{{ item.recommendUsage }}</p>
        <p v-if="item.recommendQuantity" class="w5-card__line"><strong>数量：</strong>{{ item.recommendQuantity }}</p>
        <p v-if="item.recommendationBasis" class="w5-card__line"><strong>理由：</strong>{{ item.recommendationBasis }}</p>
        <p v-if="item.cautionNotes" class="w5-card__caution">{{ item.cautionNotes }}</p>
        <div class="w5-card__actions">
          <ElButton
            type="primary"
            size="small"
            :disabled="disabled || !item.drugId"
            @click="emit('adopt', item)"
          >
            采纳到处方篮
          </ElButton>
        </div>
      </article>

      <article
        v-for="(item, index) in fallbackItems"
        :key="`w5-fb-${index}`"
        class="w5-card w5-card--fallback"
      >
        <h4 class="w5-card__name">{{ displayDrugName(item) }}</h4>
        <p v-if="item.recommendUsage" class="w5-card__line"><strong>用法：</strong>{{ item.recommendUsage }}</p>
        <p v-if="item.recommendationBasis" class="w5-card__line"><strong>理由：</strong>{{ item.recommendationBasis }}</p>
        <p v-if="item.note" class="w5-card__caution">{{ item.note }}</p>
      </article>
    </template>
  </section>
</template>

<style scoped>
.w5-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.w5-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.w5-panel__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.w5-panel__title {
  margin: 0;
  font-size: 1rem;
}

.w5-panel__subtitle {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.w5-panel__help {
  color: var(--color-text-muted);
  cursor: help;
}

.w5-panel__summary {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-bg-muted, #f5f7fa);
}

.w5-panel__summary p {
  margin: var(--space-1) 0 0;
}

.w5-panel__warnings,
.w5-panel__alert {
  margin-bottom: var(--space-2);
}

.w5-card {
  padding: var(--space-3);
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: var(--radius-md);
  background: #fff;
}

.w5-card--fallback {
  border-style: dashed;
}

.w5-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.w5-card__name {
  margin: 0;
  font-size: 0.95rem;
}

.w5-card__meta {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.w5-card__line {
  margin: 0 0 var(--space-1);
  font-size: 0.875rem;
  line-height: 1.5;
}

.w5-card__caution {
  margin: var(--space-2) 0 0;
  font-size: 0.8rem;
  color: var(--el-color-warning-dark-2, #b88230);
}

.w5-card__actions {
  margin-top: var(--space-2);
  display: flex;
  justify-content: flex-end;
}
</style>
