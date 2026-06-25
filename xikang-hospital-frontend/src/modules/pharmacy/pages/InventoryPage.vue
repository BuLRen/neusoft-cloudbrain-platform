<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import StockBatchesDialog from '@/modules/pharmacy/components/StockBatchesDialog.vue'
import LossDialog from '@/modules/pharmacy/components/LossDialog.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  DOSAGE_FORMS,
  NEAR_EXPIRY_DAYS,
  PAGE_SIZE_DEFAULT,
} from '@/shared/constants/pharmacy'
import type { DrugOption, ExpiringStockItem } from '@/shared/types/pharmacy'

const router = useRouter()

function goBatchInbound() {
  void router.push('/pharmacy/batch-inbound')
}

// ===== 筛选条件 =====
const drugKeyword = ref('')
const drugDosageForm = ref<string>('')
const drugCategory = ref<string>('')
const stockFilter = ref<'all' | 'low' | 'zero' | 'frozen'>('all')

const categoryOptions = ref<string[]>([])
const drugs = ref<DrugOption[]>([])
const lowStockDrugs = ref<DrugOption[]>([])
const expiringBatches = ref<ExpiringStockItem[]>([])
const loading = ref(false)

// 防抖输入
let searchTimer: ReturnType<typeof setTimeout> | null = null
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void loadDrugCatalog()
  }, 300)
}

// 任何筛选条件变化都触发（输入、下拉变化、状态过滤）
watch([drugKeyword, drugDosageForm, drugCategory, stockFilter], () => {
  drugPage.value = 1
  debouncedSearch()
})

// ===== 预警面板折叠状态 =====
const expandedLow = ref(false)
const expandedExpiring = ref(false)
const lowListRef = ref<HTMLElement | null>(null)
const expiringListRef = ref<HTMLElement | null>(null)

watch(expandedLow, (v) => {
  if (v) nextTick(() => {
    if (lowListRef.value) lowListRef.value.scrollTop = 0
  })
})

watch(expandedExpiring, (v) => {
  if (v) nextTick(() => {
    if (expiringListRef.value) expiringListRef.value.scrollTop = 0
  })
})

// ===== 批次弹窗 =====
const batchesVisible = ref(false)
const selectedDrug = ref<DrugOption | null>(null)

// ===== 报损弹窗 =====
const lossVisible = ref(false)
const lossDrug = ref<DrugOption | null>(null)

// ===== 分页 =====
const drugPage = ref(1)
const drugPageSize = ref(PAGE_SIZE_DEFAULT)

/**
 * 客户端过滤：低库存 / 零库存 / 冻结。
 * "冻结"指药品有冻结批次（通过 batch 数和 drug_info 无法直接判断，
 * 但调用方可在 expiring 拿不到时通过 drug_option 的 stockQuantity 推断；
 * 此处不传"冻结"药品的过滤参数，因为该状态属于批次级，需要另外的接口，
 * 这里先在 UI 上提供一个占位语义）。
 */
const filteredDrugs = computed(() => {
  let list = drugs.value
  if (stockFilter.value === 'low') {
    list = list.filter(
      (d) => (d.stockQuantity ?? 0) > 0 && (d.stockQuantity ?? 0) <= (d.lowStockThreshold ?? 0),
    )
  } else if (stockFilter.value === 'zero') {
    list = list.filter((d) => (d.stockQuantity ?? 0) === 0)
  }
  return list
})

const pagedDrugs = computed(() => {
  const start = (drugPage.value - 1) * drugPageSize.value
  return filteredDrugs.value.slice(start, start + drugPageSize.value)
})

// ===== 总览指标 =====
const overview = computed(() => {
  const totalDrugs = drugs.value.length
  const totalStock = drugs.value.reduce((s, d) => s + (d.stockQuantity ?? 0), 0)
  const lowStockCount = lowStockDrugs.value.length
  const expiringCount = expiringBatches.value.length
  const criticalExpiringCount = expiringBatches.value.filter(
    (b) => (b.daysRemaining ?? 999) <= 7,
  ).length
  return { totalDrugs, totalStock, lowStockCount, expiringCount, criticalExpiringCount }
})

// 低库存预警：零库存 vs 低库存分档
function lowStockTone(d: DrugOption): 'danger' | 'warning' {
  return (d.stockQuantity ?? 0) === 0 ? 'danger' : 'warning'
}

