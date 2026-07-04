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
  /** 导诊会话ID，供后端精确回填 register_id 到本次导诊记录（未做导诊时省略） */
  triageSessionId?: string
}

export interface RegistrationCreateResult {
  id: number
  caseNumber?: string
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
  caseNumber?: string
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
  settleCategoryName?: string
  amount?: number
  payStatus?: number
  payStatusName?: string
  payTime?: string
  refundTime?: string
  checkInTime?: string
  checkedIn?: boolean
  expenseRecords?: ExpenseRecord[]
  aiTriageResult?: string | TriageAnalysisResult | null
  aiPreVisit?: string
  createTime?: string
  /** 当前登录用户与该患者的关系（本人/配偶/父母等），来自 /managed 接口 */
  relation?: string
  /** 是否家属挂号（relation 非"本人"），用于列表显示家属标签 */
  isFamily?: boolean
  /** 排班表的通知标记（JOIN doctor_schedule.modify_remark），含"[医生变更]"等提示 */
  scheduleModifyRemark?: string
  /** 排班表状态（JOIN doctor_schedule.status），用于判断该排班是否被调整过 */
  scheduleStatus?: string
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
  success?: boolean
  registerId?: number
  itemCount?: number
  totalAmount?: number
  amount?: number
  payTime?: string
  refundTime?: string
  refundAmount?: number
  refunded?: boolean
  operatorName?: string
  payStatus?: number
  payStatusName?: string
  status?: number
  statusName?: string
  accountBalance?: number
  paymentMessage?: string
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
  startDate?: string
  endDate?: string
}

export type ExpenseRecordSortBy = 'payTime' | 'createTime' | 'refundTime'
export type ExpenseRecordSortDir = 'asc' | 'desc'

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

/**
 * 患者扫码报到接口返回结果
 * 报到机扫到合法二维码后调 POST /registration/{id}/check-in
 */
export interface CheckInResult {
  registerId: number
  patientName?: string
  /** 关联字段：就诊科室 ID */
  departmentId?: number
  /** 关联字段：就诊科室名 */
  departmentName?: string
  /** 关联字段：接诊医生 ID */
  doctorId?: number
  /** 关联字段：接诊医生名 */
  doctorName?: string
  /** 关联字段：就诊日期（YYYY-MM-DD） */
  visitDate?: string
  /** 关联字段：上午/下午 */
  noon?: string
  /** 关联字段：挂号级别（普通/专家/特需） */
  registLevelName?: string
  /** 报到时间戳 */
  checkInTime: string
  /** 是否已报到过（幂等返回时为 true） */
  alreadyCheckedIn: boolean
  /** 号序：当前患者在队列中的位置 */
  queueNumber: number
  /** 前面等待人数 */
  waitingAhead: number
  /** 提示文案 */
  message: string
}
