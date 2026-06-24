<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
  return { totalDrugs, totalStock, lowStockCount, expiringCount }
})

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
        <ElButton type="primary" @click="goBatchInbound">📥 批量入库</ElButton>
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
      </div>
      <div class="overview__item" :class="{ 'is-warn': overview.expiringCount > 0 }">
        <div class="overview__num">{{ overview.expiringCount }}</div>
        <div class="overview__label">≤ {{ NEAR_EXPIRY_DAYS }} 天到期批次</div>
      </div>
    </div>

    <!-- 低库存预警条（仅在有低库存时出现）-->
    <GlassCard v-if="lowStockDrugs.length > 0" class="alert-card" :tone="'warning'">
      <div class="alert-row">
        <div class="alert-row__icon">!</div>
        <div class="alert-row__body">
          <strong>{{ lowStockDrugs.length }} 种药品库存低于阈值</strong>
          <ul class="alert-list">
            <li v-for="d in lowStockDrugs.slice(0, 4)" :key="d.id">
              {{ d.name }}（{{ d.stockQuantity }} {{ d.unit || '件' }}）
            </li>
            <li v-if="lowStockDrugs.length > 4" class="more">
              …另外 {{ lowStockDrugs.length - 4 }} 种
            </li>
          </ul>
        </div>
        <div class="alert-row__action">
          <ElButton
            size="small"
            @click="stockFilter = 'low'"
          >仅看低库存</ElButton>
        </div>
      </div>
    </GlassCard>

    <!-- 近效期预警条 -->
    <GlassCard v-if="expiringBatches.length > 0" class="alert-card" :tone="'warning'">
      <div class="alert-row">
        <div class="alert-row__icon">⏰</div>
        <div class="alert-row__body">
          <strong>{{ expiringBatches.length }} 个批次将在 {{ NEAR_EXPIRY_DAYS }} 天内到期</strong>
          <ul class="alert-list">
            <li v-for="b in expiringBatches.slice(0, 4)" :key="b.id">
              {{ b.drugName || `#${b.drugId}` }} · 批号 {{ b.batchNumber || '-' }}
              · 剩余 {{ b.daysRemaining }} 天
            </li>
            <li v-if="expiringBatches.length > 4" class="more">
              …另外 {{ expiringBatches.length - 4 }} 个
            </li>
          </ul>
        </div>
        <div class="alert-row__action">
          <ElButton size="small" @click="openLoss(lowStockDrugs[0] ?? drugs[0] ?? null)">
            快速报损
          </ElButton>
        </div>
      </div>
    </GlassCard>

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
          >
            <template #prefix>
              <span class="field-icon">🔍</span>
            </template>
          </ElInput>
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
            <ElOption value="low" label="⚠️ 仅低库存" />
            <ElOption value="zero" label="⛔ 仅零库存" />
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
  border-color: var(--color-primary, #409eff);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.08);
}

.overview__item.is-warn {
  border-color: var(--color-warning, #e6a23c);
  background: linear-gradient(180deg, #fffbf0 0%, var(--color-surface) 100%);
}

.overview__num {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.1;
}

.overview__item.is-warn .overview__num {
  color: var(--color-warning, #e6a23c);
}

.overview__label {
  font-size: 13px;
  color: var(--color-text-muted);
}

@media (max-width: 900px) {
  .overview {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ===== 低库存 / 近效期预警条 ===== */
.alert-card {
  padding: var(--space-3) var(--space-4);
  border-left: 4px solid var(--color-warning, #e6a23c);
}

.alert-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.alert-row__icon {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-warning, #e6a23c);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.alert-row__body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.alert-list {
  margin: 0;
  padding-left: var(--space-4);
  color: var(--color-text-muted);
}

.alert-list .more {
  list-style: none;
  color: var(--color-text-muted);
}

.alert-row__action {
  flex: 0 0 auto;
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

/* ===== 筛选条：核心样式 =====
   - 4 个等宽字段，整齐对齐
   - 高度由 Element Plus size=default 统一控制为 40px
   - 间距用 gap 一致控制
*/
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

.filter-bar__field--status {
  /* 状态过滤可以稍短 */
}

.filter-bar__field :deep(.el-input__wrapper),
.filter-bar__field :deep(.el-select__wrapper) {
  min-height: 40px;
  width: 100%;
}

.field-icon {
  font-size: 14px;
  opacity: 0.6;
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
  color: var(--color-warning, #e6a23c);
  font-weight: 600;
}

.stock-zero {
  color: var(--color-danger, #f56c6c);
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
  background: var(--color-warning-bg, #fdf6ec);
}

:deep(.row-zero) {
  background: var(--color-danger-bg, #fef0f0);
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
