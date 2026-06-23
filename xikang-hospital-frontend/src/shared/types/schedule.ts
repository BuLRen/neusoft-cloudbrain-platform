/**
 * 智能排班系统类型定义
 */

/** 日历单元格数据 */
export interface CalendarDay {
  date: string
  morning: number
  afternoon: number
}

/** 排班计划 */
export interface SchedulePlan {
  id: number
  planName: string
  departmentId: number
  departmentName?: string
  planMonth: string
  status: '草稿' | '待审核' | '已发布'
  aiGenerated: boolean
  aiVersion?: number
  totalSchedules: number
  totalQuota: number
  createdBy?: number
  createdTime?: string
  publishedTime?: string
  publishedBy?: number
}

/** 医生出诊明细 */
export interface DoctorSchedule {
  id: number
  planId: number
  physicianId: number
  physicianName?: string
  physicianTitle?: string
  departmentId: number
  departmentName?: string
  workDate: string
  timeSlot: '上午' | '下午' | '晚上'
  registLevelId: number
  registLevelName?: string
  totalQuota: number
  usedQuota: number
  availableQuota: number
  price: number
  status: '正常' | '停诊' | '满诊' | '替班'
  aiSuggestion?: string
  modified: boolean
  modifyRemark?: string
  createdTime?: string
  updateTime?: string
}

export interface AiGeneratedSchedule {
  physicianId: number
  physicianName?: string
  workDate: string
  timeSlot: string
  totalQuota?: number
  availableQuota?: number
  usedQuota?: number
  price?: number
  registLevelId?: number
  aiSuggestion?: string
  status?: string
}

export interface AiGeneratePlanStatistics {
  totalSchedules?: number
  totalQuota?: number
  expertRatio?: number
  avgWeeklyWorkload?: number
}

export interface AiGeneratePlanResult {
  planId: number
  scheduleCount: number
  aiVersion?: number
  generateType?: string
  plan?: SchedulePlan
  validatedSchedules: AiGeneratedSchedule[]
  statistics?: AiGeneratePlanStatistics
  errors: unknown[]
  warnings: unknown[]
  message?: string
}

/**
 * AI 排班异步任务视图。后端内存中的任务状态，前端通过轮询拉取用于横幅展示。
 */
export type AiTaskStatus = 'running' | 'success' | 'failed' | 'cancelled'

export type AiTaskStage =
  | 'validating'
  | 'loading_doctors'
  | 'calling_coze'
  | 'parsing_ai'
  | 'ready_to_save'
  | 'saving_plan'
  | 'done'
  | 'error'
  | 'cancelled'

export interface AiGenerateTaskView {
  status: AiTaskStatus
  stage: AiTaskStage
  percent: number
  message: string
  planId?: number
  error?: string
  createdAt?: number
  updatedAt?: number
}

/** 排班调整申请 */
export interface ScheduleAdjustRequest {
  id: number
  scheduleId: number
  adjustType: 'leave_ai' | 'admin_urgent' | 'system'
  oldPhysicianId?: number
  newPhysicianId?: number
  originalPhysicianName?: string
  substitutePhysicianName?: string
  oldStatus?: string
  newStatus?: string
  oldQuota?: number
  newQuota?: number
  reason?: string
  aiSuggestion?: string
  affectPatients: number
  triggeredBy: number
  triggeredByName?: string
  status: '待确认' | '已确认' | '已驳回'
  confirmedBy?: number
  confirmedByName?: string
  confirmTime?: string
  confirmRemark?: string
  createTime: string
  workDate?: string
  timeSlot?: string
}

/** 医生请假申请 */
export interface LeaveRequest {
  id: number
  physicianId: number
  physicianName?: string
  leaveDate: string
  timeSlot?: '上午' | '下午' | '全天'
  leaveType: '事假' | '病假' | '公假' | '其他'
  reason?: string
  aiParsedDate?: string
  aiParsedSlot?: string
  aiConfidence?: number
  status: '待审批' | '已批准' | '已拒绝' | '已处理'
  approverId?: number
  approverName?: string
  approvalTime?: string
  autoProcessed: boolean
  createTime: string
}

/** 排班调整日志 */
export interface ScheduleAdjustLog {
  id: number
  scheduleId: number
  fieldName: string
  oldValue?: string
  newValue?: string
  adjustType: string
  adjustBy: number
  adjustByName?: string
  adjustTime: string
  remark?: string
}