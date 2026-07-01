<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'
import { adminPaymentApi, type AdminPatientOption } from '@/shared/api/modules/adminPayment'
import type { OrderStatusFilter, PaymentItem, PaymentOrder } from '@/shared/types/payment'
import {
  formatMoney,
  formatPaymentTime,
  itemCodeLabel,
  itemStatusText,
  itemStatusTone,
  orderStatusText,
  orderStatusTone,
} from '@/shared/utils/paymentStatus'

const authStore = useAuthStore()

const loading = ref(false)
const detailLoading = ref(false)
const patientSearchLoading = ref(false)
const orders = ref<PaymentOrder[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<PaymentOrder | null>(null)

const patientKeyword = ref('')
const patientOptions = ref<AdminPatientOption[]>([])
const selectedPatient = ref<AdminPatientOption | null>(null)
const patientBalance = ref(0)

const rechargeVisible = ref(false)
const rechargeAmount = ref(100)
const rechargeRemark = ref('现场窗口现金充值')
const rechargeLoading = ref(false)

const payingItemId = ref<number | null>(null)
const payingAll = ref(false)
const markingPaid = ref(false)

const filters = reactive({
  keyword: '',
  status: undefined as OrderStatusFilter | undefined,
  dateRange: [] as string[],
  page: 1,
  size: 20,
})

const statusTabs: { label: string; value: OrderStatusFilter | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '待缴费', value: 0 },
  { label: '已付清', value: 1 },
  { label: '含已退', value: 2 },
]

const operator = computed(() => ({
  operatorId: authStore.employeeId ?? undefined,
  operatorName: authStore.realName || '现场收费窗口',
}))

const pendingItems = computed(() =>
  (detail.value?.items ?? []).filter((item) => item.status === 0 || item.status == null),
)

function listParams() {
  const [startDate, endDate] = filters.dateRange.length === 2 ? filters.dateRange : [undefined, undefined]
  return {
    keyword: filters.keyword.trim() || undefined,
    patientId: selectedPatient.value?.id,
    status: filters.status,
    startDate,
    endDate,
    page: filters.page,
    size: filters.size,
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const result = await adminPaymentApi.list(listParams())
    orders.value = result?.orders ?? []
    total.value = result?.total ?? 0
    if (result?.page) filters.page = result.page
    if (result?.size) filters.size = result.size
  } catch (e) {
    orders.value = []
    total.value = 0
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function searchPatients() {
  const keyword = patientKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入患者姓名、手机号或档案号')
    return
  }
  patientSearchLoading.value = true
  try {
    patientOptions.value = await adminPaymentApi.searchPatients(keyword)
    if (!patientOptions.value.length) {
      ElMessage.info('未找到匹配患者')
    }
  } catch (e) {
    patientOptions.value = []
    ElMessage.error(e instanceof Error ? e.message : '患者查询失败')
  } finally {
    patientSearchLoading.value = false
  }
}

async function selectPatient(patient: AdminPatientOption) {
  selectedPatient.value = patient
  filters.keyword = patient.realName
  filters.page = 1
  await refreshPatientBalance()
  await loadRecords()
}

function clearSelectedPatient() {
  selectedPatient.value = null
  patientOptions.value = []
  patientKeyword.value = ''
  patientBalance.value = 0
  filters.keyword = ''
  void loadRecords()
}

async function refreshPatientBalance() {
  if (!selectedPatient.value) return
  try {
    const balance = await adminPaymentApi.getPatientBalance(selectedPatient.value.id)
    patientBalance.value = Number(balance.accountBalance ?? 0)
    selectedPatient.value = {
      ...selectedPatient.value,
      accountBalance: patientBalance.value,
    }
  } catch {
    patientBalance.value = Number(selectedPatient.value.accountBalance ?? 0)
  }
}

function openRechargeDialog() {
  if (!selectedPatient.value) {
    ElMessage.warning('请先选择患者')
    return
  }
  rechargeAmount.value = Math.max(100, detail.value?.pendingAmount ?? 100)
  rechargeRemark.value = '现场窗口现金充值'
  rechargeVisible.value = true
}

async function submitRecharge() {
  if (!selectedPatient.value) return
  if (!rechargeAmount.value || rechargeAmount.value <= 0) {
    ElMessage.warning('请输入有效充值金额')
    return
  }
  rechargeLoading.value = true
  try {
    const result = await adminPaymentApi.rechargePatient(
      selectedPatient.value.id,
      rechargeAmount.value,
      rechargeRemark.value,
      operator.value,
    )
    patientBalance.value = Number(result.accountBalance ?? 0)
    selectedPatient.value = { ...selectedPatient.value, accountBalance: patientBalance.value }
    ElMessage.success(result.message || '充值成功')
    rechargeVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '充值失败')
  } finally {
    rechargeLoading.value = false
  }
}

