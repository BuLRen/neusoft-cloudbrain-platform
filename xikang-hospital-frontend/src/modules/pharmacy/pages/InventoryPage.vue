<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElButton,
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
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  DOSAGE_FORMS,
  PAGE_SIZE_DEFAULT,
} from '@/shared/constants/pharmacy'
import type { DrugOption } from '@/shared/types/pharmacy'

// ===== 药品目录筛选 =====
const drugKeyword = ref('')
const drugDosageForm = ref('')
const drugCategory = ref('')
const categoryOptions = ref<string[]>([])
const drugs = ref<DrugOption[]>([])
const lowStockDrugs = ref<DrugOption[]>([])
const loading = ref(false)

// ===== 批次弹窗 =====
const batchesVisible = ref(false)
const selectedDrug = ref<DrugOption | null>(null)

// ===== 低库存快捷入库 =====
const quickInboundDrug = ref<DrugOption | null>(null)
const quickInboundVisible = ref(false)

// ===== 分页 =====
const drugPage = ref(1)
const drugPageSize = ref(PAGE_SIZE_DEFAULT)

const pagedDrugs = computed(() => {
  const start = (drugPage.value - 1) * drugPageSize.value
  return drugs.value.slice(start, start + drugPageSize.value)
})

async function loadDrugCatalog() {
  loading.value = true
  try {
    const params: Record<string, string | undefined> = {
      keyword: drugKeyword.value || undefined,
      dosageForm: drugDosageForm.value || undefined,
      category: drugCategory.value || undefined,
    }
    const hasFilter = Object.values(params).some((v) => v)
    drugs.value = await pharmacyApi.drugs(hasFilter ? params : undefined)
    lowStockDrugs.value = await pharmacyApi.lowStockDrugs()
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categoryOptions.value = await pharmacyApi.categories()
}

function openBatches(drug: DrugOption) {
  selectedDrug.value = drug
  batchesVisible.value = true
}

function openQuickInbound(drug: DrugOption) {
  quickInboundDrug.value = drug
  quickInboundVisible.value = true
}

function onBatchesChanged() {
  void loadDrugCatalog()
}

onMounted(() => {
  void loadDrugCatalog()
  void loadCategories()
})
</script>

<template>
  <div class="inventory-page u-page-grid">
    <PageHeader
      title="药库与库存"
      description="药品目录 · 批号库存 · 低库存预警。点击任意药品查看批次明细、入库、盘点或生成用药指导。"
      eyebrow="Role B / Pharmacy · ②"
    >
      <template #actions>
        <ElButton @click="loadDrugCatalog">刷新目录</ElButton>
      </template>
    </PageHeader>

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
            v-for="d in lowStockDrugs.slice(0, 1)"
            :key="d.id"
            size="small"
            type="primary"
            @click="openQuickInbound(d)"
          >补货</ElButton>
        </div>
      </div>
    </GlassCard>

    <!-- 药品目录 -->
    <GlassCard class="catalog-card">
      <div class="filter-bar">
        <div class="filter-bar__inputs">
          <ElInput
            v-model="drugKeyword"
            placeholder="药品名称 / 通用名"
            clearable
            class="field-grow"
          />
          <ElSelect v-model="drugDosageForm" placeholder="剂型" clearable class="field-fixed">
            <ElOption v-for="f in DOSAGE_FORMS" :key="f" :label="f" :value="f" />
          </ElSelect>
          <ElSelect v-model="drugCategory" placeholder="分类" clearable class="field-fixed">
            <ElOption v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </ElSelect>
        </div>
        <ElButton type="primary" @click="loadDrugCatalog">查询</ElButton>
      </div>

      <div class="section-title">
        <h3>药品目录</h3>
        <StatusTag tone="primary">{{ drugs.length }} 条 · 点击行查看批次</StatusTag>
      </div>

      <ElTable
        v-loading="loading"
        :data="pagedDrugs"
        :row-class-name="({ row }: { row: DrugOption }) => (row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0) ? 'row-warning' : ''"
        row-key="id"
        @row-click="openBatches"
      >
        <ElTableColumn prop="name" label="药品名称" min-width="200">
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
        <ElTableColumn prop="specification" label="规格" min-width="140" />
        <ElTableColumn prop="manufacturer" label="厂家" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="price" label="单价" min-width="90" align="right" />
        <ElTableColumn label="库存" min-width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'stock-low': (row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0) }">
              {{ row.stockQuantity }}
            </span>
            <span class="unit-suffix">{{ row.unit }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link size="small" @click.stop="openBatches(row as DrugOption)">
              批次 / 操作
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="pagination-row">
        <ElPagination
          v-model:current-page="drugPage"
          v-model:page-size="drugPageSize"
          :total="drugs.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          small
          background
        />
      </div>
    </GlassCard>

    <!-- 批次弹窗（含入库/盘点/详情）-->
    <StockBatchesDialog
      v-model="batchesVisible"
      :drug="selectedDrug"
      @changed="onBatchesChanged"
    />

    <!-- 低库存快捷入库（独立 InboundDialog 实例，避免与弹窗内嵌的冲突）-->
    <StockBatchesDialog
      v-model="quickInboundVisible"
      :drug="quickInboundDrug"
      @changed="onBatchesChanged"
    />
  </div>
</template>

<style scoped>
/* ===== 低库存预警条 ===== */
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

/* ===== 筛选条 ===== */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block-end: var(--space-4);
  border-block-end: 1px solid var(--color-border);
}

.filter-bar__inputs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  flex: 1 1 360px;
}

.field-grow {
  flex: 1 1 200px;
  min-width: 160px;
}

.field-fixed {
  width: 140px;
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
  color: var(--color-danger, #f56c6c);
  font-weight: 600;
}

.unit-suffix {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-left: 4px;
}

:deep(.row-warning) {
  background: var(--color-warning-bg, #fdf6ec);
}

:deep(.el-table__row) {
  cursor: pointer;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .field-grow,
  .field-fixed {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
