<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, Coin } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'
import {
  formatMoney,
  formatPaymentTime,
  itemCodeLabel,
  itemStatusText,
  itemStatusTone,
  orderStatusText,
  orderStatusTone,
} from '@/shared/utils/paymentStatus'
import type { PaymentOrder } from '@/shared/types/payment'
import { useAdminPaymentCharge } from '@/modules/admin/composables/useAdminPaymentCharge'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const registerId = computed(() => Number(route.params.registerId))

const operator = computed(() => ({
  operatorId: authStore.employeeId ?? undefined,
  operatorName: authStore.realName || '现场收费窗口',
}))

const {
  detailLoading,
  detail,
  loadError,
  patientBalance,
  rechargeVisible,
  rechargeAmount,
  rechargeRemark,
  rechargeLoading,
  payingItemId,
  payingAll,
  markingPaid,
  pendingItems,
  settledItems,
  loadDetail,
  openRechargeDialog,
  submitRecharge,
  confirmMarkItemPaid,
  confirmPayItemByBalance,
  confirmPayAllByBalance,
  confirmMarkAllPaid,
} = useAdminPaymentCharge(operator)

const amountStats = computed(() => {
  if (!detail.value) return []
  return [
    { key: 'total', label: '合计', value: detail.value.totalAmount, tone: 'primary', icon: 'total' },
    { key: 'paid', label: '已缴', value: detail.value.paidAmount, tone: 'success', icon: 'paid' },
    { key: 'pending', label: '待缴', value: detail.value.pendingAmount, tone: 'warning', icon: 'pending' },
    { key: 'balance', label: '余额', value: patientBalance.value, tone: 'purple', icon: 'balance' },
  ]
})

function closePage() {
  router.push({ name: 'PaymentBillManagement' })
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
  if (!registerId.value || Number.isNaN(registerId.value)) {
    ElMessage.error('无效的挂号号')
    closePage()
    return
  }
  await loadDetail(registerId.value)
  if (loadError.value) {
    ElMessage.warning(loadError.value)
  }
})
</script>

