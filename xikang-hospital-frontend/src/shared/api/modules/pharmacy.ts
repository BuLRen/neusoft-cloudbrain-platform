import { http } from '../request'
import axios from 'axios'
import { getAccessToken } from '@/shared/auth/tokenStorage'
import { getAxiosBaseURL } from '@/config/api'

// PDF 下载专用客户端：responseType=blob，自动携带 JWT token
const blobClient = axios.create({
  baseURL: getAxiosBaseURL(),
  timeout: 60_000,
  withCredentials: true,
})
blobClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})
import type {
  BatchInboundItem,
  BatchInboundResult,
  DispensePayload,
  DispenseResult,
  Dispensing,
  DrugInboundPayload,
  DrugOption,
  DrugPageResult,
  DrugQuery,
  DrugStock,
  DrugStockUpdatePayload,
  ExpiringStockItem,
  MedicationGuide,
  MedicationGuideRecord,
  PendingPrescriptionQuery,
  PharmacyTransaction,
  PrescriptionQuery,
  PharmacyTransactionQuery,
  PrescriptionDetailResponse,
  PrescriptionSummary,
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
  /** 药品列表：传 page/pageSize 走分页返回 DrugPageResult；不传走全量（后端 LIMIT 200 兜底） */
  drugs(params?: DrugQuery) {
    return http<DrugPageResult>({ url: '/pharmacy/drugs', method: 'GET', params })
  },
  /** 查询所有已用药品分类（drug_type：西药/中成药/生物制品） */
  categories() {
    return http<string[]>({ url: '/pharmacy/drugs/categories', method: 'GET' })
  },
  /** 查询所有已用药品剂型（drug_dosage），供前端动态下拉 */
  dosageForms() {
    return http<string[]>({ url: '/pharmacy/drugs/dosage-forms', method: 'GET' })
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
  /** 低库存药品：传 page 走分页，不传走全量 */
  lowStockDrugs(params?: { page?: number; pageSize?: number }) {
    return http<DrugPageResult>({ url: '/pharmacy/drugs/low-stock', method: 'GET', params })
  },
  inventory(drugId: number) {
    return http<DrugStock[]>({ url: `/pharmacy/inventory/${drugId}`, method: 'GET' })
  },
  inbound(drugId: number, data: DrugInboundPayload) {
    return http<void>({ url: `/pharmacy/inventory/${drugId}/inbound`, method: 'POST', data })
  },
  /** 批量入库（单事务） */
  batchInbound(items: BatchInboundItem[]) {
    return http<BatchInboundResult>({
      url: '/pharmacy/inventory/batch-inbound',
      method: 'POST',
      data: { items },
    })
  },
  updateStock(drugId: number, data: DrugStockUpdatePayload) {
    return http<void>({ url: `/pharmacy/inventory/${drugId}`, method: 'PUT', data })
  },
  /** 药品报损 */
  loss(drugId: number, data: { batchId?: number; quantity: number; reason: string; operatorName?: string }) {
    return http<void>({ url: `/pharmacy/inventory/${drugId}/loss`, method: 'POST', data })
  },
  /** 冻结批次 */
  freezeBatch(batchId: number) {
    return http<void>({ url: `/pharmacy/inventory/batch/${batchId}/freeze`, method: 'PUT' })
  },
  /** 解冻批次 */
  unfreezeBatch(batchId: number) {
    return http<void>({ url: `/pharmacy/inventory/batch/${batchId}/unfreeze`, method: 'PUT' })
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
  /**
   * 处方级用药指导单（PDF 延迟生成）
   * - 查最新一条状态：用于按钮可用性探测
   * - 重试：手动重新生成 JSON 数据
   * - 下载 PDF：用 buildMedicationGuidePdfUrl 拿 URL，配合 window.open 触发浏览器下载
   */
  medicationGuideStatus(registerId: number) {
    return http<MedicationGuideRecord | null>({ url: `/pharmacy/medication-guide/${registerId}`, method: 'GET' })
  },
  retryMedicationGuide(registerId: number) {
    return http<MedicationGuideRecord>({ url: `/pharmacy/medication-guide/${registerId}/retry`, method: 'POST' })
  },
  /**
   * 发药前主动生成用药指导单（幂等）。已有 success 记录则直接返回，否则同步生成。
   */
  generateMedicationGuide(registerId: number) {
    return http<MedicationGuideRecord>({ url: `/pharmacy/medication-guide/${registerId}/generate`, method: 'POST' })
  },
  /**
   * 下载用药指导单 PDF：调后端实时渲染接口，拿到 blob 后在浏览器触发下载。
   * 不走 window.open 是因为 PDF 接口需要 Authorization header（window.open 无法附加）。
   */
  async downloadMedicationGuidePdf(registerId: number) {
    const res = await blobClient.get(`/pharmacy/medication-guide/${registerId}/pdf`, {
      responseType: 'blob',
    })
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `medication-guide-${registerId}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // 释放 blob URL
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  /** P2-4.6 查询发药单 */
  dispensingByRegister(registerId: number) {
    return http<Dispensing[]>({ url: `/pharmacy/dispensing/${registerId}`, method: 'GET' })
  },
  /**
   * 患者端「我的处方」：返回该患者所有处方（按挂号聚合），
   * 后端会对每个挂号幂等生成药品费 expense_record 行。
   */
  patientPrescriptions(patientId: number) {
    return http<PrescriptionSummary[]>({ url: `/pharmacy/patient/${patientId}/prescriptions`, method: 'GET' })
  },
}