function searchRecords() {
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
  searchRecords()
}

async function reloadDetail(registerId: number) {
  detail.value = await adminPaymentApi.getDetail(registerId)
  if (selectedPatient.value) {
    await refreshPatientBalance()
  }
  await loadRecords()
}

async function openDetail(row: PaymentOrder) {
  detailVisible.value = true
  detail.value = row
  detailLoading.value = true
  try {
    detail.value = await adminPaymentApi.getDetail(row.registerId)
    if (!selectedPatient.value && detail.value.patientId) {
      selectedPatient.value = {
        id: detail.value.patientId,
        realName: detail.value.patientName || `患者#${detail.value.patientId}`,
        accountBalance: 0,
      }
      await refreshPatientBalance()
    }
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '加载明细失败')
  } finally {
    detailLoading.value = false
  }
}

async function confirmMarkItemPaid(item: PaymentItem) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `确认现场收取现金并标记「${item.itemName || itemCodeLabel(item.itemCode)}」为已支付？\n金额：${formatMoney(item.totalAmount)}`,
      '现场收费确认',
      { type: 'warning', confirmButtonText: '确认收费', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  markingPaid.value = true
  try {
    await adminPaymentApi.markPaid({
      registerId: detail.value.registerId,
      itemIds: [item.id],
      ...operator.value,
    })
    ElMessage.success('已标记为已支付')
    await reloadDetail(detail.value.registerId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '收费失败')
  } finally {
    markingPaid.value = false
  }
}

async function confirmPayItemByBalance(item: PaymentItem) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `确认从患者余额扣款支付「${item.itemName || itemCodeLabel(item.itemCode)}」？\n金额：${formatMoney(item.totalAmount)}\n当前余额：${formatMoney(patientBalance.value)}`,
      '余额支付确认',
      { type: 'warning', confirmButtonText: '确认扣款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payingItemId.value = item.id
  try {
    const result = await adminPaymentApi.payItemByBalance(
      detail.value.registerId,
      item.id,
      operator.value,
    )
    if (result.accountBalance != null) {
      patientBalance.value = Number(result.accountBalance)
    }
    ElMessage.success(result.paymentMessage || '支付成功')
    await reloadDetail(detail.value.registerId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '支付失败')
  } finally {
    payingItemId.value = null
  }
}

