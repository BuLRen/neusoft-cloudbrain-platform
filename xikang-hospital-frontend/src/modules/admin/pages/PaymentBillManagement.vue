<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElIcon,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { adminPaymentApi } from '@/shared/api/modules/adminPayment'
import type { OrderStatusFilter, PaymentOrder } from '@/shared/types/payment'
import {
  formatMoney,
  formatPaymentTime,
  orderStatusText,
  orderStatusTone,
} from '@/shared/utils/paymentStatus'
import {
  loadStoredFilters,
  saveFilters,
  useAdminPaymentBillSearch,
} from '@/modules/admin/composables/useAdminPaymentBillSearch'

const router = useRouter()
const stored = loadStoredFilters()

const loading = ref(false)
const statsLoading = ref(false)
const orders = ref<PaymentOrder[]>([])
const total = ref(0)

const stats = reactive({
  total: 0,
  pending: 0,
  paid: 0,
  refunded: 0,
})

const {
  searchLoading,
  patientOptions,
  selectedPatient,
  listKeyword,
  resolveSearch,
  selectPatient,
  clearPatientSelection,
  listQueryParams,
} = useAdminPaymentBillSearch()

const filters = reactive({
  searchInput: stored.searchInput ?? '',
  status: stored.status as OrderStatusFilter | undefined,
  dateRange: (stored.dateRange ?? []) as string[],
  page: stored.page ?? 1,
  size: stored.size ?? 10,
})

const statusTabs: { label: string; value: OrderStatusFilter | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '待缴费', value: 0 },
  { label: '已付清', value: 1 },
  { label: '含已退', value: 2 },
]

const statCards = [
  { key: 'total' as const, label: '账单总数', tone: 'primary', icon: 'bill' },
  { key: 'pending' as const, label: '待缴费', tone: 'warning', icon: 'pending' },
  { key: 'paid' as const, label: '已付清', tone: 'success', icon: 'paid' },
  { key: 'refunded' as const, label: '含已退', tone: 'neutral', icon: 'refund' },
]

function persistFilters() {
  saveFilters({
    searchInput: filters.searchInput,
    patientId: selectedPatient.value?.id,
    patientName: selectedPatient.value?.realName,
    listKeyword: listKeyword.value,
    status: filters.status,
    dateRange: filters.dateRange,
    page: filters.page,
    size: filters.size,
  })
}

function baseListParams() {
  const params = listQueryParams(filters)
  const { page: _page, size: _size, status: _status, ...rest } = params
  return rest
}