<template>
  <div class="charge-page">
    <header class="charge-topbar">
      <button type="button" class="charge-close" @click="closePage">
        <ElIcon :size="16"><Close /></ElIcon>
        关闭
      </button>
      <h1 class="charge-topbar__title">
        现场收费
        <span v-if="detail || registerId">· 挂号 {{ detail?.registerId ?? registerId }}</span>
      </h1>
    </header>

    <div class="charge-body">
      <template v-if="loadError && !detail">
        <section class="charge-panel charge-panel--center">
          <ElEmpty :description="loadError">
            <ElButton type="primary" class="charge-btn charge-btn--primary" @click="closePage">
              返回列表
            </ElButton>
          </ElEmpty>
        </section>
      </template>

      <template v-else-if="detail">
        <section class="charge-patient" v-loading="detailLoading">
          <div class="charge-patient__avatar" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v1.2c0 .7.5 1.2 1.2 1.2h16.8c.7 0 1.2-.5 1.2-1.2v-1.2c0-3.2-6.4-4.8-9.6-4.8z" />
            </svg>
          </div>

          <div class="charge-patient__main">
            <div class="charge-patient__meta">
              <span class="charge-patient__name">患者：{{ detail.patientName || '—' }}</span>
              <span class="charge-patient__visit">就诊：{{ visitMeta(detail) }}</span>
              <StatusTag :tone="orderStatusTone(detail)">{{ orderStatusText(detail) }}</StatusTag>
            </div>

            <div class="charge-amounts">
              <div
                v-for="item in amountStats"
                :key="item.key"
                class="charge-amount"
                :data-tone="item.tone"
              >
                <span class="charge-amount__icon" :data-icon="item.icon" aria-hidden="true">
                  <svg v-if="item.icon === 'total'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
                    <rect x="9" y="3" width="6" height="4" rx="1" />
                  </svg>
                  <svg v-else-if="item.icon === 'paid'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="5" width="20" height="14" rx="2" />
                    <path d="M2 10h20" />
                  </svg>
                  <svg v-else-if="item.icon === 'pending'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
                    <path d="M9 12h6M9 16h4" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="6" width="20" height="12" rx="2" />
                    <circle cx="12" cy="12" r="2" />
                  </svg>
                </span>
                <div class="charge-amount__text">
                  <span class="charge-amount__label">{{ item.label }}</span>
                  <strong class="charge-amount__value">{{ formatMoney(item.value) }}</strong>
                </div>
              </div>
            </div>
          </div>

          <button type="button" class="charge-recharge" @click="openRechargeDialog">
            <ElIcon :size="18"><Coin /></ElIcon>
            现金充值
          </button>
        </section>

        <section class="charge-panel" v-loading="detailLoading">
          <header class="charge-panel__head">
            <h2 class="charge-panel__title">待缴费 ({{ pendingItems.length }})</h2>
            <div v-if="pendingItems.length" class="charge-panel__actions">
              <ElButton
                type="primary"
                class="charge-btn charge-btn--primary"
                :loading="payingAll"
                @click="confirmPayAllByBalance"
              >
                余额支付全部
              </ElButton>
              <ElButton
                class="charge-btn charge-btn--ghost"
                :loading="markingPaid"
                @click="confirmMarkAllPaid"
              >
                现场收费（全部）
              </ElButton>
            </div>
          </header>

          <ElTable v-if="pendingItems.length" :data="pendingItems" class="charge-table">
            <ElTableColumn label="费用项" min-width="180">
              <template #default="{ row }">
                {{ row.itemName || itemCodeLabel(row.itemCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="金额" width="130" align="right">
              <template #default="{ row }">
                <span class="charge-table__money">{{ formatMoney(row.totalAmount) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="110">
              <template #default="{ row }">
                <StatusTag :tone="itemStatusTone(row.status)">{{ itemStatusText(row.status) }}</StatusTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" min-width="200" fixed="right" align="center">
              <template #default="{ row }">
                <button
                  type="button"
                  class="charge-link charge-link--primary"
                  :disabled="payingItemId === row.id"
                  @click="confirmPayItemByBalance(row)"
                >
                  {{ payingItemId === row.id ? '支付中…' : '余额支付' }}
                </button>
                <button
                  type="button"
                  class="charge-link charge-link--warning"
                  :disabled="markingPaid"
                  @click="confirmMarkItemPaid(row)"
                >
                  现场收费
                </button>
              </template>
            </ElTableColumn>
          </ElTable>

          <div v-else class="charge-panel__empty">
            <div class="charge-empty-illus" aria-hidden="true">
              <svg viewBox="0 0 120 120" fill="none">
                <rect x="28" y="20" width="64" height="80" rx="8" fill="#eff6ff" stroke="#bfdbfe" stroke-width="2" />
                <path d="M44 40h32M44 52h24M44 64h28" stroke="#93c5fd" stroke-width="3" stroke-linecap="round" />
                <circle cx="88" cy="88" r="18" fill="#3b82f6" opacity="0.15" />
              </svg>
            </div>
            <p>暂无待缴费用</p>
          </div>
        </section>

        <section class="charge-panel" v-loading="detailLoading">
          <header class="charge-panel__head">
            <h2 class="charge-panel__title">已缴 / 已退 ({{ settledItems.length }})</h2>
          </header>

          <ElTable v-if="settledItems.length" :data="settledItems" class="charge-table">
            <ElTableColumn label="费用项" min-width="180">
              <template #default="{ row }">
                {{ row.itemName || itemCodeLabel(row.itemCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="金额" width="130" align="right">
              <template #default="{ row }">
                <span class="charge-table__money">{{ formatMoney(row.totalAmount) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="110">
              <template #default="{ row }">
                <StatusTag :tone="itemStatusTone(row.status)">{{ itemStatusText(row.status) }}</StatusTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="支付时间" width="168">
              <template #default="{ row }">
                {{ formatPaymentTime(row.payTime) || formatPaymentTime(row.refundTime) || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作员" min-width="100">
              <template #default="{ row }">{{ row.operatorName || '—' }}</template>
            </ElTableColumn>
          </ElTable>

          <div v-else class="charge-panel__empty">
            <div class="charge-empty-illus" aria-hidden="true">
              <svg viewBox="0 0 120 120" fill="none">
                <rect x="28" y="20" width="64" height="80" rx="8" fill="#eff6ff" stroke="#bfdbfe" stroke-width="2" />
                <path d="M60 44v24M48 56h24" stroke="#60a5fa" stroke-width="4" stroke-linecap="round" />
                <circle cx="88" cy="88" r="18" fill="#3b82f6" opacity="0.12" />
              </svg>
            </div>
            <p>暂无已缴或已退费用</p>
          </div>
        </section>
      </template>
    </div>

    <ElDialog
      v-model="rechargeVisible"
      title="现场现金充值"
      width="420px"
      destroy-on-close
      class="charge-recharge-dialog"
    >
      <p v-if="detail" class="recharge-tip">
        为患者 <strong>{{ detail.patientName }}</strong> 充值到院内账户，充值后可使用余额支付。
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
.charge-page {
  position: fixed;
  inset: 0;
  z-index: 1500;
  overflow-y: auto;
  background: linear-gradient(160deg, #f0f4f8 0%, #e8eef5 45%, #f4f7fb 100%);
  padding: 20px 28px 32px;
}

.charge-topbar {
  display: flex;
  align-items: center;
  gap: 20px;
  max-width: 1180px;
  margin: 0 auto 20px;
}

.charge-close {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #dbe4ee;
  border-radius: 10px;
  background: #fff;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.charge-close:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.charge-topbar__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.charge-body {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.charge-patient {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 20px;
  padding: 22px 24px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e8edf3;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}

.charge-patient__avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  color: #3b82f6;
  flex-shrink: 0;
}

.charge-patient__avatar svg {
  width: 32px;
  height: 32px;
}

.charge-patient__main {
  flex: 1 1 320px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.charge-patient__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  font-size: 14px;
  color: #334155;
}

.charge-patient__name {
  font-weight: 600;
  color: #0f172a;
}

.charge-patient__visit {
  color: #64748b;
}

.charge-amounts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
}

.charge-amount {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 120px;
}

.charge-amount__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--amount-icon-bg, #eff6ff);
  color: var(--amount-icon-color, #3b82f6);
  flex-shrink: 0;
}

.charge-amount__icon svg {
  width: 20px;
  height: 20px;
}

.charge-amount[data-tone='primary'] {
  --amount-icon-bg: #eff6ff;
  --amount-icon-color: #3b82f6;
}

.charge-amount[data-tone='success'] {
  --amount-icon-bg: #ecfdf5;
  --amount-icon-color: #10b981;
}

.charge-amount[data-tone='warning'] {
  --amount-icon-bg: #fff7ed;
  --amount-icon-color: #f59e0b;
}

.charge-amount[data-tone='purple'] {
  --amount-icon-bg: #f5f3ff;
  --amount-icon-color: #8b5cf6;
}

.charge-amount__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.charge-amount__label {
  font-size: 12px;
  color: #94a3b8;
}

.charge-amount__value {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.charge-recharge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  padding: 12px 22px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
  transition: transform 0.15s, box-shadow 0.15s;
  align-self: center;
}

.charge-recharge:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.4);
}

.charge-panel {
  padding: 20px 24px 24px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e8edf3;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}

.charge-panel--center {
  padding: 48px 24px;
}

.charge-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.charge-panel__title {
  margin: 0;
  padding-left: 12px;
  border-left: 4px solid #3b82f6;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.charge-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.charge-btn {
  border-radius: 10px;
  font-weight: 600;
  padding-inline: 16px;
}

.charge-btn--ghost {
  --el-button-bg-color: #fff;
  --el-button-border-color: #dbe4ee;
  --el-button-text-color: #334155;
}

.charge-table {
  width: 100%;
}

.charge-table__money {
  font-weight: 600;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.charge-link {
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
  transition: background 0.15s;
}

.charge-link + .charge-link {
  margin-left: 4px;
}

.charge-link--primary {
  color: #2563eb;
}

.charge-link--primary:hover:not(:disabled) {
  background: #eff6ff;
}

.charge-link--warning {
  color: #ea580c;
}

.charge-link--warning:hover:not(:disabled) {
  background: #fff7ed;
}

.charge-link:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.charge-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 16px;
  color: #94a3b8;
  font-size: 14px;
}

.charge-panel__empty p {
  margin: 0;
}

.charge-empty-illus svg {
  width: 100px;
  height: 100px;
}

.charge-table :deep(.el-table__header th) {
  background: #f8fafc !important;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #eef2f6;
}

.charge-table :deep(.el-table__body td) {
  font-size: 14px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
  padding-block: 14px;
}

.charge-table :deep(.el-table__row:hover > td) {
  background: #f8fbff !important;
}

.charge-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.recharge-tip {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
}

.recharge-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recharge-form label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.recharge-amount {
  width: 100%;
}

@media (max-width: 768px) {
  .charge-page {
    padding: 16px;
  }

  .charge-topbar__title {
    font-size: 20px;
  }

  .charge-recharge {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }

  .charge-patient {
    flex-direction: column;
  }
}
</style>
