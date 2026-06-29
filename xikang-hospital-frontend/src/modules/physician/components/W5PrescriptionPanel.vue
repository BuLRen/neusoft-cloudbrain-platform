<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElIcon, ElTag, ElTooltip } from 'element-plus'
import {
  CircleCheck,
  Document,
  QuestionFilled,
  ShoppingCart,
  View,
  WarningFilled,
} from '@element-plus/icons-vue'
import { physicianApi, type Drug, type W5Output, type W5Suggestion } from '@/shared/api/modules/physician'
import PhysicianDrugDetailDialog from './PhysicianDrugDetailDialog.vue'
import {
  displayDrugName,
  formatW5Confidence,
  formatW5StockLabel,
  hasW5PanelContent,
  isW5LowStock,
  isW5OutOfStock,
  sortW5Suggestions,
  w5StatusLabel,
} from '@/shared/types/w5Result'

interface LiveStockInfo {
  stockQuantity: number
  drugUnit: string
  lowStockThreshold: number
}

const props = defineProps<{
  liveOutput?: W5Output | null
  savedSuggestions?: W5Suggestion[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  adopt: [item: W5Suggestion]
}>()

const liveStockByDrugId = ref<Record<number, LiveStockInfo>>({})
const drugDetailsById = ref<Record<number, Drug>>({})
const stockLoading = ref(false)
const detailVisible = ref(false)
const detailDrugId = ref<number>()
const detailInitialDrug = ref<Drug | null>(null)

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

const showGeneratedBadge = computed(() => hasContent.value && status.value !== 'fallback')

function resolveStock(item: W5Suggestion): LiveStockInfo | undefined {
  if (item.drugId == null) return undefined
  const live = liveStockByDrugId.value[item.drugId]
  if (live) return live
  if (item.stockQuantity != null) {
    return {
      stockQuantity: item.stockQuantity,
      drugUnit: item.drugUnit || '盒',
      lowStockThreshold: item.lowStockThreshold ?? 20,
    }
  }
  return undefined
}

function stockLabel(item: W5Suggestion): string {
  const stock = resolveStock(item)
  if (!stock) return '加载中…'
  return formatW5StockLabel(stock.stockQuantity, stock.drugUnit)
}

function canAdopt(item: W5Suggestion): boolean {
  if (props.disabled || !item.drugId) return false
  const stock = resolveStock(item)
  if (!stock) return true
  return stock.stockQuantity > 0
}

function openDrugDetail(item: W5Suggestion) {
  if (!item.drugId) return
  detailDrugId.value = item.drugId
  detailInitialDrug.value = drugDetailsById.value[item.drugId] ?? null
  detailVisible.value = true
}

async function refreshLiveStock(suggestions: W5Suggestion[]) {
  const drugIds = [...new Set(suggestions.map((s) => s.drugId).filter((id): id is number => id != null))]
  if (!drugIds.length) {
    liveStockByDrugId.value = {}
    drugDetailsById.value = {}
    return
  }

  stockLoading.value = true
  try {
    const entries = await Promise.all(
      drugIds.map(async (drugId) => {
        try {
          const drug = await physicianApi.drug(drugId)
          return [
            drugId,
            {
              stock: {
                stockQuantity: drug.stockQuantity ?? 0,
                drugUnit: drug.drugUnit || '盒',
                lowStockThreshold: drug.lowStockThreshold ?? 20,
              },
              drug,
            },
          ] as const
        } catch {
          return [
            drugId,
            {
              stock: { stockQuantity: 0, drugUnit: '盒', lowStockThreshold: 20 },
              drug: null,
            },
          ] as const
        }
      }),
    )
    liveStockByDrugId.value = Object.fromEntries(entries.map(([id, data]) => [id, data.stock]))
    drugDetailsById.value = Object.fromEntries(
      entries.filter(([, data]) => data.drug != null).map(([id, data]) => [id, data.drug!]),
    )
  } finally {
    stockLoading.value = false
  }
}

watch(
  displaySuggestions,
  (list) => {
    void refreshLiveStock(list)
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <section class="w5-panel">
    <div class="w5-panel__head">
      <div class="w5-panel__title-wrap">
        <div class="w5-panel__title-row">
          <h4 class="w5-panel__title">AI 用药推荐</h4>
          <ElTooltip content="根据确诊病名与病历上下文推荐药品，供医生采纳后加入处方篮。库存为查询时实时数据。" placement="top">
            <ElIcon class="w5-panel__help" aria-label="说明"><QuestionFilled /></ElIcon>
          </ElTooltip>
        </div>
        <p class="w5-panel__subtitle">{{ w5StatusLabel(liveOutput?.status) }}</p>
      </div>
      <span v-if="showGeneratedBadge" class="w5-panel__status">
        <ElIcon><CircleCheck /></ElIcon>
        推荐建议已生成
      </span>
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
        <div class="w5-panel__summary-head">
          <ElIcon class="w5-panel__summary-icon" aria-hidden="true"><Document /></ElIcon>
          <strong>用药摘要</strong>
        </div>
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
          <div class="w5-card__identity">
            <button
              v-if="item.drugId"
              type="button"
              class="w5-card__name-btn"
              @click="openDrugDetail(item)"
            >
              <h5 class="w5-card__name">{{ displayDrugName(item) }}</h5>
            </button>
            <h5 v-else class="w5-card__name">{{ displayDrugName(item) }}</h5>
            <button
              v-if="item.drugCode && item.drugId"
              type="button"
              class="w5-card__meta-btn"
              @click="openDrugDetail(item)"
            >
              {{ item.drugCode }} · 查看详情
            </button>
            <p v-else-if="item.drugCode" class="w5-card__meta">{{ item.drugCode }}</p>
          </div>
          <div class="w5-card__tags">
            <ElTag
              v-if="resolveStock(item) && isW5OutOfStock(resolveStock(item)!.stockQuantity)"
              type="danger"
              size="small"
              effect="light"
            >
              缺货
            </ElTag>
            <ElTag
              v-else-if="resolveStock(item) && isW5LowStock(resolveStock(item)!.stockQuantity, resolveStock(item)!.lowStockThreshold)"
              type="warning"
              size="small"
              effect="light"
            >
              低库存
            </ElTag>
            <ElTag v-if="item.confidence != null" class="w5-card__confidence" size="small" effect="light">
              置信度 {{ formatW5Confidence(item.confidence) }}
            </ElTag>
          </div>
        </div>

        <dl class="w5-card__details">
          <div class="w5-card__detail" :class="{ 'is-loading': stockLoading && !resolveStock(item) }">
            <dt>可用库存</dt>
            <dd>{{ stockLabel(item) }}</dd>
          </div>
          <div v-if="item.recommendUsage" class="w5-card__detail">
            <dt>用法</dt>
            <dd>{{ item.recommendUsage }}</dd>
          </div>
          <div v-if="item.recommendQuantity" class="w5-card__detail">
            <dt>数量</dt>
            <dd>{{ item.recommendQuantity }}</dd>
          </div>
          <div v-if="item.recommendationBasis" class="w5-card__detail">
            <dt>理由</dt>
            <dd>{{ item.recommendationBasis }}</dd>
          </div>
        </dl>

        <p v-if="item.cautionNotes" class="w5-card__caution">
          <ElIcon aria-hidden="true"><WarningFilled /></ElIcon>
          <span>{{ item.cautionNotes }}</span>
        </p>

        <div class="w5-card__actions">
          <ElButton
            v-if="item.drugId"
            class="w5-card__detail"
            @click="openDrugDetail(item)"
          >
            <ElIcon><View /></ElIcon>
            查看详情
          </ElButton>
          <ElTooltip
            v-if="resolveStock(item) && isW5OutOfStock(resolveStock(item)!.stockQuantity)"
            content="当前无库存，无法采纳"
            placement="top"
          >
            <ElButton class="w5-card__adopt" type="primary" disabled>缺货</ElButton>
          </ElTooltip>
          <ElButton
            v-else
            class="w5-card__adopt"
            type="primary"
            :disabled="!canAdopt(item)"
            :loading="stockLoading && !resolveStock(item)"
            @click="emit('adopt', item)"
          >
            <ElIcon><ShoppingCart /></ElIcon>
            采纳到处方篮
          </ElButton>
        </div>
      </article>

      <article
        v-for="(item, index) in fallbackItems"
        :key="`w5-fb-${index}`"
        class="w5-card w5-card--fallback"
      >
        <h5 class="w5-card__name">{{ displayDrugName(item) }}</h5>
        <dl class="w5-card__details">
          <div v-if="item.recommendUsage" class="w5-card__detail">
            <dt>用法</dt>
            <dd>{{ item.recommendUsage }}</dd>
          </div>
          <div v-if="item.recommendationBasis" class="w5-card__detail">
            <dt>理由</dt>
            <dd>{{ item.recommendationBasis }}</dd>
          </div>
        </dl>
        <p v-if="item.note" class="w5-card__caution">
          <ElIcon aria-hidden="true"><WarningFilled /></ElIcon>
          <span>{{ item.note }}</span>
        </p>
      </article>
    </template>

    <PhysicianDrugDetailDialog
      v-model="detailVisible"
      :drug-id="detailDrugId"
      :initial-drug="detailInitialDrug"
    />
  </section>
</template>

<style scoped>
.w5-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.w5-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.w5-panel__title-wrap {
  min-width: 0;
}

.w5-panel__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.w5-panel__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.w5-panel__subtitle {
  margin: var(--space-1) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.w5-panel__help {
  color: var(--color-text-soft);
  cursor: help;
}

.w5-panel__status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  color: var(--color-success);
  font-size: 12px;
  font-weight: 600;
  background: rgba(32, 180, 134, 0.1);
}

.w5-panel__summary {
  padding: var(--space-4);
  border-radius: var(--radius-sm);
  background: rgba(31, 140, 255, 0.08);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.w5-panel__summary-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
  color: var(--color-primary-strong);
  font-size: 13px;
}

.w5-panel__summary-icon {
  font-size: 16px;
}

.w5-panel__summary p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.w5-panel__warnings,
.w5-panel__alert {
  margin-bottom: var(--space-2);
}

.w5-card {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: #fff;
}

.w5-card--fallback {
  border-style: dashed;
}

.w5-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
  margin-block-end: var(--space-3);
}

.w5-card__identity {
  min-width: 0;
}

.w5-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  justify-content: flex-end;
}

.w5-card__confidence {
  --el-tag-bg-color: rgba(32, 180, 134, 0.1);
  --el-tag-border-color: rgba(32, 180, 134, 0.22);
  --el-tag-text-color: var(--color-success);
}

.w5-card__name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.w5-card__name-btn,
.w5-card__meta-btn {
  display: block;
  padding: 0;
  border: none;
  background: none;
  text-align: start;
  cursor: pointer;
}

.w5-card__name-btn:hover .w5-card__name,
.w5-card__meta-btn:hover {
  color: var(--color-primary-strong);
}

.w5-card__meta {
  margin: var(--space-1) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.w5-card__meta-btn {
  margin: var(--space-1) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
}

.w5-card__details {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.w5-card__detail {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--space-2);
  font-size: 13px;
  line-height: 1.6;
}

.w5-card__detail dt {
  margin: 0;
  color: var(--color-text-soft);
  font-weight: 500;
}

.w5-card__detail dd {
  margin: 0;
  color: var(--color-text);
}

.w5-card__detail.is-loading dd {
  color: var(--color-text-soft);
}

.w5-card__caution {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding: var(--space-2) var(--space-3);
  border-radius: 8px;
  color: var(--color-warning-strong);
  font-size: 12px;
  line-height: 1.6;
  background: var(--color-warning-soft);
}

.w5-card__caution .el-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  font-size: 14px;
}

.w5-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.w5-card__detail,
.w5-card__adopt {
  width: 100%;
  margin: 0;
}
</style>