// 近效期预警：紧急(≤7天) vs 关注(8-30天) 分档
function expiryTone(days: number | undefined): 'danger' | 'warning' {
  return (days ?? 999) <= 7 ? 'danger' : 'warning'
}

async function loadDrugCatalog() {
  loading.value = true
  try {
    const params: Record<string, string | undefined> = {
      keyword: drugKeyword.value.trim() || undefined,
      dosageForm: drugDosageForm.value || undefined,
      category: drugCategory.value || undefined,
    }
    const hasFilter = Object.values(params).some((v) => v)
    drugs.value = await pharmacyApi.drugs(hasFilter ? params : undefined)
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  // 并行拉取：低库存、近效期
  const [low, expiring] = await Promise.all([
    pharmacyApi.lowStockDrugs(),
    pharmacyApi.expiringStock(NEAR_EXPIRY_DAYS).catch(() => [] as ExpiringStockItem[]),
  ])
  lowStockDrugs.value = low
  expiringBatches.value = expiring
}

async function loadCategories() {
  categoryOptions.value = await pharmacyApi.categories()
}

function openBatches(drug: DrugOption) {
  selectedDrug.value = drug
  batchesVisible.value = true
}

function openLoss(drug: DrugOption) {
  lossDrug.value = drug
  lossVisible.value = true
}

function onBatchesChanged() {
  void loadDrugCatalog()
  void loadOverview()
}

function onLossSuccess() {
  void loadDrugCatalog()
  void loadOverview()
}

function refreshAll() {
  void loadDrugCatalog()
  void loadOverview()
}

function resetFilters() {
  drugKeyword.value = ''
  drugDosageForm.value = ''
  drugCategory.value = ''
  stockFilter.value = 'all'
}

onMounted(() => {
  void loadCategories()
  void loadOverview()
  // 首次进入：触发一次目录加载
  debouncedSearch()
})
</script>

<template>
  <div class="inventory-page u-page-grid">
    <PageHeader
      title="药库与库存"
      description="药品目录 · 批号库存 · 低库存预警。点击任意药品查看批次明细、入库、盘点或报损。"
      eyebrow="Role B / Pharmacy · ②"
    >
      <template #actions>
        <ElButton @click="resetFilters">清空筛选</ElButton>
        <ElButton @click="refreshAll">刷新数据</ElButton>
        <ElButton type="primary" @click="goBatchInbound">批量入库</ElButton>
      </template>
    </PageHeader>

    <!-- 库存总览卡片（4 个指标）-->
    <div class="overview">
      <div class="overview__item">
        <div class="overview__num">{{ overview.totalDrugs }}</div>
        <div class="overview__label">药品种类</div>
      </div>
      <div class="overview__item">
        <div class="overview__num">{{ overview.totalStock.toLocaleString() }}</div>
        <div class="overview__label">总库存件数</div>
      </div>
      <div class="overview__item" :class="{ 'is-warn': overview.lowStockCount > 0 }">
        <div class="overview__num">{{ overview.lowStockCount }}</div>
        <div class="overview__label">低于阈值</div>
        <div v-if="overview.lowStockCount > 0" class="overview__hint">需补货</div>
      </div>
      <div
        class="overview__item"
        :class="{
          'is-danger': overview.criticalExpiringCount > 0,
          'is-warn': overview.expiringCount > 0 && overview.criticalExpiringCount === 0,
        }"
      >
        <div class="overview__num">{{ overview.expiringCount }}</div>
        <div class="overview__label">≤ {{ NEAR_EXPIRY_DAYS }} 天到期批次</div>
        <div v-if="overview.criticalExpiringCount > 0" class="overview__hint overview__hint--danger">
          紧急 {{ overview.criticalExpiringCount }} 批
        </div>
      </div>
    </div>

    <!-- 预警面板：低库存 + 近效期，左右并排 -->
    <div
      v-if="lowStockDrugs.length > 0 || expiringBatches.length > 0"
      class="alert-panels"
    >
      <!-- 低库存面板 -->
      <GlassCard v-if="lowStockDrugs.length > 0" class="alert-panel">
        <div class="alert-panel__head">
          <div class="alert-panel__title">
            <span class="alert-panel__badge" :class="lowStockDrugs.some(d => (d.stockQuantity ?? 0) === 0) ? 'is-danger' : 'is-warning'">!</span>
            <span>库存预警</span>
          </div>
          <div class="alert-panel__count">{{ lowStockDrugs.length }}</div>
        </div>
        <div ref="lowListRef" class="alert-panel__list">
          <div
            v-for="d in (expandedLow ? lowStockDrugs : lowStockDrugs.slice(0, 3))"
            :key="d.id"
            class="alert-panel__item"
          >
            <div class="alert-panel__strip" :class="lowStockTone(d)"></div>
            <div class="alert-panel__body">
              <div class="alert-panel__name">{{ d.name }}</div>
              <div class="alert-panel__meta">
                <span :class="lowStockTone(d) === 'danger' ? 'text-danger' : 'text-warning'">
                  {{ (d.stockQuantity ?? 0) === 0 ? '缺货' : '低库存' }}
                </span>
                <span class="dot">·</span>
                <span>{{ d.stockQuantity ?? 0 }} {{ d.unit || '件' }}</span>
                <template v-if="(d.stockQuantity ?? 0) > 0 && d.lowStockThreshold">
                  <span class="dot">·</span>
                  <span>阈值 {{ d.lowStockThreshold }}</span>
                </template>
              </div>
            </div>
          </div>
          <button
            v-if="lowStockDrugs.length > 3"
            type="button"
            class="alert-panel__more"
            @click="expandedLow = !expandedLow"
          >
            {{ expandedLow ? '收起' : `+ 另外 ${lowStockDrugs.length - 3} 种` }}
          </button>
        </div>
        <div class="alert-panel__foot">
          <ElButton size="small" @click="stockFilter = 'low'">仅看低库存</ElButton>
        </div>
      </GlassCard>

      <!-- 近效期面板 -->
      <GlassCard v-if="expiringBatches.length > 0" class="alert-panel">
        <div class="alert-panel__head">
          <div class="alert-panel__title">
            <span class="alert-panel__badge" :class="overview.criticalExpiringCount > 0 ? 'is-danger' : 'is-warning'">!</span>
            <span>近效期预警</span>
          </div>
          <div class="alert-panel__count">{{ expiringBatches.length }}</div>
        </div>
        <div ref="expiringListRef" class="alert-panel__list">
          <div
            v-for="b in (expandedExpiring ? expiringBatches : expiringBatches.slice(0, 3))"
            :key="b.id"
            class="alert-panel__item"
          >
            <div class="alert-panel__strip" :class="expiryTone(b.daysRemaining)"></div>
            <div class="alert-panel__body">
              <div class="alert-panel__name">{{ b.drugName || `#${b.drugId}` }}</div>
              <div class="alert-panel__meta">
                <span :class="expiryTone(b.daysRemaining) === 'danger' ? 'text-danger' : 'text-warning'">
                  {{ expiryTone(b.daysRemaining) === 'danger' ? '紧急' : '关注' }}
                </span>
                <span class="dot">·</span>
                <span>剩 {{ b.daysRemaining ?? '-' }} 天</span>
                <span class="dot">·</span>
                <span>批号 {{ b.batchNumber || '-' }}</span>
              </div>
            </div>
          </div>
          <button
            v-if="expiringBatches.length > 3"
            type="button"
            class="alert-panel__more"
            @click="expandedExpiring = !expandedExpiring"
          >
            {{ expandedExpiring ? '收起' : `+ 另外 ${expiringBatches.length - 3} 个` }}
          </button>
        </div>
        <div class="alert-panel__foot">
          <ElButton
            size="small"
            :disabled="!lowStockDrugs[0] && !drugs[0]"
            @click="openLoss(lowStockDrugs[0] ?? drugs[0] ?? null)"
          >
            快速报损
          </ElButton>
        </div>
      </GlassCard>
    </div>

    <!-- 药品目录 -->
    <GlassCard class="catalog-card">
      <!-- 筛选条：统一等高、统一宽度、实时筛选 -->
      <div class="filter-bar">
        <div class="filter-bar__field">
          <ElInput
            v-model="drugKeyword"
            placeholder="药品名称 / 通用名"
            clearable
            size="default"
          />
        </div>
        <div class="filter-bar__field">
          <ElSelect
            v-model="drugDosageForm"
            placeholder="选择剂型"
            clearable
            size="default"
            class="full"
          >
            <ElOption v-for="f in DOSAGE_FORMS" :key="f" :label="f" :value="f" />
          </ElSelect>
        </div>
        <div class="filter-bar__field">
          <ElSelect
            v-model="drugCategory"
            placeholder="选择分类"
            clearable
            filterable
            size="default"
            class="full"
          >
            <ElOption v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </ElSelect>
        </div>
        <div class="filter-bar__field filter-bar__field--status">
          <ElSelect v-model="stockFilter" size="default" class="full">
            <ElOption value="all" label="全部库存" />
            <ElOption value="low" label="仅低库存" />
            <ElOption value="zero" label="仅零库存" />
          </ElSelect>
        </div>
      </div>

      <div class="section-title">
        <h3>药品目录</h3>
        <StatusTag tone="primary">
          {{ filteredDrugs.length }} / {{ drugs.length }} 条 · 点击行查看批次
        </StatusTag>
      </div>

      <ElTable
        v-loading="loading"
        :data="pagedDrugs"
        :row-class-name="({ row }: { row: DrugOption }) => {
          const q = row.stockQuantity ?? 0
          return q === 0 ? 'row-zero' : q <= (row.lowStockThreshold ?? 0) ? 'row-warning' : ''
        }"
        row-key="id"
        empty-text="没有匹配的药品"
        @row-click="openBatches"
      >
        <ElTableColumn prop="name" label="药品名称" min-width="220">
          <template #default="{ row }">
            <div class="drug-name-cell">
              <span class="drug-name">{{ row.name }}</span>
              <div class="drug-tags">
                <ElTag v-if="row.category" size="small" effect="plain">{{ row.category }}</ElTag>
                <ElTag v-if="row.dosageForm" size="small" type="info" effect="plain">
                  {{ row.dosageForm }}
                </ElTag>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="specification" label="规格" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="manufacturer" label="厂家" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="price" label="单价" min-width="90" align="right">
          <template #default="{ row }">¥ {{ row.price ?? '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="库存" min-width="120" align="right">
          <template #default="{ row }">
            <span
              :class="{
                'stock-zero': (row.stockQuantity ?? 0) === 0,
                'stock-low': (row.stockQuantity ?? 0) > 0 && (row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0),
              }"
            >
              {{ row.stockQuantity ?? 0 }}
            </span>
            <span class="unit-suffix">{{ row.unit || '' }}</span>
            <ElTag
              v-if="(row.stockQuantity ?? 0) === 0"
              type="danger"
              size="small"
              effect="plain"
              class="ml"
            >缺货</ElTag>
            <ElTag
              v-else-if="(row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0)"
              type="warning"
              size="small"
              effect="plain"
              class="ml"
            >低库存</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link size="small" type="primary" @click.stop="openBatches(row as DrugOption)">
              批次
            </ElButton>
            <ElButton
              link
              size="small"
              type="danger"
              :disabled="(row.stockQuantity ?? 0) === 0"
              @click.stop="openLoss(row as DrugOption)"
            >
              报损
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElEmpty
        v-if="!loading && drugs.length > 0 && filteredDrugs.length === 0"
        description="当前筛选条件下没有药品，试试调整筛选条件"
        class="empty-hint"
      />

      <div class="pagination-row">
        <ElPagination
          v-model:current-page="drugPage"
          v-model:page-size="drugPageSize"
          :total="filteredDrugs.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          small
          background
        />
      </div>
    </GlassCard>

    <!-- 批次弹窗 -->
    <StockBatchesDialog
      v-model="batchesVisible"
      :drug="selectedDrug"
      @changed="onBatchesChanged"
    />

    <!-- 报损弹窗 -->
    <LossDialog
      v-model="lossVisible"
      :drug="lossDrug"
      @success="onLossSuccess"
    />
  </div>