async function loadStats() {
  statsLoading.value = true
  try {
    const base = baseListParams()
    const [all, pending, paid, refunded] = await Promise.all([
      adminPaymentApi.list({ ...base, status: undefined, page: 1, size: 1 }),
      adminPaymentApi.list({ ...base, status: 0, page: 1, size: 1 }),
      adminPaymentApi.list({ ...base, status: 1, page: 1, size: 1 }),
      adminPaymentApi.list({ ...base, status: 2, page: 1, size: 1 }),
    ])
    stats.total = all?.total ?? 0
    stats.pending = pending?.total ?? 0
    stats.paid = paid?.total ?? 0
    stats.refunded = refunded?.total ?? 0
  } catch {
    // stats are supplementary; ignore failures
  } finally {
    statsLoading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  persistFilters()
  try {
    const result = await adminPaymentApi.list(listQueryParams(filters))
    orders.value = result?.orders ?? []
    total.value = result?.total ?? 0
    if (result?.page) filters.page = result.page
    if (result?.size) filters.size = result.size
    void loadStats()
  } catch (e) {
    orders.value = []
    total.value = 0
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function searchRecords() {
  filters.page = 1
  const params = await resolveSearch(filters.searchInput)
  if (!params.patientId && !params.keyword && filters.searchInput.trim()) {
    return
  }
  await loadRecords()
}

async function selectPatientAndLoad(patient: Parameters<typeof selectPatient>[0]) {
  selectPatient(patient)
  filters.page = 1
  await loadRecords()
}

function clearSearch() {
  filters.searchInput = ''
  clearPatientSelection()
  filters.page = 1
  void loadRecords()
}

function onPageChange(page: number) {
  filters.page = page
  void loadRecords()
}

function onPageSizeChange(size: number) {
  filters.size = size
  filters.page = 1
  void loadRecords()
}

function setStatusFilter(status: OrderStatusFilter | undefined) {
  filters.status = status
  filters.page = 1
  void loadRecords()
}

function openCharge(row: PaymentOrder) {
  router.push({ name: 'PaymentBillCharge', params: { registerId: String(row.registerId) } })
}

function visitMeta(row: PaymentOrder): string {
  const parts: string[] = []
  if (row.departmentName) parts.push(row.departmentName)
  if (row.doctorName) parts.push(row.doctorName)
  const visitDate = formatPaymentTime(row.visitDate)
  if (visitDate) parts.push(visitDate)
  return parts.length ? parts.join(' · ') : '—'
}

onMounted(async () => {
  if (stored.patientId && stored.patientName) {
    selectPatient({ id: stored.patientId, realName: stored.patientName })
  } else if (stored.listKeyword) {
    listKeyword.value = stored.listKeyword
  } else if (filters.searchInput.trim()) {
    await resolveSearch(filters.searchInput)
  }
  await loadRecords()
})

watch(
  () => filters.dateRange,
  () => {
    persistFilters()
  },
)
</script>

<template>
  <div class="bill-page">
    <header class="bill-hero">
      <div class="bill-hero__text">
        <h1>门诊账单管理</h1>
        <p>查询与处理患者就诊账单</p>
      </div>
      <div class="bill-stats" v-loading="statsLoading">
        <div
          v-for="card in statCards"
          :key="card.key"
          class="bill-stat"
          :data-tone="card.tone"
        >
          <span class="bill-stat__icon" :data-icon="card.icon" aria-hidden="true">
            <svg v-if="card.icon === 'bill'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
              <rect x="9" y="3" width="6" height="4" rx="1" />
              <path d="M9 12h6M9 16h4" />
            </svg>
            <svg v-else-if="card.icon === 'pending'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3 2" />
            </svg>
            <svg v-else-if="card.icon === 'paid'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M5 12l5 5L20 7" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 10h10a4 4 0 0 1 4 4v0a4 4 0 0 1-4 4H5" />
              <path d="M7 6L3 10l4 4" />
            </svg>
          </span>
          <div class="bill-stat__body">
            <span class="bill-stat__label">{{ card.label }}</span>
            <strong class="bill-stat__value">{{ stats[card.key] }} 条</strong>
          </div>
        </div>
      </div>
    </header>

    <section class="bill-panel">
      <div class="bill-toolbar">
        <div class="bill-toolbar__row">
          <ElInput
            v-model="filters.searchInput"
            clearable
            placeholder="患者姓名 / 手机号 / 档案号 / 挂号号"
            class="bill-search"
            @keyup.enter="searchRecords"
            @clear="clearSearch"
          >
            <template #prefix>
              <ElIcon class="bill-search__icon"><Search /></ElIcon>
            </template>
          </ElInput>
          <ElDatePicker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="创建开始"
            end-placeholder="创建结束"
            value-format="YYYY-MM-DD"
            class="bill-date"
            clearable
          />
          <ElButton
            type="primary"
            class="bill-btn bill-btn--primary"
            :loading="searchLoading || loading"
            @click="searchRecords"
          >
            查询账单
          </ElButton>
          <ElButton class="bill-btn bill-btn--ghost" :icon="Refresh" @click="loadRecords">
            刷新
          </ElButton>
        </div>

        <div v-if="patientOptions.length" class="patient-options">
          <p class="patient-options__hint">找到多位患者，请选择：</p>
          <div class="patient-options__list">
            <button
              v-for="patient in patientOptions"
              :key="patient.id"
              type="button"
              class="patient-card"
              @click="selectPatientAndLoad(patient)"
            >
              <strong>{{ patient.realName }}</strong>
              <span>档案号 {{ patient.id }}</span>
              <span v-if="patient.phone">{{ patient.phone }}</span>
            </button>
          </div>
        </div>

        <div v-if="selectedPatient" class="selected-patient">
          <strong>当前筛选患者：{{ selectedPatient.realName }}</strong>
          <span class="selected-patient__meta">档案号 {{ selectedPatient.id }}</span>
          <button type="button" class="selected-patient__clear" @click="clearSearch">清除</button>
        </div>

        <div class="bill-tabs" role="tablist" aria-label="账单状态筛选">
          <button
            v-for="tab in statusTabs"
            :key="String(tab.value)"
            type="button"
            role="tab"
            class="bill-tabs__item"
            :class="{ 'bill-tabs__item--active': filters.status === tab.value }"
            :aria-selected="filters.status === tab.value"
            @click="setStatusFilter(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <div class="bill-table-wrap" v-loading="loading">
        <ElTable v-if="orders.length" :data="orders" class="bill-table">
          <ElTableColumn prop="registerId" label="挂号号" width="96" />
          <ElTableColumn label="患者" min-width="100">
            <template #default="{ row }">{{ row.patientName || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="就诊信息" min-width="240">
            <template #default="{ row }">
              <span class="bill-table__visit">{{ visitMeta(row) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="总金额" width="120" align="right">
            <template #default="{ row }">
              <span class="bill-table__money">{{ formatMoney(row.totalAmount) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="已缴 / 待缴" width="170" align="right">
            <template #default="{ row }">
              <span class="bill-table__split">
                {{ formatMoney(row.paidAmount) }} / {{ formatMoney(row.pendingAmount) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="108">
            <template #default="{ row }">
              <StatusTag :tone="orderStatusTone(row)">{{ orderStatusText(row) }}</StatusTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="创建时间" width="168">
            <template #default="{ row }">
              {{ formatPaymentTime(row.createTime as string) || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="88" fixed="right" align="center">
            <template #default="{ row }">
              <button type="button" class="bill-action" @click="openCharge(row)">处理</button>
            </template>
          </ElTableColumn>
        </ElTable>

        <ElEmpty v-else description="暂无支付账单" class="bill-empty" />
      </div>

      <footer v-if="total > 0" class="bill-footer">
        <p class="bill-footer__total">共 {{ total }} 条</p>
        <ElPagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </footer>
    </section>
  </div>
</template>

<style scoped>
.bill-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.bill-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.bill-hero__text h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text, #0f172a);
  line-height: 1.2;
}

.bill-hero__text p {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--color-text-muted, #64748b);
}

.bill-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.bill-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 148px;
  padding: 12px 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid var(--color-border, #e8edf3);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.bill-stat__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  background: var(--stat-icon-bg, #eff6ff);
  color: var(--stat-icon-color, #3b82f6);
}

.bill-stat__icon svg {
  width: 20px;
  height: 20px;
}

.bill-stat[data-tone='primary'] {
  --stat-icon-bg: #eff6ff;
  --stat-icon-color: #3b82f6;
}

.bill-stat[data-tone='warning'] {
  --stat-icon-bg: #fff7ed;
  --stat-icon-color: #f59e0b;
}

.bill-stat[data-tone='success'] {
  --stat-icon-bg: #ecfdf5;
  --stat-icon-color: #10b981;
}

.bill-stat[data-tone='neutral'] {
  --stat-icon-bg: #f1f5f9;
  --stat-icon-color: #64748b;
}

.bill-stat__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bill-stat__label {
  font-size: 12px;
  color: var(--color-text-muted, #64748b);
}

.bill-stat__value {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text, #0f172a);
  line-height: 1.2;
}

.bill-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--color-border, #e8edf3);
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.bill-toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--color-border, #eef2f6);
  background: #fff;
}

.bill-toolbar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.bill-search {
  flex: 1 1 280px;
  max-width: 420px;
}

.bill-search__icon {
  color: #94a3b8;
}

.bill-date {
  width: 280px;
}

.bill-btn {
  border-radius: 10px;
  font-weight: 600;
  padding-inline: 18px;
}

.bill-btn--ghost {
  --el-button-bg-color: #fff;
  --el-button-border-color: #dbe4ee;
  --el-button-text-color: #334155;
}

.bill-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
  width: fit-content;
}

.bill-tabs__item {
  border: none;
  background: transparent;
  padding: 8px 18px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.bill-tabs__item:hover {
  color: #334155;
}

.bill-tabs__item--active {
  background: #fff;
  color: #2563eb;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.patient-options__hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted, #64748b);
}

.patient-options__list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.patient-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 180px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.patient-card:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.patient-card strong {
  font-size: 15px;
  color: #0f172a;
}

.patient-card span {
  font-size: 12px;
  color: #64748b;
}

.selected-patient {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  font-size: 14px;
  color: #1e40af;
}

.selected-patient__meta {
  color: #64748b;
  font-size: 13px;
}

.selected-patient__clear {
  margin-left: auto;
  border: none;
  background: none;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.bill-table-wrap {
  min-height: 200px;
  padding: 0 8px;
}

.bill-table {
  width: 100%;
}

.bill-table__visit {
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.bill-table__money {
  font-weight: 600;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.bill-table__split {
  font-size: 13px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.bill-action {
  border: none;
  background: none;
  color: #2563eb;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.bill-action:hover {
  background: #eff6ff;
}

.bill-empty {
  padding: 48px 0;
}

.bill-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px 18px;
  border-top: 1px solid var(--color-border, #eef2f6);
  background: #fafbfc;
}

.bill-footer__total {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

/* Table polish */
.bill-table :deep(.el-table__header th) {
  background: #f8fafc !important;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #eef2f6;
}

.bill-table :deep(.el-table__body td) {
  font-size: 14px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
  padding-block: 14px;
}

.bill-table :deep(.el-table__row:hover > td) {
  background: #f8fbff !important;
}

.bill-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.bill-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.bill-date :deep(.el-input__wrapper) {
  border-radius: 10px;
}

@media (max-width: 900px) {
  .bill-hero {
    flex-direction: column;
  }

  .bill-stats {
    width: 100%;
  }

  .bill-stat {
    flex: 1 1 calc(50% - 6px);
    min-width: 0;
  }

  .bill-search {
    max-width: none;
    width: 100%;
  }

  .bill-date {
    width: 100%;
  }
}
</style>
