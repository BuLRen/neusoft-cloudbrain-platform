import { http } from '../request'
import type { CtAnalyzeResult, CtSegmentResult } from './ctViewer'
import type { MedicalTechnologyCatalogItem } from '@/shared/types/medtech'
import type { SimulatedCheckStructuredOutput } from '@/shared/types/simulatedCheckResult'

export type MedtechTechType = 'check' | 'inspection' | 'disposal'

export interface MedtechApplication {
  id: number
  techType: MedtechTechType
  registerId?: number
  patientName?: string
  caseNumber?: string
  techName?: string
  techCode?: string
  aiCategoryCode?: string
  position?: string
  info?: string
  statusText?: string
  creationTime?: string
  paid?: boolean
  payStatus?: number
  payStatusText?: string
  feeAmount?: number
  hasImaging?: boolean
  imagingVolumeId?: string
  imagingUploadedAt?: string
  imagingSourceName?: string
  hasImagingAnalysis?: boolean
  imagingAnalyzedAt?: string
  imagingAnalysisResult?: CtAnalyzeResult
  hasImagingSegmentation?: boolean
  imagingSegmentedAt?: string
  imagingSegmentationResult?: CtSegmentResult
  imagingSegmentationMaskVolumeId?: string
}

export interface CheckApplication extends MedtechApplication {
  techType: 'check'
  checkState?: string
  checkTime?: string
}

export interface InspectionApplication extends MedtechApplication {
  techType: 'inspection'
  inspectionState?: string
  inspectionTime?: string
}

export interface DisposalApplication extends MedtechApplication {
  techType: 'disposal'
  disposalState?: string
  disposalTime?: string
}

export interface CheckReport extends CheckApplication {
  medicalTechnologyId?: number
  checkResult?: string
  checkRemark?: string
}

export interface InspectionReport extends InspectionApplication {
  medicalTechnologyId?: number
  inspectionResult?: string
  inspectionRemark?: string
  result?: string
}

export interface DisposalReport extends DisposalApplication {
  medicalTechnologyId?: number
  disposalResult?: string
  disposalRemark?: string
  result?: string
}

export interface CheckSimulationResult {
  source?: string
  workflowRunId?: string
  structuredOutput?: SimulatedCheckStructuredOutput
  simulatedValues?: Record<string, unknown>
  resultText?: string
  elapsedTime?: number
  difyError?: string
  riskLevel?: string
  limitations?: string
}

export interface ArchiveRequest {
  reason: string
  remark?: string
}

export interface CheckImagingInfo {
  checkRequestId?: number
  volumeId?: string
  uploadedAt?: string
  sourceName?: string
  analyzedAt?: string
  segmentedAt?: string
  hasImaging?: boolean
  hasImagingAnalysis?: boolean
  hasImagingSegmentation?: boolean
  maskVolumeId?: string
  analysisResult?: CtAnalyzeResult
  segmentationResult?: CtSegmentResult
}

export interface MedtechProfile {
  userId: number
  role: string
  employeeId?: number
  departmentId?: number
  departmentName?: string
  adminAllAccess?: boolean
}

export interface MedtechHistoricalSummary {
  totalCompletedChecks: number
  totalCompletedInspections: number
  totalCompletedDisposals: number
  totalCompletedAll: number
  todayCompletedChecks: number
  todayCompletedInspections: number
  todayCompletedDisposals: number
  todayCompletedAll: number
}

/** Dify 模拟检查 blocking 调用超时（与后端 read-timeout-ms 一致） */
const CHECK_SIMULATE_TIMEOUT_MS = 5 * 60 * 1000
const CT_IMAGING_ANALYZE_TIMEOUT_MS = 3 * 60 * 1000