</template>

<style scoped>
/* ===== 总览卡片 ===== */
.overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-block-end: var(--space-4);
}

.overview__item {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.overview__item:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(31, 140, 255, 0.08);
}

.overview__item.is-warn {
  border-color: var(--color-warning);
  background: linear-gradient(180deg, rgba(245, 159, 0, 0.06) 0%, var(--color-surface) 100%);
}

.overview__item.is-danger {
  border-color: var(--color-danger);
  background: linear-gradient(180deg, rgba(239, 77, 90, 0.06) 0%, var(--color-surface) 100%);
}

.overview__num {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.1;
  letter-spacing: -0.5px;
}

.overview__item.is-warn .overview__num {
  color: var(--color-warning);
}

.overview__item.is-danger .overview__num {
  color: var(--color-danger);
}

.overview__label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.overview__hint {
  margin-top: var(--space-1);
  font-size: 12px;
  color: var(--color-warning);
  font-weight: 500;
}

.overview__hint--danger {
  color: var(--color-danger);
}

@media (max-width: 900px) {
  .overview {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ===== 预警面板：左右并排 ===== */
.alert-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  margin-block-end: var(--space-4);
}

.alert-panel {
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.alert-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-block-end: var(--space-3);
  border-block-end: 1px solid var(--color-border);
}

.alert-panel__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.alert-panel__badge {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.alert-panel__badge.is-warning {
  background: var(--color-warning);
}

.alert-panel__badge.is-danger {
  background: var(--color-danger);
}

.alert-panel__count {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1;
}

.alert-panel__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-height: 264px;
  overflow-y: auto;
  padding-inline-end: var(--space-1);
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-strong) transparent;
}

