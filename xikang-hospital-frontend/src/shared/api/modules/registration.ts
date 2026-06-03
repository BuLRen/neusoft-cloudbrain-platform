import { http } from '../request'
import type { TriageAnalysisDetail, TriageAnalysisResult } from '@/shared/types/ai'
import type {
  ChargePayload,
  ChargeResult,
  DepartmentOption,
  ExpenseRecord,
  ExpenseRecordQuery,
  PendingChargeItem,
  RefundPayload,
  RegistrationCreatePayload,
  RegistrationCreateResult,
  RegistrationRecord,
  RegistLevelOption,
  SchedulingBatchResult,
  SchedulingCreatePayload,
  SchedulingOption,
  SchedulingQuota,
  SchedulingUpdatePayload,
  SettleCategoryOption,
  TriageDeskConfirmPayload,
  TriageDeskCreatePayload,
  TriageDeskRecord,
} from '@/shared/types/registration'

function parseJsonField<T>(value: unknown): T | null {
  if (value == null || value === '') {
    return null
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }
  return value as T
}

function normalizeRegistration(record: RegistrationRecord): RegistrationRecord {
  return {
    ...record,
    aiTriageResult: parseJsonField<TriageAnalysisResult>(record.aiTriageResult) || record.aiTriageResult || null,
  }
}

function normalizeTriageRecord(record: TriageDeskRecord): TriageDeskRecord {
  return {
    ...record,
    aiTriageResult: parseJsonField<TriageAnalysisResult>(record.aiTriageResult) || null,
    aiAnalysis: parseJsonField<TriageAnalysisDetail>(record.aiAnalysis) || null,
  }
}

