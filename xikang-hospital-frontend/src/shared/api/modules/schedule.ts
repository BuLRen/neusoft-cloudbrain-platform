import { http } from '../request'
import type {
  SchedulePlan,
  DoctorSchedule,
  ScheduleAdjustRequest,
  LeaveRequest,
  AiGenerateTaskView,
} from '@/shared/types/schedule'

interface UpdateSchedulePayload {
  physicianId?: number
  workDate?: string
  timeSlot?: string
  totalQuota?: number
  status?: string
  aiSuggestion?: string
  operatorId?: number
  remark?: string
}

export const scheduleApi = {
  // ==================== 排班计划相关 ====================

  /**
   * 获取排班计划列表
   */
  plans(params: { departmentId?: number; month?: string }) {
    return http<SchedulePlan[]>({ url: '/schedule/plans', method: 'GET', params })
  },

  /**
   * 获取排班计划详情
   */
  plan(planId: number) {
    return http<SchedulePlan>({ url: `/schedule/plan/${planId}`, method: 'GET' })
  },

  /**
   * 获取计划下的排班明细
   */
  planSchedules(planId: number) {
    return http<DoctorSchedule[]>({ url: `/schedule/plan/${planId}/schedules`, method: 'GET' })
  },

  /**
   * 获取日历数据
   */
  calendar(departmentId: number, month: string) {
    return http<{ date: string; morning: number; afternoon: number }[]>({
      url: '/schedule/calendar',
      method: 'GET',
      params: { departmentId, month },
    })
  },

  /**
   * 创建排班计划
   */
  createPlan(data: { planName: string; departmentId: number; planMonth: string }) {
    return http<SchedulePlan>({ url: '/schedule/plan', method: 'POST', data })
  },

  /**
   * AI 生成排班计划（异步提交，立即返回任务视图）
   */
  generatePlanByAI(data: {
    departmentId: number
    month: string
    operatorId: number
    generateType?: string
  }) {
    return http<AiGenerateTaskView>({
      url: '/schedule/plan/ai-generate',
      method: 'POST',
      data,
      timeout: 30000,
    })
  },

  /**
   * 查询进行中或刚结束的 AI 排班任务
   */
  getActiveAiTask(params: {
    operatorId: number
    departmentId: number
    month: string
  }) {
    return http<AiGenerateTaskView | null>({
      url: '/schedule/plan/ai-generate/active',
      method: 'GET',
      params,
    })
  },

  /**
   * 取消 AI 排班任务
   */
  cancelActiveAiTask(params: {
    operatorId: number
    departmentId: number
    month: string
  }) {
    return http<void>({
      url: '/schedule/plan/ai-generate/active',
      method: 'DELETE',
      params,
    })
  },

  /**
   * 发布排班
   */
  publishPlan(planId: number, operatorId: number) {
    return http<void>({ url: `/schedule/plan/${planId}/publish`, method: 'POST', data: { operatorId } })
  },

  // ==================== 排班明细相关 ====================

  /**
   * 获取可用排班
   */
  available(departmentId: number, date: string, timeSlot?: string) {
    return http<DoctorSchedule[]>({
      url: '/schedule/available',
      method: 'GET',
      params: { departmentId, date, timeSlot },
    })
  },

  /**
   * 获取排班详情
   */
  detail(scheduleId: number) {
    return http<DoctorSchedule>({ url: `/schedule/detail/${scheduleId}`, method: 'GET' })
  },

  /**
   * 获取医生排班
   */
  byPhysician(physicianId: number, startDate: string, endDate: string) {
    return http<DoctorSchedule[]>({
      url: `/schedule/physician/${physicianId}`,
      method: 'GET',
      params: { startDate, endDate },
    })
  },

  /**
   * 创建排班
   */
  createSchedule(data: {
    planId: number
    physicianId: number
    departmentId: number
    workDate: string
    timeSlot: string
    totalQuota: number
    status: string
    aiSuggestion?: string
  }) {
    return http<DoctorSchedule>({ url: '/schedule/schedule', method: 'POST', data })
  },

  /**
   * 更新排班
   */
  updateSchedule(scheduleId: number, data: UpdateSchedulePayload) {
    return http<void>({ url: `/schedule/schedule/${scheduleId}`, method: 'PUT', data })
  },

  /**
   * 停诊
   */
  stopSchedule(scheduleId: number, operatorId: number, reason: string) {
    return http<void>({
      url: `/schedule/schedule/${scheduleId}/stop`,
      method: 'POST',
      data: { operatorId, reason },
    })
  },

  /**
   * 恢复出诊
   */
  resumeSchedule(scheduleId: number, operatorId: number) {
    return http<void>({ url: `/schedule/schedule/${scheduleId}/resume`, method: 'POST', data: { operatorId } })
  },

  // ==================== 请假相关 ====================

  /**
   * 获取请假列表
   */
  leaves(params?: { physicianId?: number; status?: string; startDate?: string; endDate?: string }) {
    return http<LeaveRequest[]>({ url: '/schedule/leave/list', method: 'GET', params })
  },

  /**
   * 创建请假
   */
  createLeave(data: {
    physicianId: number
    leaveDate: string
    timeSlot?: string
    leaveType: string
    reason?: string
    rawText?: string
  }) {
    return http<LeaveRequest>({ url: '/schedule/leave/create', method: 'POST', data })
  },

  /**
   * 审批请假
   */
  approveLeave(leaveId: number, approverId: number) {
    return http<LeaveRequest>({ url: `/schedule/leave/${leaveId}/approve`, method: 'POST', data: { approverId } })
  },

  // ==================== 替班相关 ====================

  /**
   * 查询替班医生
   */
  substitutes(departmentId: number, leaveDate: string, timeSlot: string, excludePhysicianId?: number) {
    return http<{ physicianId: number; physicianName: string }[]>({
      url: '/schedule/substitutes',
      method: 'GET',
      params: { departmentId, leaveDate, timeSlot, excludePhysicianId },
    })
  },

  // ==================== 调整相关 ====================

  /**
   * 获取待确认调整
   */
  pendingAdjusts() {
    return http<ScheduleAdjustRequest[]>({ url: '/schedule/adjust/pending', method: 'GET' })
  },

  /**
   * 获取调整详情
   */
  adjust(adjustId: number) {
    return http<ScheduleAdjustRequest>({ url: `/schedule/adjust/${adjustId}`, method: 'GET' })
  },

  /**
   * 确认调整
   */
  confirmAdjust(requestId: number, confirmedBy: number, remark?: string) {
    return http<void>({
      url: '/schedule/adjust/confirm',
      method: 'POST',
      data: { requestId, confirmedBy, remark },
    })
  },

  /**
   * 驳回调整
   */
  rejectAdjust(requestId: number, rejectedBy: number, reason: string) {
    return http<void>({
      url: '/schedule/adjust/reject',
      method: 'POST',
      data: { requestId, rejectedBy, reason },
    })
  },

  /**
   * 创建紧急调整
   */
  createUrgentAdjust(data: {
    scheduleId: number
    newPhysicianId: number
    reason: string
    operatorId: number
  }) {
    return http<ScheduleAdjustRequest>({ url: '/schedule/adjust/urgent', method: 'POST', data })
  },

  // ==================== 号源相关 ====================

  /**
   * 扣减号源
   */
  deductQuota(scheduleId: number, count: number, registerId?: number) {
    return http<void>({
      url: '/schedule/quota/deduct',
      method: 'POST',
      data: { scheduleId, count, registerId },
    })
  },

  /**
   * 退还号源
   */
  returnQuota(scheduleId: number, count: number) {
    return http<void>({
      url: '/schedule/quota/return',
      method: 'POST',
      data: { scheduleId, count },
    })
  },
}