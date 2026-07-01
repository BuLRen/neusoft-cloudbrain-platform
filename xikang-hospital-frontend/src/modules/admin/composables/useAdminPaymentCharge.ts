import { computed, ref, type ComputedRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminPaymentApi } from '@/shared/api/modules/adminPayment'
import type { PaymentItem, PaymentOrder } from '@/shared/types/payment'
import { formatMoney, itemCodeLabel } from '@/shared/utils/paymentStatus'

export interface PaymentOperator {
  operatorId?: number
  operatorName?: string
}

function isPendingItem(item: PaymentItem) {
  return item.status === 0 || item.status == null
}

export function useAdminPaymentCharge(operator: ComputedRef<PaymentOperator>) {
  const detailLoading = ref(false)
  const detail = ref<PaymentOrder | null>(null)
  const loadError = ref<string | null>(null)
  const patientBalance = ref(0)

  const rechargeVisible = ref(false)
  const rechargeAmount = ref(100)
  const rechargeRemark = ref('现场窗口现金充值')
  const rechargeLoading = ref(false)

  const payingItemId = ref<number | null>(null)
  const payingAll = ref(false)
  const markingPaid = ref(false)

  const pendingItems = computed(() => (detail.value?.items ?? []).filter(isPendingItem))
  const settledItems = computed(() => (detail.value?.items ?? []).filter((item) => !isPendingItem(item)))

  async function refreshPatientBalance(patientId: number) {
    try {
      const balance = await adminPaymentApi.getPatientBalance(patientId)
      patientBalance.value = Number(balance.accountBalance ?? 0)
    } catch {
      patientBalance.value = 0
    }
  }

  async function loadDetail(registerId: number) {
    detailLoading.value = true
    loadError.value = null
    try {
      detail.value = await adminPaymentApi.getDetail(registerId)
      if (detail.value.patientId) {
        await refreshPatientBalance(detail.value.patientId)
      } else {
        patientBalance.value = 0
      }
    } catch (e) {
      detail.value = null
      loadError.value = e instanceof Error ? e.message : '加载明细失败'
    } finally {
      detailLoading.value = false
    }
  }

  async function reloadDetail() {
    if (!detail.value) return
    await loadDetail(detail.value.registerId)
  }

  function openRechargeDialog() {
    if (!detail.value?.patientId) {
      ElMessage.warning('未找到患者信息，无法充值')
      return
    }
    rechargeAmount.value = Math.max(100, detail.value.pendingAmount ?? 100)
    rechargeRemark.value = '现场窗口现金充值'
    rechargeVisible.value = true
  }

  async function submitRecharge() {
    const patientId = detail.value?.patientId
    if (!patientId) return
    if (!rechargeAmount.value || rechargeAmount.value <= 0) {
      ElMessage.warning('请输入有效充值金额')
      return
    }
    rechargeLoading.value = true
    try {
      const result = await adminPaymentApi.rechargePatient(
        patientId,
        rechargeAmount.value,
        rechargeRemark.value,
        operator.value,
      )
      patientBalance.value = Number(result.accountBalance ?? 0)
      ElMessage.success(result.message || '充值成功')
      rechargeVisible.value = false
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '充值失败')
    } finally {
      rechargeLoading.value = false
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
      await reloadDetail()
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
      await reloadDetail()
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
      await reloadDetail()
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
      await reloadDetail()
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '收费失败')
    } finally {
      markingPaid.value = false
    }
  }

  return {
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
    reloadDetail,
    refreshPatientBalance,
    openRechargeDialog,
    submitRecharge,
    confirmMarkItemPaid,
    confirmPayItemByBalance,
    confirmPayAllByBalance,
    confirmMarkAllPaid,
  }
}
