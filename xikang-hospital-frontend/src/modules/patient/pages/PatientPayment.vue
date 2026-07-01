<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'
import { paymentApi } from '@/shared/api/modules/payment'
import type { OrderStatusFilter, PaymentOrder } from '@/shared/types/payment'
import {
  formatPaymentTime,
  itemCodeLabel,
  itemStatusText,
  itemStatusTone,
  orderStatusText,
  orderStatusTone,
} from '@/shared/utils/paymentStatus'

const authStore = useAuthStore()

const loading = ref(false)
const orders = ref<PaymentOrder[]>([])
const statusFilter = ref<OrderStatusFilter | undefined>(undefined)

// 分页
const PAGE_SIZE = 4
const page = ref(1)
const pagedOrders = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return orders.value.slice(start, start + PAGE_SIZE)
})

// 选中订单的详情
const selectedRegisterId = ref<number | null>(null)
const detail = ref<PaymentOrder | null>(null)
const detailLoading = ref(false)

// 支付按钮 loading
const payingItemId = ref<number | null>(null)
const payingAllRegisterId = ref<number | null>(null)

const patientId = computed(() => authStore.currentPatientId)
const currentPatient = computed(() => authStore.currentPatient)
const isFamily = computed(() => {
  const p = currentPatient.value
  if (!p) return false
  return p.isPrimary !== 1 && !!p.relation && p.relation !== '本人'
})
const relationLabel = computed(() => currentPatient.value?.relation || '家属')

const showFamilyTabs = computed(() => (authStore.patients?.length ?? 0) > 1)

const statusTabs: { label: string; value: OrderStatusFilter | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '待缴费', value: 0 },
  { label: '已付清', value: 1 },
  { label: '含已退', value: 2 },
]

