import { http } from '../request'
import type {
  DispensePayload,
  DispenseResult,
  Dispensing,
  DrugInboundPayload,
  DrugOption,
  DrugQuery,
  DrugStock,
  DrugStockUpdatePayload,
  ExpiringStockItem,
  FollowUpFeedback,
  FollowUpPlan,
  MedicationGuide,
  PendingPrescriptionQuery,
  PharmacyTransaction,
  PrescriptionQuery,
  PharmacyTransactionQuery,
  PrescriptionDetailResponse,
  PrescriptionSummary,
  StatisticsResult,
  ReturnDrugPayload,
  ReviewResult,
} from '@/shared/types/pharmacy'

export const pharmacyApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'PUT', data })
  },
  delete<T>(url: string) {
    return http<T>({ url, method: 'DELETE' })
  },
  pendingPrescriptions(params?: PendingPrescriptionQuery) {
    return http<PrescriptionSummary[]>({ url: '/pharmacy/pending', method: 'GET', params })
  },
  queryPrescriptions(params?: PrescriptionQuery) {
    return http<PrescriptionSummary[]>({ url: '/pharmacy/prescriptions', method: 'GET', params })
  },
  prescriptionDetail(prescriptionId: number) {
    return http<PrescriptionDetailResponse>({ url: `/pharmacy/prescription/${prescriptionId}`, method: 'GET' })
  },
  dispense(registerId: number, data?: DispensePayload) {
    return http<DispenseResult>({ url: `/pharmacy/dispense/${registerId}`, method: 'PUT', data })
  },
  returnDrug(registerId: number, data?: ReturnDrugPayload) {
    return http<void>({ url: `/pharmacy/return/${registerId}`, method: 'PUT', data })
  },
  /** P1-4.1 发药前审核 */
  review(registerId: number) {
    return http<ReviewResult>({ url: `/pharmacy/dispense/${registerId}/review`, method: 'POST' })
  },
  drugs(params?: DrugQuery) {
    return http<DrugOption[]>({ url: '/pharmacy/drugs', method: 'GET', params })
  },
  /** P1-4.3 查询所有已用药品分类 */
  categories() {
    return http<string[]>({ url: '/pharmacy/drugs/categories', method: 'GET' })
  },
  drugDetail(id: number) {
    return http<DrugOption>({ url: `/pharmacy/drugs/${id}`, method: 'GET' })
  },
  createDrug(data: DrugOption) {
    return http<DrugOption>({ url: '/pharmacy/drugs', method: 'POST', data })
  },
  updateDrug(id: number, data: DrugOption) {
    return http<void>({ url: `/pharmacy/drugs/${id}`, method: 'PUT', data })
  },
  deleteDrug(id: number) {
    return http<void>({ url: `/pharmacy/drugs/${id}`, method: 'DELETE' })
  },
  lowStockDrugs() {
    return http<DrugOption[]>({ url: '/pharmacy/drugs/low-stock', method: 'GET' })
  },
  inventory(drugId: number) {
    return http<DrugStock[]>({ url: `/pharmacy/inventory/${drugId}`, method: 'GET' })
  },
  inbound(drugId: number, data: DrugInboundPayload) {
    return http<void>({ url: `/pharmacy/inventory/${drugId}/inbound`, method: 'POST', data })
  },
  updateStock(drugId: number, data: DrugStockUpdatePayload) {
    return http<void>({ url: `/pharmacy/inventory/${drugId}`, method: 'PUT', data })
  },
  /** P1-4.2 近效期批次查询 */
  expiringStock(days = 30) {
    return http<ExpiringStockItem[]>({ url: '/pharmacy/inventory/expiring', method: 'GET', params: { days } })
  },
  transactions(params?: PharmacyTransactionQuery) {
    return http<PharmacyTransaction[]>({ url: '/pharmacy/transactions', method: 'GET', params })
  },
  /** P1-6.1 生成用药指导 */
  medicationGuide(drugId: number) {
    return http<MedicationGuide>({ url: `/pharmacy/drugs/${drugId}/guide`, method: 'POST' })
  },
  /** P1-6.2 患者随访计划 */
  patientFollowUpPlans(patientId: number) {
    return http<FollowUpPlan[]>({ url: `/pharmacy/followup/patient/${patientId}`, method: 'GET' })
  },
  /** P2-6.3 重试创建随访 */
  retryFollowUp(prescriptionId: number) {
    return http<Record<string, unknown>>({ url: `/pharmacy/followup/retry/${prescriptionId}`, method: 'POST' })
  },
  /** P2-6.4 录入随访反馈 */
  submitFollowUpFeedback(planId: number, data: FollowUpFeedback) {
    return http<void>({ url: `/pharmacy/followup/${planId}/feedback`, method: 'POST', data })
  },
  /** P2-4.6 查询发药单 */
  dispensingByRegister(registerId: number) {
    return http<Dispensing[]>({ url: `/pharmacy/dispensing/${registerId}`, method: 'GET' })
  },
  /** P1-8 统计报表 */
  statistics(params?: { startDate?: string; endDate?: string; topLimit?: number }) {
    return http<StatisticsResult>({ url: '/pharmacy/statistics', method: 'GET', params })
  },
}