async function confirmPayAllByBalance() {
  if (!detail.value || pendingItems.value.length === 0) return
  const totalPending = pendingItems.value.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0)
  try {
    await ElMessageBox.confirm(
      `确认从患者余额扣款支付全部待缴费用？\n待缴合计：${formatMoney(totalPending)}\n当前余额：${formatMoney(patientBalance.value)}`,
      '一键余额支付',
      { type: 'warning', confirmButtonText: '确认扣款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payingAll.value = true
  try {
    const result = await adminPaymentApi.payAllByBalance(detail.value.registerId, operator.value)
    if (result.accountBalance != null) {
      patientBalance.value = Number(result.accountBalance)
    }
    if (result.success) {
      ElMessage.success(result.paymentMessage || '支付成功')
    } else {
      ElMessage.warning(result.paymentMessage || '部分费用支付失败')
    }
    await reloadDetail(detail.value.registerId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '支付失败')
  } finally {
    payingAll.value = false
  }
}

async function confirmMarkAllPaid() {
  if (!detail.value || pendingItems.value.length === 0) return
  const totalPending = pendingItems.value.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0)
  try {
    await ElMessageBox.confirm(
      `确认现场收取现金并标记全部待缴费用为已支付？\n待缴合计：${formatMoney(totalPending)}`,
      '现场收费确认',
      { type: 'warning', confirmButtonText: '确认收费', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  markingPaid.value = true
  try {
    await adminPaymentApi.markPaid({
      registerId: detail.value.registerId,
      itemIds: pendingItems.value.map((item) => item.id),
      ...operator.value,
    })
    ElMessage.success('已全部标记为已支付')
    await reloadDetail(detail.value.registerId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '收费失败')
  } finally {
    markingPaid.value = false
  }
}

function visitMeta(row: PaymentOrder): string {
  const parts: string[] = []
  if (row.departmentName) parts.push(row.departmentName)
  if (row.doctorName) parts.push(row.doctorName)
  const visitDate = formatPaymentTime(row.visitDate)
  if (visitDate) parts.push(visitDate)
  return parts.length ? parts.join(' · ') : '—'
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <div class="payment-bill-management u-page-grid">
    <PageHeader
      title="支付账单"
      description="现场收费窗口：按患者姓名查询账单，支持现金充值、余额扣款与现场收费记账。"
      eyebrow="管理员"
    />

    <GlassCard class="panel patient-panel">
      <div class="patient-panel__head">
        <h3>患者查询</h3>
        <p>适用于一楼大厅现场收费，先定位患者再处理账单。</p>
      </div>
      <div class="toolbar__filters">
        <ElInput
          v-model="patientKeyword"
          clearable
          placeholder="患者姓名 / 手机号 / 档案号"
          class="field field--keyword"
          @keyup.enter="searchPatients"
        />
        <ElButton type="primary" :loading="patientSearchLoading" @click="searchPatients">查找患者</ElButton>
        <ElButton v-if="selectedPatient" @click="clearSelectedPatient">清除选择</ElButton>
      </div>

      <div v-if="patientOptions.length" class="patient-options">
        <button
          v-for="patient in patientOptions"
          :key="patient.id"
          type="button"
          class="patient-card"
          :class="{ 'patient-card--active': selectedPatient?.id === patient.id }"
          @click="selectPatient(patient)"
        >
          <strong>{{ patient.realName }}</strong>
          <span>档案号 {{ patient.id }}</span>
          <span v-if="patient.phone">{{ patient.phone }}</span>
          <span>余额 {{ formatMoney(patient.accountBalance) }}</span>
        </button>
      </div>

      <div v-if="selectedPatient" class="selected-patient">
        <div>
          <strong>当前患者：{{ selectedPatient.realName }}</strong>
          <span class="selected-patient__meta">档案号 {{ selectedPatient.id }}</span>
        </div>
        <div class="selected-patient__actions">
          <StatusTag tone="primary">余额 {{ formatMoney(patientBalance) }}</StatusTag>
          <ElButton type="primary" @click="openRechargeDialog">现金充值</ElButton>
          <ElButton @click="refreshPatientBalance">刷新余额</ElButton>
        </div>
      </div>
    </GlassCard>

    <GlassCard class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <ElInput
            v-model="filters.keyword"
            clearable
            placeholder="患者姓名 / 挂号号"
            class="field field--keyword"
            @keyup.enter="searchRecords"
          />
          <ElDatePicker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="创建开始"
            end-placeholder="创建结束"
            value-format="YYYY-MM-DD"
            class="field field--date"
            clearable
          />
          <ElButton type="primary" @click="searchRecords">查询账单</ElButton>
          <ElButton @click="loadRecords">刷新</ElButton>
        </div>
        <div class="status-tabs">
          <ElButton
            v-for="tab in statusTabs"
            :key="String(tab.value)"
            :type="filters.status === tab.value ? 'primary' : 'default'"
            size="small"
            @click="setStatusFilter(tab.value)"
          >
            {{ tab.label }}
          </ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="orders" stripe>
        <ElTableColumn prop="registerId" label="挂号号" width="100" />
        <ElTableColumn label="患者" min-width="120">
          <template #default="{ row }">{{ row.patientName || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="就诊信息" min-width="200">
          <template #default="{ row }">{{ visitMeta(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="总金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </ElTableColumn>
        <ElTableColumn label="已缴 / 待缴" width="160" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.paidAmount) }} / {{ formatMoney(row.pendingAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :tone="orderStatusTone(row)">{{ orderStatusText(row) }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="160">
          <template #default="{ row }">{{ formatPaymentTime(row.createTime as string) || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row)">处理</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElEmpty v-if="!loading && !orders.length" description="暂无支付账单" />

      <div class="pagination-bar">
        <p class="table-footer">共 {{ total }} 条账单</p>
        <ElPagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </GlassCard>

    <ElDrawer
      v-model="detailVisible"
      :title="detail ? `现场收费 · 挂号 ${detail.registerId}` : '现场收费'"
      size="640px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-summary">
            <p><strong>患者：</strong>{{ detail.patientName || '—' }}</p>
            <p><strong>就诊：</strong>{{ visitMeta(detail) }}</p>
            <p>
              <strong>状态：</strong>
              <StatusTag :tone="orderStatusTone(detail)">{{ orderStatusText(detail) }}</StatusTag>
            </p>
            <p>
              <strong>金额：</strong>
              合计 {{ formatMoney(detail.totalAmount) }}，
              已缴 {{ formatMoney(detail.paidAmount) }}，
              待缴 {{ formatMoney(detail.pendingAmount) }}
            </p>
            <p v-if="selectedPatient">
              <strong>账户余额：</strong>{{ formatMoney(patientBalance) }}
            </p>
          </div>

          <div v-if="pendingItems.length" class="detail-actions">
            <ElButton type="primary" :loading="payingAll" @click="confirmPayAllByBalance">
              余额支付全部
            </ElButton>
            <ElButton :loading="markingPaid" @click="confirmMarkAllPaid">
              现场收费（全部）
            </ElButton>
            <ElButton @click="openRechargeDialog">充值</ElButton>
          </div>

          <ElTable :data="detail.items" stripe size="small" class="detail-table">
            <ElTableColumn label="费用项" min-width="120">
              <template #default="{ row }">
                {{ row.itemName || itemCodeLabel(row.itemCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="金额" width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <StatusTag :tone="itemStatusTone(row.status)">{{ itemStatusText(row.status) }}</StatusTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" min-width="220" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 0 || row.status == null">
                  <ElButton
                    link
                    type="primary"
                    :loading="payingItemId === row.id"
                    @click="confirmPayItemByBalance(row)"
                  >
                    余额支付
                  </ElButton>
                  <ElButton
                    link
                    type="warning"
                    :loading="markingPaid"
                    @click="confirmMarkItemPaid(row)"
                  >
                    现场收费
                  </ElButton>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </ElTableColumn>
          </ElTable>
        </template>
      </div>
    </ElDrawer>

    <ElDialog v-model="rechargeVisible" title="现场现金充值" width="420px" destroy-on-close>
      <p v-if="selectedPatient" class="recharge-tip">
        为患者 <strong>{{ selectedPatient.realName }}</strong> 充值到院内账户，充值后可使用余额支付。
      </p>
      <div class="recharge-form">
        <label>充值金额</label>
        <ElInputNumber v-model="rechargeAmount" :min="0.01" :step="10" :precision="2" class="recharge-amount" />
        <label>备注</label>
        <ElInput v-model="rechargeRemark" placeholder="例如：一楼大厅现金充值" />
      </div>
      <template #footer>
        <ElButton @click="rechargeVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="rechargeLoading" @click="submitRecharge">确认充值</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.patient-panel__head h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.patient-panel__head p {
  margin: 0;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.field--keyword {
  width: 240px;
}

.field--date {
  width: 280px;
}

.status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.patient-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.patient-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 180px;
  padding: 12px 14px;
  border: 1px solid var(--border-subtle, #e2e8f0);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  text-align: left;
}

.patient-card--active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}

.patient-card strong {
  font-size: 15px;
}

.patient-card span {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.selected-patient {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(59, 130, 246, 0.06);
}

.selected-patient__meta {
  margin-left: 8px;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
}

.selected-patient__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.pagination-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-footer {
  margin: 0;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
}

.detail-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  line-height: 1.6;
}

.detail-summary p {
  margin: 0;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-table {
  width: 100%;
}

.muted {
  color: var(--text-secondary, #94a3b8);
}

.recharge-tip {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.6;
}

.recharge-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recharge-amount {
  width: 100%;
}
</style>
