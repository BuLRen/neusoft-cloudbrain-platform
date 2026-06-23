<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  CRITICAL_EXPIRY_DAYS,
  NEAR_EXPIRY_DAYS,
} from '@/shared/constants/pharmacy'
import type { DrugOption, DrugStock } from '@/shared/types/pharmacy'
import InboundDialog from './InboundDialog.vue'
import StockCheckDialog from './StockCheckDialog.vue'
import DrugDetailDialog from './DrugDetailDialog.vue'

const props = defineProps<{
  modelValue: boolean
  drug: DrugOption | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const loading = ref(false)
const rows = ref<DrugStock[]>([])

// 子弹窗
const inboundVisible = ref(false)
const checkVisible = ref(false)
const detailVisible = ref(false)

const isLowStock = computed(() => {
  if (!props.drug) return false
  return (props.drug.stockQuantity ?? 0) <= (props.drug.lowStockThreshold ?? 0)
})

async function load() {
  if (!props.drug) return
  loading.value = true
  try {
    rows.value = await pharmacyApi.inventory(props.drug.id)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.drug?.id],
  ([visible]) => {
    if (visible && props.drug) void load()
  },
)

function expiryTone(date?: string): 'danger' | 'warning' | '' {
  if (!date) return ''
  const days = Math.ceil((new Date(date).getTime() - Date.now()) / 86400000)
  if (days <= CRITICAL_EXPIRY_DAYS) return 'danger'
  if (days <= NEAR_EXPIRY_DAYS) return 'warning'
  return ''
}

function rowClass({ row }: { row: DrugStock }) {
  const tone = expiryTone(row.expiryDate)
  if (tone === 'danger') return 'row-critical'
  if (tone === 'warning') return 'row-warning'
  return ''
}

function openInbound() {
  inboundVisible.value = true
}
function openCheck() {
  checkVisible.value = true
}
function openDetail() {
  detailVisible.value = true
}

function onSubSuccess() {
  void load()
  emit('changed')
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="drug ? `${drug.name} · 库存批次` : '库存批次'"
    width="780px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="drug">
      <!-- 顶部：药品概要 -->
      <div class="summary">
        <div class="summary__main">
          <div class="summary__name">
            <strong>{{ drug.name }}</strong>
            <ElTag v-if="drug.category" size="small" effect="plain">{{ drug.category }}</ElTag>
            <ElTag v-if="drug.dosageForm" size="small" type="info" effect="plain">
              {{ drug.dosageForm }}
            </ElTag>
          </div>
          <div class="summary__meta">
            <span>规格：{{ drug.specification || '-' }}</span>
            <span>单价：{{ drug.price ?? '-' }} 元</span>
            <span>预警阈值：{{ drug.lowStockThreshold ?? '-' }}</span>
          </div>
        </div>
        <div class="summary__stock" :class="{ 'is-low': isLowStock }">
          <div class="summary__stock-num">{{ drug.stockQuantity ?? 0 }}</div>
          <div class="summary__stock-label">当前总库存 / {{ drug.unit || '件' }}</div>
        </div>
      </div>

      <!-- 批次表 -->
      <div class="batches-section">
        <div class="section-title">
          <h4>库存批次（按失效日期升序）</h4>
          <span v-if="rows.length" class="muted">{{ rows.length }} 个批次</span>
        </div>
        <ElEmpty v-if="!loading && rows.length === 0" description="该药品暂无批次，可通过入库新增" />
        <ElTable
          v-else
          v-loading="loading"
          :data="rows"
          :row-class-name="rowClass"
          size="small"
        >
          <ElTableColumn prop="batchNumber" label="批次号" min-width="140" />
          <ElTableColumn prop="quantity" label="数量" min-width="80" />
          <ElTableColumn prop="location" label="货位" min-width="110" />
          <ElTableColumn prop="productionDate" label="生产日期" min-width="110" />
          <ElTableColumn label="失效日期" min-width="140">
            <template #default="{ row }">
              <span>{{ row.expiryDate }}</span>
              <ElTag
                v-if="expiryTone(row.expiryDate) === 'danger'"
                type="danger"
                size="small"
                effect="dark"
                class="ml"
              >≤ {{ CRITICAL_EXPIRY_DAYS }} 天</ElTag>
              <ElTag
                v-else-if="expiryTone(row.expiryDate) === 'warning'"
                type="warning"
                size="small"
                effect="dark"
                class="ml"
              >≤ {{ NEAR_EXPIRY_DAYS }} 天</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </template>

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">关闭</ElButton>
      <ElButton @click="openDetail">查看完整详情</ElButton>
      <ElButton @click="openCheck">库存盘点</ElButton>
      <ElButton type="primary" @click="openInbound">入库</ElButton>
    </template>

    <!-- 子弹窗 -->
    <InboundDialog
      v-model="inboundVisible"
      :drug="drug"
      @success="onSubSuccess"
    />
    <StockCheckDialog
      v-model="checkVisible"
      :drug="drug"
      @success="onSubSuccess"
    />
    <DrugDetailDialog
      v-model="detailVisible"
      :drug="drug"
      @check="openCheck"
    />
  </ElDialog>
</template>

<style scoped>
.summary {
  display: flex;
  align-items: stretch;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-soft, var(--color-surface));
  margin-block-end: var(--space-4);
}

.summary__main {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.summary__name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 16px;
}

.summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.summary__stock {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2) var(--space-4);
  border-left: 1px dashed var(--color-border);
  min-width: 120px;
}

.summary__stock.is-low .summary__stock-num {
  color: var(--color-danger, #f56c6c);
}

.summary__stock-num {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1;
}

.summary__stock-label {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-block-start: 4px;
  text-align: center;
}

.batches-section {
  margin-block-start: var(--space-2);
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-block-end: var(--space-3);
}

.section-title h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.muted {
  color: var(--color-text-muted);
  font-size: 12px;
}

.ml {
  margin-left: var(--space-2);
}

:deep(.row-warning) {
  background: var(--color-warning-bg, #fdf6ec);
}

:deep(.row-critical) {
  background: var(--color-danger-bg, #fef0f0);
}
</style>