export const medtechApi = {
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

  profile() {
    return http<MedtechProfile>({ url: '/medtech/profile', method: 'GET' })
  },

  statsSummary() {
    return http<MedtechHistoricalSummary>({ url: '/medtech/stats/summary', method: 'GET' })
  },

  checkApplications(params?: { registrationId?: number; checkState?: string; status?: number }) {
    return http<CheckApplication[]>({ url: '/medtech/check/applications', method: 'GET', params }).then((rows) =>
      rows.map((row) => ({ ...row, techType: 'check' as const })),
    )
  },
  inspectionApplications(params?: { registrationId?: number; inspectionState?: string; status?: number }) {
    return http<InspectionApplication[]>({ url: '/medtech/inspection/applications', method: 'GET', params }).then(
      (rows) => rows.map((row) => ({ ...row, techType: 'inspection' as const })),
    )
  },
  disposalApplications(params?: { registrationId?: number; disposalState?: string; status?: number }) {
    return http<DisposalApplication[]>({ url: '/medtech/disposal/applications', method: 'GET', params }).then((rows) =>
      rows.map((row) => ({ ...row, techType: 'disposal' as const })),
    )
  },
  startCheck(id: number, operatorInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/check/start/${id}`, method: 'PUT', data: operatorInfo })
  },
  submitCheckResult(
    id: number,
    data: {
      values?: Record<string, unknown>
      result?: string
      checkResult?: string
      checkRemark?: string
      aiAnalysis?: string
      structuredOutput?: SimulatedCheckStructuredOutput
    },
  ) {
    return http<Record<string, unknown>>({ url: `/medtech/check/result/${id}`, method: 'PUT', data })
  },
  checkReport(id: number) {
    return http<CheckReport>({ url: `/medtech/check/report/${id}`, method: 'GET' })
  },
  simulateCheck(id: number, body?: { normal_status?: boolean }) {
    return http<CheckSimulationResult>({
      url: `/medtech/check/simulate/${id}`,
      method: 'POST',
      data: body,
      timeout: CHECK_SIMULATE_TIMEOUT_MS,
    })
  },
  simulateInspection(id: number, body?: { normal_status?: boolean }) {
    return http<CheckSimulationResult>({
      url: `/medtech/inspection/simulate/${id}`,
      method: 'POST',
      data: body,
      timeout: CHECK_SIMULATE_TIMEOUT_MS,
    })
  },
  ctInferCheck(id: number) {
    return http<CheckSimulationResult>({
      url: `/medtech/check/ct-infer/${id}`,
      method: 'POST',
      timeout: CHECK_SIMULATE_TIMEOUT_MS,
    })
  },
  getCheckImaging(id: number) {
    return http<CheckImagingInfo>({ url: `/medtech/check/${id}/imaging`, method: 'GET' })
  },
  bindCheckImaging(id: number, data: { volumeId: string; sourceName?: string }) {
    return http<CheckImagingInfo>({ url: `/medtech/check/${id}/imaging`, method: 'PUT', data })
  },
  clearCheckImaging(id: number) {
    return http<void>({ url: `/medtech/check/${id}/imaging`, method: 'DELETE' })
  },
  analyzeCheckImaging(id: number) {
    return http<CheckImagingInfo>({
      url: `/medtech/check/${id}/imaging/analyze`,
      method: 'POST',
      timeout: CT_IMAGING_ANALYZE_TIMEOUT_MS,
    })
  },
  segmentCheckImaging(id: number) {
    return http<CheckImagingInfo>({
      url: `/medtech/check/${id}/imaging/segment`,
      method: 'POST',
      timeout: CT_IMAGING_ANALYZE_TIMEOUT_MS,
    })
  },
  aiSegmentCheckImaging(id: number) {
    return http<CheckImagingInfo>({
      url: `/medtech/check/${id}/imaging/segment/ai`,
      method: 'POST',
      timeout: 10 * 60 * 1000,
    })
  },
  startInspection(id: number, operatorInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/inspection/start/${id}`, method: 'PUT', data: operatorInfo })
  },
  recordInspectionSpecimen(id: number, specimenInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/inspection/specimen/${id}`, method: 'PUT', data: specimenInfo })
  },
  submitInspectionResult(
    id: number,
    data: {
      values?: Record<string, unknown>
      structuredOutput?: SimulatedCheckStructuredOutput
      inspectionResult?: string
      result?: string | Record<string, unknown>
      inspectionRemark?: string
      aiAnalysis?: string
    },
  ) {
    return http<Record<string, unknown>>({ url: `/medtech/inspection/result/${id}`, method: 'PUT', data })
  },
  inspectionReport(id: number) {
    return http<InspectionReport>({ url: `/medtech/inspection/report/${id}`, method: 'GET' })
  },
  startDisposal(id: number, operatorInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/disposal/start/${id}`, method: 'PUT', data: operatorInfo })
  },
  submitDisposalResult(id: number, data: { disposalResult?: string; result?: string; disposalRemark?: string }) {
    return http<Record<string, unknown>>({ url: `/medtech/disposal/result/${id}`, method: 'PUT', data })
  },
  disposalReport(id: number) {
    return http<DisposalReport>({ url: `/medtech/disposal/report/${id}`, method: 'GET' })
  },
  medicalTechnologies(type?: string) {
    return http<MedicalTechnologyCatalogItem[]>({
      url: '/medtech/medical-technologies',
      method: 'GET',
      params: { type },
    })
  },
  medicalTechnology(id: number) {
    return http<MedicalTechnologyCatalogItem>({
      url: `/medtech/medical-technologies/${id}`,
      method: 'GET',
    })
  },
  archiveCheck(id: number, data: ArchiveRequest) {
    return http<void>({ url: `/medtech/check/archive/${id}`, method: 'PUT', data })
  },
  archiveInspection(id: number, data: ArchiveRequest) {
    return http<void>({ url: `/medtech/inspection/archive/${id}`, method: 'PUT', data })
  },
  archiveDisposal(id: number, data: ArchiveRequest) {
    return http<void>({ url: `/medtech/disposal/archive/${id}`, method: 'PUT', data })
  },
}

export async function fetchMedtechReport(techType: MedtechTechType, id: number) {
  if (techType === 'check') return medtechApi.checkReport(id)
  if (techType === 'inspection') return medtechApi.inspectionReport(id)
  return medtechApi.disposalReport(id)
}
