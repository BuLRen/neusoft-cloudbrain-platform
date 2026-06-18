import { http } from '../request'
import type {
  DispensePayload,
  DispenseResult,
  DrugInboundPayload,
  DrugOption,
  DrugStock,
  DrugStockUpdatePayload,
  PendingPrescriptionQuery,
  PharmacyTransaction,
  PharmacyTransactionQuery,
  PrescriptionDetailResponse,
  PrescriptionSummary,
  ReturnDrugPayload,
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
  prescriptionDetail(prescriptionId: number) {
    return http<PrescriptionDetailResponse>({ url: `/pharmacy/prescription/${prescriptionId}`, method: 'GET' })
  },
  dispense(registerId: number, data?: DispensePayload) {
    return http<DispenseResult>({ url: `/pharmacy/dispense/${registerId}`, method: 'PUT', data })
  },
  returnDrug(registerId: number, data?: ReturnDrugPayload) {
    return http<void>({ url: `/pharmacy/return/${registerId}`, method: 'PUT', data })
  },
  drugs(params?: { keyword?: string; dosageForm?: string }) {
    return http<DrugOption[]>({ url: '/pharmacy/drugs', method: 'GET', params })
  },
  drugDetail(id: number) {
    return http<DrugOption>({ url: `/pharmacy/drugs/${id}`, method: 'GET' })
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
  transactions(params?: PharmacyTransactionQuery) {
    return http<PharmacyTransaction[]>({ url: '/pharmacy/transactions', method: 'GET', params })
  },
}