async function load() {
  if (!patientId.value) {
    ElMessage.warning('未找到患者档案，请重新登录')
    return
  }
  loading.value = true
  selectedRegisterId.value = null
  detail.value = null
  page.value = 1
  try {
    const result = await paymentApi.listOrders(patientId.value, statusFilter.value)
    orders.value = result?.orders ?? []
  } catch (e) {
    orders.value = []
    const msg = e instanceof Error ? e.message : '加载失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

async function selectOrder(o: PaymentOrder) {
  if (selectedRegisterId.value === o.registerId) return
  selectedRegisterId.value = o.registerId
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await paymentApi.getOrderDetail(o.registerId, patientId.value!)
  } catch (e) {
    detail.value = o
    const msg = e instanceof Error ? e.message : '加载明细失败'
    ElMessage.warning(msg)
  } finally {
    detailLoading.value = false
  }
}

async function switchTo(pid: number) {
  if (pid === patientId.value) return
  authStore.switchPatient(pid)
  await load()
}

async function payItem(itemId: number, itemName?: string) {
  if (!selectedRegisterId.value) return
  try {
    await ElMessageBox.confirm(
      `确认支付「${itemName ?? '费用项'}」？将从账户余额扣款。`,
      '支付确认',
      { type: 'warning', confirmButtonText: '确认支付', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payingItemId.value = itemId
  try {
    const r = await paymentApi.payItem(selectedRegisterId.value, itemId)
    if (r?.success) {
      ElMessage.success(r.paymentMessage || '支付成功')
      // 刷新明细 + 列表
      await Promise.all([
        paymentApi.getOrderDetail(selectedRegisterId.value, patientId.value!).then(d => { detail.value = d }),
        load(),
      ])
    } else {
      ElMessage.error(r?.paymentMessage || '支付失败')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '支付失败')
  } finally {
    payingItemId.value = null
  }
}

async function payAll(o: PaymentOrder) {
  if (o.pendingAmount <= 0) {
    ElMessage.info('该订单已无待缴费用')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认支付订单合计 ¥${o.pendingAmount.toFixed(2)} ？将从账户余额扣款。`,
      '一键支付确认',
      { type: 'warning', confirmButtonText: '确认支付', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payingAllRegisterId.value = o.registerId
  try {
    const r = await paymentApi.payAll(o.registerId)
    if (r.success) {
      ElMessage.success(`成功支付 ${r.paidCount} 项，共 ¥${(r.totalAmount ?? 0).toFixed(2)}`)
      if (selectedRegisterId.value === o.registerId) {
        await paymentApi.getOrderDetail(o.registerId, patientId.value!).then(d => { detail.value = d })
      }
      await load()
    } else {
      ElMessage.error(r.paymentMessage || '支付失败')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '支付失败')
  } finally {
    payingAllRegisterId.value = null
  }
}

// 元数据行：科室 / 医生 / 就诊日期 任意非空才显示，避免出现 "— · — · —"
function orderMetaParts(o: PaymentOrder): string[] {
  const parts: string[] = []
  if (o.departmentName) parts.push(o.departmentName)
  if (o.doctorName) parts.push(o.doctorName)
  const vd = formatPaymentTime(o.visitDate)
  if (vd) parts.push(vd)
  return parts
}

function itemCodeName(code?: string): string {
  return itemCodeLabel(code)
}

watch(() => authStore.currentPatientId, (cur, prev) => {
  if (cur && cur !== prev) void load()
})

onMounted(load)
</script>

<template>
  <div class="payment-page">
    <div class="list-toolbar">
      <div>
        <h2>我的账单</h2>
        <p>按挂号号聚合查看费用明细，余额支付后即时同步</p>
      </div>
      <ElButton type="primary" :loading="loading" @click="load">刷新</ElButton>
    </div>

      <!-- 家属切换 Tab -->
      <div v-if="showFamilyTabs" class="family-tabs">
        <ElButton
          v-for="p in authStore.patients"
          :key="p.patientId"
          :type="p.patientId === patientId ? 'primary' : 'default'"
          size="small"
          round
          @click="switchTo(p.patientId)"
        >
          {{ p.realName || `就诊人 ${p.patientId}` }}
          <span v-if="p.isPrimary !== 1 && p.relation" class="family-tabs__relation">
            （{{ p.relation }}）
          </span>
        </ElButton>
      </div>

      <!-- 状态筛选 Tab -->
      <div class="status-tabs">
        <ElButton
          v-for="t in statusTabs"
          :key="String(t.value)"
          :type="statusFilter === t.value ? 'primary' : 'default'"
          size="small"
          text
          @click="statusFilter = t.value; load()"
        >
          {{ t.label }}
        </ElButton>
      </div>

      <ElAlert v-if="loading && !orders.length" type="info" :closable="false" title="正在加载…" />

      <div v-else class="split-grid">
        <!-- 左栏：订单列表（按挂号维度） -->
        <div class="pane pane--list">
          <div class="pane__title">
            <span>账单列表</span>
            <StatusTag v-if="orders.length" tone="primary">{{ orders.length }} 个订单</StatusTag>
            <StatusTag v-if="isFamily" tone="ai">{{ relationLabel }}</StatusTag>
          </div>

          <ElEmpty
            v-if="!orders.length"
            description="该就诊人暂无账单"
            :image-size="120"
          />

          <div v-else class="order-list">
            <div
              v-for="o in pagedOrders"
              :key="o.registerId"
              class="order-card"
              :class="{ 'order-card--active': selectedRegisterId === o.registerId }"
              @click="selectOrder(o)"
            >
              <div class="order-card__head">
                <span class="order-card__id">挂号 {{ o.registerId }}</span>
                <StatusTag :tone="orderStatusTone(o)">{{ orderStatusText(o) }}</StatusTag>
              </div>
              <div class="order-card__meta">
                <span v-for="(p, i) in orderMetaParts(o)" :key="i">
                  <span v-if="i > 0" class="meta-sep"> · </span>{{ p }}
                </span>
                <span v-if="orderMetaParts(o).length === 0" class="meta-empty">费用记录</span>
              </div>
              <div class="order-card__amount">
                <span class="amount-label">合计</span>
                <span class="amount-value">¥ {{ (o.totalAmount ?? 0).toFixed(2) }}</span>
                <span v-if="o.pendingAmount > 0" class="amount-pending">
                  待缴 ¥ {{ o.pendingAmount.toFixed(2) }}
                </span>
                <span v-else class="amount-paid">已结清</span>
              </div>
              <div v-if="o.pendingAmount > 0" class="order-card__action">
                <ElButton
                  size="small"
                  type="primary"
                  :loading="payingAllRegisterId === o.registerId"
                  @click.stop="payAll(o)"
                >
                  一键支付
                </ElButton>
              </div>
            </div>
          </div>

          <div v-if="orders.length > PAGE_SIZE" class="pagination-row">
            <ElPagination
              v-model:current-page="page"
              :total="orders.length"
              :page-size="PAGE_SIZE"
              layout="prev, pager, next"
              small
              background
            />
          </div>
        </div>

        <!-- 右栏：订单明细（平铺） -->
        <div class="pane pane--detail">
          <ElEmpty
            v-if="!detail && !detailLoading"
            description="请从左侧选择订单查看明细"
            :image-size="140"
          />
          <ElAlert v-else-if="detailLoading" type="info" :closable="false" title="正在加载明细…" />

          <template v-else-if="detail">
            <div class="pane__title">
              <span>订单明细 · 挂号 {{ detail.registerId }}</span>
              <StatusTag :tone="orderStatusTone(detail)">{{ orderStatusText(detail) }}</StatusTag>
            </div>

            <ElDescriptions :column="2" border size="small" class="detail-head">
              <ElDescriptionsItem v-if="detail.departmentName" label="科室">{{ detail.departmentName }}</ElDescriptionsItem>
              <ElDescriptionsItem v-if="detail.doctorName" label="医生">{{ detail.doctorName }}</ElDescriptionsItem>
              <ElDescriptionsItem v-if="formatPaymentTime(detail.visitDate)" label="就诊日期">{{ formatPaymentTime(detail.visitDate) }}</ElDescriptionsItem>
              <ElDescriptionsItem v-if="formatPaymentTime(detail.payTime)" label="支付时间">{{ formatPaymentTime(detail.payTime) }}</ElDescriptionsItem>
              <ElDescriptionsItem label="合计金额">
                <span class="amount-value">¥ {{ (detail.totalAmount ?? 0).toFixed(2) }}</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="已缴金额">
                <span class="amount-paid">¥ {{ (detail.paidAmount ?? 0).toFixed(2) }}</span>
              </ElDescriptionsItem>
            </ElDescriptions>

            <div class="detail-table">
              <ElTable :data="detail.items" size="small" stripe>
                <ElTableColumn label="项目" prop="itemName" min-width="160">
                  <template #default="{ row }">
                    <div class="item-name">{{ row.itemName || itemCodeName(row.itemCode) || '—' }}</div>
                    <div v-if="row.itemCode" class="item-code">{{ itemCodeName(row.itemCode) }}</div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="类别" prop="categoryName" width="100" />
                <ElTableColumn label="数量" prop="quantity" width="60" align="center" />
                <ElTableColumn label="单价" width="90" align="right">
                  <template #default="{ row }">¥ {{ (row.unitPrice ?? 0).toFixed(2) }}</template>
                </ElTableColumn>
                <ElTableColumn label="金额" width="100" align="right">
                  <template #default="{ row }">
                    <span class="amount-value">¥ {{ (row.totalAmount ?? 0).toFixed(2) }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <StatusTag :tone="itemStatusTone(row.status)">{{ itemStatusText(row.status) }}</StatusTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <ElButton
                      v-if="row.status === 0"
                      size="small"
                      type="primary"
                      :loading="payingItemId === row.id"
                      @click="payItem(row.id, row.itemName)"
                    >
                      支付
                    </ElButton>
                    <ElButton
                      v-else
                      size="small"
                      type="info"
                      disabled
                    >
                      已支付
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>

            <div v-if="detail.pendingAmount > 0" class="detail-action">
              <ElButton
                type="primary"
                :loading="payingAllRegisterId === detail.registerId"
                @click="payAll(detail)"
              >
                一键支付剩余 ¥ {{ detail.pendingAmount.toFixed(2) }}
              </ElButton>
            </div>
          </template>
        </div>
      </div>
  </div>
</template>

<style scoped>
/* 与 PatientRegistration 同款大卡片容器 */
.payment-page {
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-8);
  background: var(--color-surface);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
}

.list-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.list-toolbar h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 var(--space-2);
  color: var(--color-text);
}

.list-toolbar p {
  color: var(--color-text-muted);
  margin: 0;
  font-size: 13px;
}

/* 家属切换 Tab */
.family-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  margin-bottom: var(--space-3);
}

.family-tabs__relation {
  font-size: 12px;
  opacity: 0.85;
}

/* 状态筛选 Tab */
.status-tabs {
  display: flex;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

/* 双栏：左列表窄，右明细宽（2:3），给右栏表格留足空间避免被裁 */
.split-grid {
  display: grid;
  grid-template-columns: 2fr 3fr;
  gap: var(--space-5);
  align-items: start;
}

.pane {
  min-width: 0;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  padding: var(--space-4);
  min-height: 360px;
  display: flex;
  flex-direction: column;
}

.pane__title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 15px;
  font-weight: 600;
  margin-bottom: var(--space-3);
}

/* 左栏列表 */
.order-list {
  display: grid;
  gap: var(--space-3);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.order-card {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: var(--space-3);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  background: var(--color-bg, #fff);
}

.order-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}

.order-card--active {
  border-color: var(--color-primary);
  background: var(--color-primary-bg, rgba(64, 158, 255, 0.08));
}

.order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.order-card__id {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text);
}

.order-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-2);
}

.meta-sep {
  color: var(--color-text-muted);
  opacity: 0.6;
}

.meta-empty {
  color: var(--color-text-muted);
  opacity: 0.7;
}

.order-card__amount {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.amount-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.amount-value {
  font-weight: 600;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.amount-pending {
  font-size: 12px;
  color: var(--color-warning, #e6a23c);
  font-weight: 600;
}

.amount-paid {
  font-size: 12px;
  color: var(--color-success, #67c23a);
  font-weight: 600;
}

.order-card__action {
  display: flex;
  justify-content: flex-end;
}

/* 右栏明细 */
.detail-head {
  margin-bottom: var(--space-3);
}

.detail-table {
  margin-bottom: var(--space-3);
  /* 防止表格撑爆 grid 子项；列太多时改为内部横向滚动 */
  min-width: 0;
  overflow-x: auto;
}

.item-code {
  font-size: 11px;
  color: var(--color-text-muted);
}

.item-name {
  font-weight: 500;
}

.detail-action {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
}

/* 响应式 */
@media (max-width: 768px) {
  .payment-page {
    width: 95%;
    padding: var(--space-5);
  }

  .split-grid {
    grid-template-columns: 1fr;
  }

  .pane--detail {
    min-height: 240px;
  }
}
</style>
