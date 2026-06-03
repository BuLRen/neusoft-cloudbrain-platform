import { http } from '../request'
import type {
  CheckApplication,
  CheckReport,
  CheckResultPayload,
  CheckResultSubmitResult,
  DisposalApplication,
  DisposalResultPayload,
  InspectionApplication,
  InspectionResultPayload,
  InspectionResultSubmitResult,
  InspectionSpecimenPayload,
  MedtechApplicationQuery,
  MedicalTechnologyCatalogItem,
} from '@/shared/types/medtech'

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
  checkApplications(params?: MedtechApplicationQuery) {
    return http<CheckApplication[]>({ url: '/medtech/check/applications', method: 'GET', params })
  },
  startCheck(id: number, data?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/check/start/${id}`, method: 'PUT', data })
  },
  submitCheckResult(id: number, data: CheckResultPayload) {
    return http<CheckResultSubmitResult>({ url: `/medtech/check/result/${id}`, method: 'PUT', data })
  },
  checkReport(id: number) {
    return http<CheckReport>({ url: `/medtech/check/report/${id}`, method: 'GET' })
  },
  inspectionApplications(params?: MedtechApplicationQuery) {
    return http<InspectionApplication[]>({ url: '/medtech/inspection/applications', method: 'GET', params })
  },
  startInspection(id: number) {
    return http<void>({ url: `/medtech/inspection/start/${id}`, method: 'PUT' })
  },
  submitInspectionSpecimen(id: number, data: InspectionSpecimenPayload) {
    return http<void>({ url: `/medtech/inspection/specimen/${id}`, method: 'PUT', data })
  },
  submitInspectionResult(id: number, data: InspectionResultPayload) {
    return http<InspectionResultSubmitResult>({ url: `/medtech/inspection/result/${id}`, method: 'PUT', data })
  },
  disposalApplications(params?: MedtechApplicationQuery) {
    return http<DisposalApplication[]>({ url: '/medtech/disposal/applications', method: 'GET', params })
  },
  startDisposal(id: number) {
    return http<void>({ url: `/medtech/disposal/start/${id}`, method: 'PUT' })
  },
  submitDisposalResult(id: number, data: DisposalResultPayload) {
    return http<void>({ url: `/medtech/disposal/result/${id}`, method: 'PUT', data })
  },
  medicalTechnologies(type?: string) {
    return http<MedicalTechnologyCatalogItem[]>({ url: '/medtech/medical-technologies', method: 'GET', params: { type } })
  },
  medicalTechnology(id: number) {
    return http<MedicalTechnologyCatalogItem>({ url: `/medtech/medical-technologies/${id}`, method: 'GET' })
  },
}
