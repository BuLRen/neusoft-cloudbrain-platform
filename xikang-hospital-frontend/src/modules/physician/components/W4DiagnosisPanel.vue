<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElCard, ElEmpty, ElTag } from 'element-plus'
import type { W4FallbackSuggestion, W4Output, W4Suggestion } from '@/shared/api/modules/physician'
import {
  formatW4Probability,
  hasW4PanelContent,
  sortSuggestions,
  suggestionDisplayName,
  w4RiskLabel,
  w4RiskTone,
  w4StatusLabel,
  w4StatusTone,
} from '@/shared/types/w4Result'

const props = defineProps<{
  liveOutput?: W4Output | null
  savedSuggestions?: W4Suggestion[]
}>()

const emit = defineEmits<{
  adopt: [item: W4Suggestion | W4FallbackSuggestion]
}>()

const hasContent = computed(() => hasW4PanelContent(props.liveOutput, props.savedSuggestions))

const status = computed(() => props.liveOutput?.status)

const liveSuggestions = computed(() => sortSuggestions(props.liveOutput?.suggestions))

const showLiveSuggestions = computed(() => Boolean(props.liveOutput && status.value !== 'fallback' && liveSuggestions.value.length))

const showSavedSuggestions = computed(() => !props.liveOutput && Boolean(props.savedSuggestions?.length))

const showFallback = computed(() => props.liveOutput?.status === 'fallback')

const differentialList = computed(() => props.liveOutput?.differentialDiagnosis ?? [])
</script>

<template>
  <section class="w4-panel">
    <div class="w4-panel__head">
      <h3 class="w4-panel__title">W4 AI 诊断参考</h3>
      <ElTag v-if="liveOutput?.status" :type="w4StatusTone(liveOutput.status)" size="small">
        {{ w4StatusLabel(liveOutput.status) }}
      </ElTag>
    </div>

    <p v-if="liveOutput?.modelId" class="w4-panel__meta">
      模型：{{ liveOutput.modelId }}
      <span v-if="liveOutput.workflowRunId"> · Run ID：{{ liveOutput.workflowRunId }}</span>
    </p>

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

      <div v-if="liveOutput?.clinicalSummaryForDoctor" class="w4-panel__block">
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

      <div v-if="showLiveSuggestions" class="w4-panel__list">
        <ElCard v-for="(item, idx) in liveSuggestions" :key="`live-${item.id ?? idx}`" class="w4-panel__card" shadow="never">
          <div class="w4-panel__card-head">
            <strong>#{{ item.sortOrder ?? idx + 1 }} {{ suggestionDisplayName(item) }}</strong>
            <ElTag v-if="item.riskLevel" size="small" :type="w4RiskTone(item.riskLevel)">
              {{ w4RiskLabel(item.riskLevel) }}
            </ElTag>
          </div>
          <p v-if="item.recommendIcd">ICD：{{ item.recommendIcd }}</p>
          <p>概率：{{ formatW4Probability(item.probability) }}</p>
          <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
          <p v-if="item.treatmentDirection">治疗方向：{{ item.treatmentDirection }}</p>
          <ElButton size="small" @click="emit('adopt', item)">采纳到确诊表单</ElButton>
        </ElCard>
      </div>

      <div v-else-if="showSavedSuggestions" class="w4-panel__list">
        <p class="w4-panel__hint">以下为历史 AI 推荐（点击采纳填入下方表单）</p>
        <ElCard
          v-for="(item, idx) in sortSuggestions(savedSuggestions)"
          :key="`saved-${item.id ?? idx}`"
          class="w4-panel__card"
          shadow="never"
        >
          <div class="w4-panel__card-head">
            <strong>#{{ item.sortOrder ?? idx + 1 }} {{ suggestionDisplayName(item) }}</strong>
            <ElTag v-if="item.riskLevel" size="small" :type="w4RiskTone(item.riskLevel)">
              {{ w4RiskLabel(item.riskLevel) }}
            </ElTag>
          </div>
          <p v-if="item.recommendIcd">ICD：{{ item.recommendIcd }}</p>
          <p>概率：{{ formatW4Probability(item.probability) }}</p>
          <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
          <p v-if="item.treatmentDirection">治疗方向：{{ item.treatmentDirection }}</p>
          <ElButton size="small" @click="emit('adopt', item)">采纳到确诊表单</ElButton>
        </ElCard>
      </div>

      <div v-if="showFallback && liveOutput?.fallbackSuggestions?.length" class="w4-panel__list">
        <ElCard
          v-for="(item, idx) in liveOutput.fallbackSuggestions"
          :key="`fb-${idx}`"
          class="w4-panel__card w4-panel__card--fallback"
          shadow="never"
        >
          <div class="w4-panel__card-head">
            <strong>{{ suggestionDisplayName(item) }}</strong>
            <ElTag size="small" type="warning">兜底</ElTag>
          </div>
          <p v-if="item.estimatedIcdPrefix">ICD 前缀：{{ item.estimatedIcdPrefix }}</p>
          <p>概率：{{ formatW4Probability(item.probability) }}</p>
          <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
          <p v-if="item.note">{{ item.note }}</p>
          <ElButton size="small" @click="emit('adopt', item)">采纳到确诊表单</ElButton>
        </ElCard>
      </div>

      <div v-if="differentialList.length" class="w4-panel__block">
        <strong>鉴别诊断</strong>
        <ul class="w4-panel__ddx">
          <li v-for="(item, idx) in differentialList" :key="`ddx-${idx}`">
            <span>{{ item.diagnosisName || '-' }}</span>
            <span v-if="item.reason" class="w4-panel__ddx-reason"> — {{ item.reason }}</span>
          </li>
        </ul>
      </div>
    </template>

    <p class="w4-panel__footer">AI 建议仅供参考，最终诊断由医生确认并保存。</p>
  </section>
</template>

<style scoped>
.w4-panel {
  margin-block-end: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 251, 255, 0.88));
}

.w4-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.w4-panel__title {
  margin: 0;
  font-size: 16px;
}

.w4-panel__meta {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.w4-panel__alert {
  margin-block-start: var(--space-3);
}

.w4-panel__block {
  margin-block-start: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
}

.w4-panel__block p,
.w4-panel__ddx {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w4-panel__warnings {
  display: grid;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.w4-panel__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-block-start: var(--space-3);
}

.w4-panel__hint {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.w4-panel__card {
  border: 1px solid var(--color-border);
}

.w4-panel__card--fallback {
  border-color: rgba(230, 162, 60, 0.35);
}

.w4-panel__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.w4-panel__card p {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 13px;
}

.w4-panel__ddx {
  padding-inline-start: 1.2rem;
}

.w4-panel__ddx-reason {
  color: var(--color-text-muted);
}

.w4-panel__footer {
  margin: var(--space-3) 0 0;
  color: var(--color-ai);
  font-size: 13px;
}
</style>