export const registrationApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'PUT', data, params })
  },
  delete<T>(url: string) {
    return http<T>({ url, method: 'DELETE' })
  },
  departments(type?: string) {
    return http<DepartmentOption[]>({
      url: type ? `/registration/departments/type/${type}` : '/registration/departments',
      method: 'GET',
    })
  },
  registLevels() {
    return http<RegistLevelOption[]>({ url: '/registration/regist-levels', method: 'GET' })
  },
  settleCategories() {
    return http<SettleCategoryOption[]>({ url: '/registration/settle-categories', method: 'GET' })
  },
  schedulingOptions(departmentId: number, date: string) {
    return http<SchedulingOption[]>({ url: `/registration/scheduling/${departmentId}/${date}`, method: 'GET' })
  },
  schedulingDetail(schedulingId: number) {
    return http<SchedulingOption>({ url: `/registration/scheduling/${schedulingId}/detail`, method: 'GET' })
  },
  createRegistration(data: RegistrationCreatePayload) {
    return http<RegistrationCreateResult>({ url: '/registration/register', method: 'POST', data })
  },
  async registration(id: number) {
    const result = await http<RegistrationRecord>({ url: `/registration/${id}`, method: 'GET' })
    return normalizeRegistration(result)
  },
  async registrationsByPatient(patientId: number) {
    const result = await http<RegistrationRecord[]>({ url: `/registration/patient/${patientId}`, method: 'GET' })
    return result.map(normalizeRegistration)
  },
  async registrationsByDate(date: string) {
    const result = await http<RegistrationRecord[]>({ url: `/registration/date/${date}`, method: 'GET' })
    return result.map(normalizeRegistration)
  },
  pendingChargesByPatient(patientId: number) {
    return http<PendingChargeItem[]>({ url: `/registration/pending-charges/${patientId}`, method: 'GET' })
  },
  pendingChargesByRegister(registerId: number) {
    return http<PendingChargeItem[]>({ url: `/registration/pending-charges/register/${registerId}`, method: 'GET' })
  },
  charge(data: ChargePayload) {
    return http<ChargeResult>({ url: '/registration/charge', method: 'POST', data })
  },
  expenseRecords(params: ExpenseRecordQuery) {
    return http<ExpenseRecord[]>({ url: '/registration/expense-records', method: 'GET', params })
  },
  refund(data: RefundPayload) {
    return http<void>({ url: '/registration/refund', method: 'POST', data })
  },
  refundByRegister(registerId: number, data?: Omit<RefundPayload, 'expenseRecordId'>) {
    return http<void>({ url: `/registration/refund/register/${registerId}`, method: 'POST', data })
  },
  cancelRegistration(id: number) {
    return http<void>({ url: `/registration/${id}/cancel`, method: 'PUT' })
  },
  createScheduling(data: SchedulingCreatePayload) {
    return http<SchedulingOption>({ url: '/registration/scheduling', method: 'POST', data })
  },
  updateScheduling(id: number, data: SchedulingUpdatePayload) {
    return http<void>({ url: `/registration/scheduling/${id}`, method: 'PUT', data })
  },
  deleteScheduling(id: number) {
    return http<void>({ url: `/registration/scheduling/${id}`, method: 'DELETE' })
  },
  scheduling(id: number) {
    return http<SchedulingOption>({ url: `/registration/scheduling/${id}`, method: 'GET' })
  },
  schedulingAvailable(departmentId: number, date: string) {
    return http<SchedulingOption[]>({ url: '/registration/scheduling/available', method: 'GET', params: { departmentId, date } })
  },
  schedulingByDepartment(departmentId: number) {
    return http<SchedulingOption[]>({ url: `/registration/scheduling/department/${departmentId}`, method: 'GET' })
  },
  schedulingByDate(date: string) {
    return http<SchedulingOption[]>({ url: `/registration/scheduling/date/${date}`, method: 'GET' })
  },
  schedulingByPhysician(physicianId: number, params?: { startDate?: string; endDate?: string }) {
    return http<SchedulingOption[]>({ url: `/registration/scheduling/physician/${physicianId}`, method: 'GET', params })
  },
  createSchedulingBatch(data: SchedulingCreatePayload[]) {
    return http<SchedulingBatchResult[]>({ url: '/registration/scheduling/batch', method: 'POST', data })
  },
  schedulingQuota(id: number) {
    return http<SchedulingQuota>({ url: `/registration/scheduling/${id}/quota`, method: 'GET' })
  },
  createTriage(data: TriageDeskCreatePayload) {
    return http<TriageDeskRecord>({ url: '/registration/triage-desk', method: 'POST', data })
  },
  async triagePending() {
    const result = await http<TriageDeskRecord[]>({ url: '/registration/triage-desk/pending', method: 'GET' })
    return result.map(normalizeTriageRecord)
  },
  async triageByPatient(patientId: number) {
    const result = await http<TriageDeskRecord[]>({ url: `/registration/triage-desk/patient/${patientId}`, method: 'GET' })
    return result.map(normalizeTriageRecord)
  },
  async triageDetail(id: number) {
    const result = await http<TriageDeskRecord>({ url: `/registration/triage-desk/${id}`, method: 'GET' })
    return normalizeTriageRecord(result)
  },
  confirmTriage(id: number, data: TriageDeskConfirmPayload) {
    return http<TriageDeskRecord>({ url: `/registration/triage-desk/${id}/confirm`, method: 'PUT', data })
  },
  cancelTriage(id: number, reason?: string) {
    return http<void>({ url: `/registration/triage-desk/${id}/cancel`, method: 'PUT', params: { reason } })
  },

  // ==================== 医生相关接口 ====================
  /**
   * 获取科室医生列表
   */
  getDoctorsByDepartment(departmentId: number) {
    return http<DoctorInfo[]>({ url: `/registration/doctors/department/${departmentId}`, method: 'GET' })
  },

  /**
   * 根据科室和挂号级别获取医生列表
   */
  getDoctorsByDepartmentAndLevel(departmentId: number, registLevelId: number) {
    return http<DoctorInfo[]>({ url: `/registration/doctors/department/${departmentId}/level/${registLevelId}`, method: 'GET' })
  },

  /**
   * 获取医生详情
   */
  getDoctor(id: number) {
    return http<DoctorInfo>({ url: `/registration/doctors/${id}`, method: 'GET' })
  },
}

// 医生信息类型
export interface DoctorInfo {
  id: number
  deptmentId: number
  registLevelId: number
  realname: string
  delmark: number
  deptName?: string
  registName?: string
}
