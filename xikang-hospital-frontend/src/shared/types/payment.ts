/**
 * Payment 模块类型（v3.2 §4.1 患者端 API）
 *
 * 数据结构与后端 payment-service.PaymentService 的 listOrders / getOrderDetail / payItem 对齐。
 */

/** 费用明细行（单条 expense_record） */
export interface PaymentItem {
  id: number
  registerId: number
  patientId?: number
  patientName?: string
  categoryId?: number
  categoryName?: string
  itemId?: number
  itemName?: string
  itemCode?: string
  quantity?: number
  unitPrice?: number
  totalAmount?: number
  status?: number
  statusName?: string
  payTime?: string
  refundTime?: string
  operatorName?: string
  remark?: string
  createTime?: string
}

/** 单个挂号下的订单（按 registerId 聚合） */
export interface PaymentOrder {
  registerId: number
  patientId?: number
  patientName?: string
  departmentName?: string
  doctorName?: string
  visitDate?: string
  items: PaymentItem[]
  totalAmount: number
  paidAmount: number
  pendingAmount: number
  paidItemCount?: number
  pendingItemCount?: number
  status: number // 0 待缴 / 1 已付清 / 2 含已退
  statusName: string
  payTime?: string
  /** 管理端收费页：后端附带的患者余额 */
  accountBalance?: number
  balanceUnavailable?: boolean
}

/** 订单列表响应 */
export interface PaymentOrderList {
  orders: PaymentOrder[]
  total: number
  page: number
  size: number
}

/** 支付单条结果 */
export interface PayItemResult {
  success?: boolean
  registerId?: number
  itemId?: number
  itemName?: string
  amount?: number
  paidAmount?: number
  accountBalance?: number
  payTime?: string
  paymentMessage?: string
}

/** 支付整个订单结果 */
export interface PayAllResult {
  success: boolean
  registerId: number
  paidCount: number
  totalAmount: number
  failedItems?: { itemId: number; itemName?: string; reason: string }[]
  accountBalance?: number
  paymentMessage?: string
}

/** 订单状态过滤：0 待缴 / 1 已付清 / 2 含已退 */
export type OrderStatusFilter = 0 | 1 | 2
