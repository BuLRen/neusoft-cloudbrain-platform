<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
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
  dispensationStatusName,
  PAGE_SIZE_DEFAULT,
} from '@/shared/constants/pharmacy'
import type {
  PrescriptionDetailResponse,
  PrescriptionSummary,
} from '@/shared/types/pharmacy'

const list = ref<PrescriptionSummary[]>([])
const loading = ref(false)
const loaded = ref(false)

const filter = ref<{
  patientId?: number
  registerId?: number
  status?: number
  dateRange: [string, string] | null
}>({
  patientId: undefined,
  registerId: undefined,
  status: undefined,
  dateRange: null,
})

const page = ref(1)
const pageSize = ref(PAGE_SIZE_DEFAULT)

const paged = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

// 详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PrescriptionDetailResponse | null>(null)

const statusOptions = [
  { value: 0, label: '待发药' },
  { value: 1, label: '已发药' },
  { value: 2, label: '已退药' },
]

function statusTone(status?: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

/** 把后端 ISO 时间（2026-06-30T10:36:08.32531）格式化成 2026-06-30 10:36 */
function formatTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filter.value.patientId) params.patientId = filter.value.patientId
    if (filter.value.registerId) params.registerId = filter.value.registerId
    if (filter.value.status != null) params.status = filter.value.status
    if (filter.value.dateRange && filter.value.dateRange.length === 2) {
      const [s, e] = filter.value.dateRange
      params.startDate = `${s}T00:00:00`
      params.endDate = `${e}T23:59:59`
    }
    list.value = await pharmacyApi.queryPrescriptions(
      Object.keys(params).length ? params : undefined,
    )
    page.value = 1
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function reset() {
  filter.value = { patientId: undefined, registerId: undefined, status: undefined, dateRange: null }
  void load()
}

async function viewDetail(row: PrescriptionSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await pharmacyApi.prescriptionDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="query-page u-page-grid">
    <PageHeader
      title="处方查询"
      description="按患者 / 状态 / 日期范围追溯历史处方。用于药师审计、患者用药史回顾、医生处方复核。"
      eyebrow="Role B / Pharmacy · ⑥"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="query-card">
      <div class="filter-bar">
        <div class="filter-bar__inputs">
          <ElInputNumber
            v-model="filter.patientId"
            :min="1"
            :controls="false"
            placeholder="患者 ID"
            class="field-id"
          />
          <ElInputNumber
            v-model="filter.registerId"
            :min="1"
            :controls="false"
            placeholder="挂号号"
            class="field-id"
          />
          <ElSelect
            v-model="filter.status"
            placeholder="发药状态"
            clearable
            class="field-fixed"
          >
            <ElOption
              v-for="o in statusOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </ElSelect>
          <ElDatePicker
            v-model="filter.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </div>
        <ElButton type="primary" @click="load">查询</ElButton>
        <ElButton text @click="reset">重置</ElButton>
      </div>

      <div class="section-title">
        <h3>查询结果</h3>
        <StatusTag v-if="loaded" tone="primary">{{ list.length }} 条</StatusTag>
      </div>

      <ElTable
        v-loading="loading"
        :data="paged"
        row-key="id"
        @row-click="viewDetail"
      >
        <ElTableColumn prop="id" label="处方 ID" min-width="90" align="center" />
        <ElTableColumn prop="patientName" label="患者" min-width="110" />
        <ElTableColumn prop="physicianName" label="开方医生" min-width="120" />
        <ElTableColumn prop="diagnosis" label="诊断" min-width="180" show-overflow-tooltip />
        <ElTableColumn prop="registerId" label="挂号号" min-width="100" align="center" />
        <ElTableColumn label="状态" min-width="100">
          <template #default="{ row }">
            <StatusTag :tone="statusTone(row.dispensationStatus)">
              {{ row.dispensationStatusName || dispensationStatusName(row.dispensationStatus) }}
            </StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="totalAmount" label="金额" min-width="100" align="right" />
        <ElTableColumn prop="createTime" label="开方时间" min-width="160" :formatter="(_row, _col, val) => formatTime(val as string)" />
        <ElTableColumn label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link size="small" @click.stop="viewDetail(row as PrescriptionSummary)">
              详情
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElEmpty
        v-if="loaded && list.length === 0"
        description="所选条件下暂无处方记录"
      />

      <div v-if="list.length > pageSize" class="pagination-row">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="list.length"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          small
          background
        />
      </div>
    </GlassCard>

    <!-- 处方详情弹窗 -->
    <ElDialog v-model="detailVisible" title="处方详情" width="780px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="患者">{{ detail.prescription.patientName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="开方医生">{{ detail.prescription.physicianName || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="诊断" :span="2">{{ detail.prescription.diagnosis || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="挂号号">{{ detail.prescription.registerId || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="发药状态">
              <StatusTag :tone="statusTone(detail.prescription.dispensationStatus)">
                {{ dispensationStatusName(detail.prescription.dispensationStatus) }}
              </StatusTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="发药人">{{ detail.prescription.pharmacist || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="发药时间">{{ formatTime(detail.prescription.dispensationTime) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="总金额" :span="2">
              {{ detail.prescription.totalAmount ?? '-' }} 元
            </ElDescriptionsItem>
          </ElDescriptions>

          <h4 class="mt">药品明细</h4>
          <ElTable :data="detail.details" size="small">
            <ElTableColumn prop="drugName" label="药品" min-width="140" />
            <ElTableColumn prop="specification" label="规格" min-width="110" />
            <ElTableColumn prop="usage" label="用法" min-width="110" />
            <ElTableColumn prop="dosage" label="剂量" min-width="90" />
            <ElTableColumn prop="quantity" label="数量" min-width="70" align="right" />
            <ElTableColumn prop="unitPrice" label="单价" min-width="80" align="right" />
            <ElTableColumn prop="totalAmount" label="金额" min-width="90" align="right" />
          </ElTable>
        </template>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.query-card {
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
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

.field-id {
  width: 130px;
}

.field-fixed {
  width: 140px;
}

.mt {
  margin-block-start: var(--space-5);
  font-size: 14px;
  font-weight: 600;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 1080px) {
  .field-id,
  .field-fixed {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>
