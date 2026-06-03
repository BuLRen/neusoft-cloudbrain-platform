
import { http } from '../request'

export interface CheckApplication {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  caseNumber?: string
  techName?: string
  position?: string
  info?: string
  status?: number
  statusText?: string
  reportTime?: string
}

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

  checkApplications(params?: { registrationId?: number; status?: number }) {
    return http<CheckApplication[]>({ url: '/medtech/check/applications', method: 'GET', params })
  },
  startCheck(id: number, operatorInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/check/start/${id}`, method: 'PUT', data: operatorInfo })
  },
  submitCheckResult(id: number, data: { result: string; findings?: string; conclusion?: string; impression?: string; aiAnalysis?: string }) {
    return http<Record<string, unknown>>({ url: `/medtech/check/result/${id}`, method: 'PUT', data })
  },
  checkReport(id: number) {
    return http<Record<string, unknown>>({ url: `/medtech/check/report/${id}`, method: 'GET' })
  },
}
