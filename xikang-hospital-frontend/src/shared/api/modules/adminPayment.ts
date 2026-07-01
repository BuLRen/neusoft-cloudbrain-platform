import { http } from '../request'
import type { OrderStatusFilter, PaymentOrder, PaymentOrderList } from '@/shared/types/payment'

export interface AdminPatientOption {
  id: number
  realName: string
  phone?: string
  gender?: string
  accountBalance?: number
}

export interface AdminRechargeResult {
  success: boolean
  message?: string
  patientId?: number
  accountBalance: number
}

export interface AdminPayResult {
  success?: boolean
  registerId?: number
  itemId?: number
  paidCount?: number
  failedCount?: number
  totalAmount?: number
  accountBalance?: number
  paymentMessage?: string
  itemCount?: number
  payTime?: string
  operatorName?: string
}

export const adminPaymentApi = {
  searchPatients(keyword: string, limit = 20) {
    return http<AdminPatientOption[]>({
      url: '/registration/admin/payment-orders/patients/search',
      method: 'GET',
      params: { keyword, limit },
    })
  },

  getPatientBalance(patientId: number) {
    return http<{ patientId: number; accountBalance: number; balanceUnavailable?: boolean }>({
      url: `/registration/admin/payment-orders/patients/${patientId}/balance`,
      method: 'GET',
      skipErrorMessage: true,
    })
  },

  rechargePatient(patientId: number, amount: number, remark?: string, operator?: { operatorId?: number; operatorName?: string }) {
    return http<AdminRechargeResult>({
      url: `/registration/admin/payment-orders/patients/${patientId}/recharge`,
      method: 'POST',
      data: {
        amount,
        remark,
        operatorId: operator?.operatorId,
        operatorName: operator?.operatorName,
      },
    })
  },

  /** 按挂号号充值：后端从挂号表解析真实 patient_id，避免误用挂号号 */
  rechargeByRegister(registerId: number, amount: number, remark?: string, operator?: { operatorId?: number; operatorName?: string }) {
    return http<AdminRechargeResult>({
      url: `/registration/admin/payment-orders/${registerId}/recharge`,
      method: 'POST',
      data: {
        amount,
        remark,
        operatorId: operator?.operatorId,
        operatorName: operator?.operatorName,
      },
    })
  },

  list(params?: {
    keyword?: string
    patientId?: number
    status?: OrderStatusFilter
    startDate?: string
    endDate?: string
    page?: number
    size?: number
  }) {
    return http<PaymentOrderList>({
      url: '/registration/admin/payment-orders',
      method: 'GET',
      params,
    })
  },

  getDetail(registerId: number) {
    return http<PaymentOrder>({
      url: `/registration/admin/payment-orders/${registerId}`,
      method: 'GET',
    })
  },

  /** 现场现金收费：直接标记为已支付（不扣余额） */
  markPaid(data: {
    registerId: number
    itemIds?: number[]
    operatorId?: number
    operatorName?: string
  }) {
    return http<AdminPayResult>({
      url: '/registration/admin/payment-orders/charge',
      method: 'POST',
      data,
    })
  },

  /** 从患者余额扣款支付单条费用 */
  payItemByBalance(registerId: number, itemId: number, operator?: { operatorId?: number; operatorName?: string }) {
    return http<AdminPayResult>({
      url: `/registration/admin/payment-orders/${registerId}/items/${itemId}/pay`,
      method: 'POST',
      data: operator,
    })
  },

  /** 从患者余额扣款支付全部待缴费用 */
  payAllByBalance(registerId: number, operator?: { operatorId?: number; operatorName?: string }) {
    return http<AdminPayResult>({
      url: `/registration/admin/payment-orders/${registerId}/pay-all`,
      method: 'POST',
      data: operator,
    })
  },
}