.alert-panel__list::-webkit-scrollbar {
  width: 6px;
}

.alert-panel__list::-webkit-scrollbar-thumb {
  background: var(--color-border-strong);
  border-radius: 3px;
}

.alert-panel__list::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-soft);
}

.alert-panel__list::-webkit-scrollbar-track {
  background: transparent;
}

.alert-panel__item {
  display: flex;
  align-items: stretch;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-bg-soft);
  transition: background 0.15s ease;
}

.alert-panel__item:hover {
  background: var(--color-primary-soft);
}

.alert-panel__strip {
  flex: 0 0 4px;
  width: 4px;
  border-radius: 2px;
}

.alert-panel__strip.is-warning {
  background: var(--color-warning);
}

.alert-panel__strip.is-danger {
  background: var(--color-danger);
}

.alert-panel__body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.alert-panel__name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alert-panel__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.alert-panel__meta .dot {
  color: var(--color-text-soft);
}

.text-warning {
  color: var(--color-warning);
  font-weight: 500;
}

.text-danger {
  color: var(--color-danger);
  font-weight: 500;
}

.alert-panel__more {
  align-self: flex-start;
  margin-block-start: var(--space-1);
  padding: 2px var(--space-2);
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background 0.15s ease;
}

.alert-panel__more:hover {
  background: var(--color-primary-soft);
}

