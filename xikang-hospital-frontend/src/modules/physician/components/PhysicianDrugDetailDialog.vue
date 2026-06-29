<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { ElDialog, ElIcon, ElTag } from 'element-plus'
import {
  Calendar,
  Coin,
  CollectionTag,
  Document,
  FirstAidKit,
  Grid,
  Key,
  OfficeBuilding,
  Postcard,
  Wallet,
} from '@element-plus/icons-vue'
import { physicianApi, type Drug } from '@/shared/api/modules/physician'

interface DetailField {
  label: string
  value: string
  icon: Component
}

const props = defineProps<{
  modelValue: boolean
  drugId?: number
  initialDrug?: Drug | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const drug = ref<Drug | null>(null)

const detailCache = new Map<number, Drug>()

const displayDrug = computed(() => drug.value)

const detailTags = computed(() => {
  const current = displayDrug.value
  if (!current) return []
  return [current.drugFormat, current.drugDosage, current.drugType].filter((item): item is string => Boolean(item?.trim()))
})

const detailQuickItems = computed(() => {
  const current = displayDrug.value
  if (!current) return []
  return [
    { label: '规格', value: current.drugFormat || '-', icon: Grid },
    { label: '剂型', value: current.drugDosage || '-', icon: FirstAidKit },
    { label: '药品类型', value: current.drugType || '-', icon: CollectionTag },
  ]
})

function stockText(item: Drug): string {
  const qty = item.stockQuantity ?? 0
  const unit = item.drugUnit || '盒'
  if (qty <= 0) return '缺货'
  return `${qty} ${unit}`
}

const detailLeftFields = computed<DetailField[]>(() => {
  const current = displayDrug.value
  if (!current) return []
  return [
    { label: '药品编码', value: current.drugCode, icon: Postcard },
    { label: '规格', value: current.drugFormat || '-', icon: Grid },
    { label: '药品类型', value: current.drugType || '-', icon: CollectionTag },
    { label: '生产企业', value: current.manufacturer || '-', icon: OfficeBuilding },
    { label: '助记码', value: current.mnemonicCode || '-', icon: Key },
    { label: '可用库存', value: stockText(current), icon: Wallet },
  ]
})

const detailRightFields = computed<DetailField[]>(() => {
  const current = displayDrug.value
  if (!current) return []
  return [
    { label: '药品名称', value: current.drugName, icon: Document },
    { label: '剂型', value: current.drugDosage || '-', icon: FirstAidKit },
    { label: '单位', value: current.drugUnit || '-', icon: Wallet },
    { label: '单价', value: `${current.drugPrice} 元`, icon: Coin },
    { label: '录入日期', value: current.creationDate || '-', icon: Calendar },
  ]
})

function close() {
  emit('update:modelValue', false)
}

async function loadDrug(id: number) {
  const cached = detailCache.get(id)
  if (cached) {
    drug.value = cached
    return
  }

  if (props.initialDrug?.id === id) {
    drug.value = props.initialDrug
  }

  loading.value = true
  try {
    const full = await physicianApi.drug(id)
    detailCache.set(id, full)
    drug.value = full
  } catch {
    if (!drug.value) drug.value = props.initialDrug ?? null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.drugId] as const,
  ([visible, id]) => {
    if (visible && id != null) {
      void loadDrug(id)
      return
    }
    if (!visible) {
      drug.value = null
    }
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    class="physician-drug-detail-dialog"
    width="min(860px, 94vw)"
    align-center
    append-to-body
    destroy-on-close
    title="药品详情"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="close"
  >
    <div v-loading="loading" class="drug-detail">
      <header v-if="displayDrug" class="drug-detail__hero">
        <div class="drug-detail__hero-main">
          <h2 class="drug-detail__title">{{ displayDrug.drugName }}</h2>
          <div v-if="detailTags.length" class="drug-detail__tags">
            <ElTag
              v-for="tag in detailTags"
              :key="tag"
              class="drug-detail__tag"
              size="small"
              effect="plain"
              round
            >
              {{ tag }}
            </ElTag>
          </div>
        </div>

        <aside class="drug-detail__summary-card">
          <ul class="drug-detail__quick-list">
            <li v-for="item in detailQuickItems" :key="item.label" class="drug-detail__quick-item">
              <ElIcon class="drug-detail__quick-icon" aria-hidden="true">
                <component :is="item.icon" />
              </ElIcon>
              <span class="drug-detail__quick-label">{{ item.label }}</span>
              <span class="drug-detail__quick-value">{{ item.value }}</span>
            </li>
          </ul>
        </aside>
      </header>

      <div v-if="displayDrug" class="drug-detail__columns">
        <section class="drug-detail__panel">
          <div
            v-for="field in detailLeftFields"
            :key="field.label"
            class="drug-detail__row"
          >
            <ElIcon class="drug-detail__row-icon" aria-hidden="true">
              <component :is="field.icon" />
            </ElIcon>
            <span class="drug-detail__row-label">{{ field.label }}</span>
            <span class="drug-detail__row-value">{{ field.value }}</span>
          </div>
        </section>

        <section class="drug-detail__panel">
          <div
            v-for="field in detailRightFields"
            :key="field.label"
            class="drug-detail__row"
          >
            <ElIcon class="drug-detail__row-icon" aria-hidden="true">
              <component :is="field.icon" />
            </ElIcon>
            <span class="drug-detail__row-label">{{ field.label }}</span>
            <span class="drug-detail__row-value">{{ field.value }}</span>
          </div>
        </section>
      </div>

      <p v-else-if="!loading" class="drug-detail__empty">无法加载药品详情</p>
    </div>
  </ElDialog>
</template>

<style scoped>
.drug-detail {
  min-height: 120px;
}

.drug-detail__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: var(--space-5);
  margin-block-end: var(--space-5);
}

.drug-detail__title {
  margin: 0;
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.03em;
  color: var(--color-primary-strong);
}

.drug-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.drug-detail__tag {
  --el-tag-bg-color: rgba(31, 140, 255, 0.1);
  --el-tag-border-color: rgba(31, 140, 255, 0.2);
  --el-tag-text-color: var(--color-primary-strong);
}

.drug-detail__summary-card {
  display: flex;
  align-items: center;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.drug-detail__quick-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
  min-width: 0;
}

.drug-detail__quick-item {
  display: grid;
  grid-template-columns: 18px minmax(56px, auto) minmax(0, 1fr);
  gap: var(--space-2);
  align-items: center;
  font-size: 12px;
}

.drug-detail__quick-icon {
  color: var(--color-primary);
}

.drug-detail__quick-label {
  color: var(--color-text-soft);
}

.drug-detail__quick-value {
  color: var(--color-text);
  font-weight: 600;
}

.drug-detail__columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.drug-detail__panel {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.drug-detail__row {
  display: grid;
  grid-template-columns: 20px 88px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  padding: var(--space-3) 0;
  border-block-end: 1px solid rgba(70, 111, 160, 0.1);
}

.drug-detail__row:last-child {
  border-block-end: none;
}

.drug-detail__row-icon {
  margin-block-start: 2px;
  color: var(--color-primary);
  font-size: 16px;
}

.drug-detail__row-label {
  color: var(--color-text-soft);
  font-size: 13px;
  line-height: 1.6;
}

.drug-detail__row-value {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  word-break: break-word;
}

.drug-detail__empty {
  margin: 0;
  padding: var(--space-5);
  color: var(--color-text-soft);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 760px) {
  .drug-detail__hero {
    grid-template-columns: 1fr;
  }

  .drug-detail__columns {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.physician-drug-detail-dialog.el-dialog {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 42%);
}

.physician-drug-detail-dialog .el-dialog__header {
  margin-inline: var(--space-5);
  padding-block: var(--space-5) 0;
}

.physician-drug-detail-dialog .el-dialog__body {
  padding: var(--space-4) var(--space-5) var(--space-5);
}
</style>
