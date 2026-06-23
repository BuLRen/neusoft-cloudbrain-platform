<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElInputNumber,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  PAGE_SIZE_DEFAULT,
  TRANSACTION_TYPES,
} from '@/shared/constants/pharmacy'
import type { PharmacyTransaction } from '@/shared/types/pharmacy'

const transactions = ref<PharmacyTransaction[]>([])
const loading = ref(false)
const filter = ref<{
  type: string
  drugId?: number
  dateRange: [string, string] | null
}>({
  type: '',
  drugId: undefined,
  dateRange: null,
})

const page = ref(1)
const pageSize = ref(PAGE_SIZE_DEFAULT)

const paged = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return transactions.value.slice(start, start + pageSize.value)
})

function typeTone(type?: string): 'success' | 'warning' | 'danger' | 'primary' {
  switch (type) {
    case '发放':
      return 'danger'
    case '退回':
      return 'warning'
    case '入库':
      return 'success'
    case '盘点':
      return 'primary'
    default:
      return 'primary'
  }
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filter.value.type) params.type = filter.value.type
    if (filter.value.drugId) params.drugId = filter.value.drugId
    if (filter.value.dateRange && filter.value.dateRange.length === 2) {
      const [s, e] = filter.value.dateRange
      params.startDate = `${s}T00:00:00`
      params.endDate = `${e}T23:59:59`
    }
    transactions.value = await pharmacyApi.transactions(
      Object.keys(params).length ? params : undefined,
    )
    page.value = 1
  } finally {
    loading.value = false
  }
}

function reset() {
  filter.value = { type: '', drugId: undefined, dateRange: null }
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="txn-page u-page-grid">
    <PageHeader
      title="出入库流水"
      description="所有发放、退回、入库、盘点、报损的审计流水。支持按类型、药品、日期范围组合筛选。"
      eyebrow="Role B / Pharmacy · ③"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="txn-card">
      <!-- 筛选条 -->
      <div class="filter-bar">
        <ElSelect
          v-model="filter.type"
          placeholder="交易类型"
          clearable
          class="field-fixed"
        >
          <ElOption v-for="t in TRANSACTION_TYPES" :key="t" :label="t" :value="t" />
        </ElSelect>
        <ElDatePicker
          v-model="filter.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <ElInputNumber
          v-model="filter.drugId"
          :min="1"
          :controls="false"
          placeholder="药品 ID"
          class="field-id"
        />
        <ElButton type="primary" @click="load">查询</ElButton>
        <ElButton text @click="reset">重置</ElButton>
      </div>

      <ElTable v-loading="loading" :data="paged">
        <ElTableColumn prop="transactionTime" label="时间" min-width="160" />
        <ElTableColumn label="类型" min-width="90">
          <template #default="{ row }">
            <StatusTag :tone="typeTone(row.type)">{{ row.type }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="drugName" label="药品" min-width="160" />
        <ElTableColumn prop="quantity" label="数量" min-width="80" />
        <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
        <ElTableColumn prop="operatorName" label="操作人" min-width="120" />
        <ElTableColumn prop="reason" label="原因" min-width="180" />
      </ElTable>

      <ElEmpty
        v-if="!loading && transactions.length === 0"
        description="所选条件下暂无交易记录"
      />

      <div v-if="transactions.length > pageSize" class="pagination-row">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="transactions.length"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          small
          background
        />
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.txn-card {
  padding: var(--space-5);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.field-fixed {
  width: 140px;
}

.field-id {
  width: 120px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-3);
}

@media (max-width: 1080px) {
  .field-fixed,
  .field-id {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
