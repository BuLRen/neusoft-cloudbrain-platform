import { http } from '../request'
import type {
  PayAllResult,
  PayItemResult,
  PaymentOrder,
  PaymentOrderList,
  OrderStatusFilter,
} from '@/shared/types/payment'

/**
 * Payment API（v3.2 §4.1 患者端 API）
 *
 * 所有路径走 gateway → payment-service 的 /api/payment/orders/**。
 * JWT 由 request 拦截器自动添加。
 */
export const paymentApi = {
  /**
   * 我的账单列表（按挂号号聚合）
   * @param patientId 患者档案 ID（必传，由 authStore.currentPatientId 提供）
   * @param statusFilter 0 待缴 / 1 已付清 / 2 含已退；undefined = 全部
   * @param page 页码（1-based）
   * @param size 每页订单数（默认 20）
   */
  listOrders(patientId: number, statusFilter?: OrderStatusFilter, page = 1, size = 20) {
    return http<PaymentOrderList>({
      url: '/payment/orders',
      method: 'GET',
      params: { patientId, status: statusFilter, page, size },
    })
  },

  /** 单个挂号下的订单详情（含所有费用行） */
  getOrderDetail(registerId: number, patientId: number) {
    return http<PaymentOrder>({
      url: `/payment/orders/${registerId}`,
      method: 'GET',
      params: { patientId },
    })
  },

  /** 支付单个费用行（一般是药品费） */
  payItem(registerId: number, itemId: number) {
    return http<PayItemResult>({
      url: `/payment/orders/${registerId}/items/${itemId}/pay`,
      method: 'POST',
    })
  },

  /** 一键支付整个订单的所有待缴行 */
  payAll(registerId: number) {
    return http<PayAllResult>({
      url: `/payment/orders/${registerId}/pay-all`,
      method: 'POST',
    })
  },
}
