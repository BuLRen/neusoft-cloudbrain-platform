
import { http } from '../request'
import type { SimulatedCheckStructuredOutput } from '@/shared/types/simulatedCheckResult'

export interface CheckApplication {
  id: number
  registerId?: number
  patientName?: string
  caseNumber?: string
  techName?: string
  techCode?: string
  aiCategoryCode?: string
  position?: string
  info?: string
  statusText?: string
  checkState?: string
  creationTime?: string
  checkTime?: string
}

export interface CheckReport extends CheckApplication {
  medicalTechnologyId?: number
  checkResult?: string
  checkRemark?: string
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

/** Dify 模拟检查 blocking 调用超时（与后端 read-timeout-ms 一致） */
const CHECK_SIMULATE_TIMEOUT_MS = 5 * 60 * 1000

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

  checkApplications(params?: { registrationId?: number; checkState?: string }) {
    return http<CheckApplication[]>({ url: '/medtech/check/applications', method: 'GET', params })
  },
  startCheck(id: number, operatorInfo?: Record<string, unknown>) {
    return http<void>({ url: `/medtech/check/start/${id}`, method: 'PUT', data: operatorInfo })
  },
  submitCheckResult(
    id: number,
    data: { values?: Record<string, unknown>; result?: string; checkResult?: string; checkRemark?: string; aiAnalysis?: string },
  ) {
    return http<Record<string, unknown>>({ url: `/medtech/check/result/${id}`, method: 'PUT', data })
  },
  checkReport(id: number) {
    return http<CheckReport>({ url: `/medtech/check/report/${id}`, method: 'GET' })
  },
  simulateCheck(id: number, body?: { isNormal?: boolean }) {
    return http<CheckSimulationResult>({
      url: `/medtech/check/simulate/${id}`,
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
}