.alert-panel__foot {
  display: flex;
  justify-content: flex-end;
  padding-block-start: var(--space-2);
  border-block-start: 1px solid var(--color-border);
}

@media (max-width: 980px) {
  .alert-panels {
    grid-template-columns: 1fr;
  }
}

/* ===== 目录卡片 ===== */
.catalog-card {
  padding: var(--space-5);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block: var(--space-5) var(--space-4);
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

/* ===== 筛选条 ===== */
.filter-bar {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr 1fr;
  gap: var(--space-3);
  align-items: center;
  padding-block-end: var(--space-4);
  border-block-end: 1px solid var(--color-border);
}

.filter-bar__field {
  width: 100%;
  min-width: 0;
}

.filter-bar__field :deep(.el-input__wrapper),
.filter-bar__field :deep(.el-select__wrapper) {
  min-height: 40px;
  width: 100%;
}

@media (max-width: 980px) {
  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 600px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }
}

/* ===== 表格 ===== */
.drug-name-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.drug-name {
  font-weight: 500;
}

.drug-tags {
  display: flex;
  gap: 4px;
}

.stock-low {
  color: var(--color-warning);
  font-weight: 600;
}

.stock-zero {
  color: var(--color-danger);
  font-weight: 600;
}

.unit-suffix {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-left: 4px;
}

.ml {
  margin-left: var(--space-2);
}

:deep(.row-warning) {
  background: color-mix(in srgb, var(--color-warning) 8%, transparent);
}

:deep(.row-zero) {
  background: color-mix(in srgb, var(--color-danger) 8%, transparent);
}

:deep(.el-table__row) {
  cursor: pointer;
}

.empty-hint {
  padding-block: var(--space-5);
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
}
</style>
