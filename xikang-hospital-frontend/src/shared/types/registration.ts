import type { TriageAnalysisDetail, TriageAnalysisResult } from './ai'

export interface DepartmentOption {
  id: number
  name: string
  code?: string
  type?: string
  parentId?: number
  orderNum?: number
  status?: number
  description?: string
  createTime?: string
  updateTime?: string
}

export interface RegistLevelOption {
  id: number
  name: string
  price?: number
  description?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface SettleCategoryOption {
  id: number
  name: string
  code?: string
  description?: string
  status?: number
  createTime?: string
}

export interface SchedulingOption {
  id?: number
  physicianId?: number
  physicianName?: string
  departmentId?: number
  departmentName?: string
  workDate?: string
  timeSlot?: string
  timeSlotName?: string
  totalQuota?: number
  usedQuota?: number
  availableQuota?: number
  status?: number
  statusName?: string
  remark?: string
  createTime?: string
  price?: number
  registLevelId?: number
  registLevelName?: string
  physicianTitle?: string
}

export interface SchedulingQuota {
  id: number
  physicianName?: string
  departmentName?: string
  workDate?: string
  timeSlot?: string
  totalQuota?: number
  usedQuota?: number
  availableQuota?: number
  usageRate?: number
}

export interface RegistrationForm {
  patientId?: number
  patientName: string
  patientPhone: string
  idCard: string
  departmentId?: number
  physicianId?: number
  physicianName?: string
  schedulingId?: number
  visitDate: string
  visitTime: string
  complaint: string
  registerType: number
  registLevelId?: number
  settleCategoryId?: number
  operatorId?: number
  operatorName?: string
  aiTriageResult?: TriageAnalysisResult | null
}

export interface RegistrationCreatePayload {
  patientId: number
  patientName?: string  // 可选，后端会根据 patientId 自动获取
  patientPhone?: string
  idCard?: string
  cardNumber?: string
  gender?: string
  birthdate?: string
  homeAddress?: string
  departmentId: number
  physicianId?: number
  physicianName?: string
  schedulingId?: number
  visitDate?: string
  visitTime?: string
  complaint?: string
  registerType?: number
  registLevelId?: number
  settleCategoryId?: number
  operatorId?: number
  operatorName?: string
  aiTriageResult?: TriageAnalysisResult | string | null
}

export interface RegistrationCreateResult {
  id: number
  patientName?: string
  departmentName?: string
  physicianName?: string
  visitDate?: string
  visitTime?: string
  registLevelId?: number
  registLevelName?: string
  settleCategoryId?: number
  settleCategoryName?: string
  amount?: number
  status?: number
  statusName?: string
  payStatus?: number
  payStatusName?: string
  accountBalance?: number
  paymentMessage?: string
}

export interface RegistrationRecord {
  id: number
  patientId?: number
  patientName?: string
  patientPhone?: string
  idCard?: string
  departmentId?: number
  departmentName?: string
  physicianId?: number
  physicianName?: string
  visitDate?: string
  visitTime?: string
  complaint?: string
  status?: number
  statusName?: string
  registerType?: number
  registerTypeName?: string
  registLevelId?: number
  registLevelName?: string
  settleCategoryId?: number
  amount?: number
  payStatus?: number
  payStatusName?: string
  aiTriageResult?: string | TriageAnalysisResult | null
  aiPreVisit?: string
  createTime?: string
}

export interface PendingChargeItem {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  categoryName?: string
  itemId?: number
  itemName?: string
  quantity?: number
  unitPrice?: number
  totalAmount?: number
  createTime?: string
}

export interface ChargePayload {
  registerId: number
  itemIds?: number[]
  operatorId?: number
  operatorName?: string
}

export interface ChargeResult {
  registerId?: number
  itemCount?: number
  totalAmount?: number
  payTime?: string
  operatorName?: string
}

export interface ExpenseRecord {
  id: number
  registerId?: number
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

export interface ExpenseRecordQuery {
  patientId?: number
  registerId?: number
  status?: number
}

export interface RefundPayload {
  expenseRecordId?: number
  operatorId?: number
  operatorName?: string
  reason?: string
}

export interface SchedulingCreatePayload {
  physicianId: number
  physicianName: string
  departmentId: number
  departmentName: string
  workDate: string
  timeSlot: string
  totalQuota?: number
  remark?: string
}

export interface SchedulingUpdatePayload {
  totalQuota?: number
  remark?: string
  status?: number
}

export type SchedulingBatchResult = SchedulingOption | { error: string; data: SchedulingCreatePayload }

export interface TriageDeskCreatePayload {
  patientId: number
  patientName: string
  patientPhone?: string
  symptoms: string
  aiTriageResult?: TriageAnalysisResult | string | null
  operatorId?: number
  operatorName?: string
}

export interface TriageDeskRecord {
  id: number
  patientId?: number
  patientName?: string
  patientPhone?: string
  symptoms?: string
  recommendedDepartment?: string
  recommendedDepartmentId?: number
  recommendedPhysicianName?: string
  recommendedPhysicianId?: number
  riskLevel?: string
  riskLevelName?: string
  status?: number
  statusName?: string
  confirmedDepartment?: string
  confirmedPhysicianName?: string
  createTime?: string
  confirmTime?: string
  aiTriageResult?: TriageAnalysisResult | null
  aiAnalysis?: TriageAnalysisDetail | null
}

export interface TriageDeskConfirmPayload {
  departmentId: number
  departmentName: string
  physicianId?: number
  physicianName?: string
  operatorId?: number
  operatorName?: string
  remark?: string
}
