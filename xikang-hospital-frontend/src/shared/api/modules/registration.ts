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
import type { DrugOption } from '@/shared/types/pharmacy'

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

export interface AdminDrugPage {
  records: DrugOption[]
  total: number
  page: number
  size: number
  totalPages: number
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
  adminDrugs(params?: {
    keyword?: string
    dosageForm?: string
    category?: string
    page?: number
    size?: number
  }) {
    return http<AdminDrugPage>({
      url: '/registration/admin/drugs',
      method: 'GET',
      params: {
        keyword: params?.keyword || undefined,
        dosageForm: params?.dosageForm || undefined,
        category: params?.category || undefined,
        page: params?.page,
        size: params?.size,
      },
    })
  },
  adminDrugCategories() {
    return http<string[]>({ url: '/registration/admin/drugs/categories', method: 'GET' })
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
  payRegistration(id: number) {
    return http<ChargeResult>({ url: `/registration/${id}/pay`, method: 'POST' })
  },
  /** 患者自助支付药品费（扣余额，独立于挂号费） */
  payMedication(registerId: number) {
    return http<ChargeResult>({ url: `/registration/${registerId}/pay-medication`, method: 'POST' })
  },
  expenseRecords(params: ExpenseRecordQuery) {
    return http<ExpenseRecord[]>({ url: '/registration/expense-records', method: 'GET', params })
  },
  refund(data: RefundPayload) {
    return http<ChargeResult>({ url: '/registration/refund', method: 'POST', data })
  },
  refundByRegister(registerId: number, data?: Omit<RefundPayload, 'expenseRecordId'>) {
    return http<ChargeResult>({ url: `/registration/refund/register/${registerId}`, method: 'POST', data })
  },
  cancelRegistration(id: number) {
    return http<ChargeResult>({ url: `/registration/${id}/cancel`, method: 'PUT' })
  },
  checkIn(id: number) {
    return http<{ registerId: number; patientName?: string; checkInTime: string; alreadyCheckedIn: boolean; queueNumber: number; waitingAhead: number; message: string }>({
      url: `/registration/${id}/check-in`,
      method: 'POST',
    })
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

  // ==================== 管理员统计接口 ====================
  /**
   * 科室工作量（挂号量 / 接诊量 / 检查量 / 处方量）
   */
  departmentWorkload(params?: { startDate?: string; endDate?: string }) {
    return http<DepartmentWorkloadItem[]>({
      url: '/registration/stats/department-workload',
      method: 'GET',
      params,
    })
  },

  /**
   * 每日趋势（默认近 7 天）
   */
  dailyTrend(days = 7) {
    return http<DailyTrendPoint[]>({
      url: '/registration/stats/daily-trend',
      method: 'GET',
      params: { days },
    })
  },

  /**
   * KPI 汇总（在册科室 / 在册医生 / 药品目录 / AI 导诊咨询数）
   */
  kpi() {
    return http<KpiSummary>({
      url: '/registration/stats/kpi',
      method: 'GET',
    })
  },
}

// 管理员统计相关类型
export interface DepartmentWorkloadItem {
  departmentId: number
  departmentName: string
  registrations: number
  visits: number
  inspections: number
  prescriptions: number
}

export interface DailyTrendPoint {
  label: string
  date: string
  registrations: number
  charges: number
  triagePending: number
}

export interface KpiSummary {
  /** 在册科室数 */
  departments: number
  /** 在册临床医生数 */
  doctors: number
  /** 启用药品数 */
  drugs: number
  /** AI 导诊历史咨询数 */
  aiTriageConsultations: number
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

/**
 * 排班 API - 调用 schedule-service
 * 用于挂号时获取可用排班
 */
export const scheduleApi = {
  /**
   * 获取可用排班（供挂号使用）
   * 调用 GET /api/schedule/available?departmentId={}&date={}
   */
  async schedulingOptions(departmentId: number, date: string): Promise<SchedulingOption[]> {
    const response = await http<ScheduleResponse[]>({
      url: '/schedule/available',
      method: 'GET',
      params: { departmentId, date }
    })
    // 转换格式以匹配前端期望
    return response.map(item => ({
      id: item.scheduleId || item.id,
      physicianId: item.physicianId,
      physicianName: item.physicianName || '',
      departmentId: item.departmentId,
      departmentName: item.departmentName || '',
      workDate: item.workDate,
      timeSlot: item.timeSlot,
      timeSlotName: item.timeSlot === '上午' ? '上午' : item.timeSlot === '下午' ? '下午' : item.timeSlot === '晚上' ? '晚上' : item.timeSlot || '',
      totalQuota: item.totalQuota,
      usedQuota: item.usedQuota,
      availableQuota: item.availableQuota,
      status: item.status === '正常' ? 1 : item.status === '停诊' ? 0 : item.status === '满诊' ? 2 : 1,
      statusName: item.status || '正常',
      price: item.price,
      registLevelId: item.registLevelId,
      registLevelName: item.registLevelName,
      physicianTitle: item.physicianTitle,
    }))
  },

  /**
   * 获取排班详情
   * 调用 GET /api/schedule/detail/{scheduleId}
   */
  async schedulingDetail(scheduleId: number): Promise<SchedulingOption> {
    const response = await http<ScheduleResponse>({
      url: `/schedule/detail/${scheduleId}`,
      method: 'GET'
    })
    return {
      id: response.scheduleId || response.id,
      physicianId: response.physicianId,
      physicianName: response.physicianName || '',
      departmentId: response.departmentId,
      departmentName: response.departmentName || '',
      workDate: response.workDate,
      timeSlot: response.timeSlot,
      timeSlotName: response.timeSlot === '上午' ? '上午' : response.timeSlot === '下午' ? '下午' : response.timeSlot === '晚上' ? '晚上' : response.timeSlot || '',
      totalQuota: response.totalQuota,
      usedQuota: response.usedQuota,
      availableQuota: response.availableQuota,
      status: response.status === '正常' ? 1 : response.status === '停诊' ? 0 : response.status === '满诊' ? 2 : 1,
      statusName: response.status || '正常',
      price: response.price,
      registLevelId: response.registLevelId,
      registLevelName: response.registLevelName,
      physicianTitle: response.physicianTitle,
    }
  }
}

// schedule-service 返回的排班数据格式
interface ScheduleResponse {
  id?: number
  scheduleId?: number
  physicianId?: number
  physicianName?: string
  departmentId?: number
  departmentName?: string
  workDate?: string
  timeSlot?: string
  totalQuota?: number
  usedQuota?: number
  availableQuota?: number
  price?: number
  registLevelId?: number
  registLevelName?: string
  physicianTitle?: string
  status?: string
  remark?: string
}
