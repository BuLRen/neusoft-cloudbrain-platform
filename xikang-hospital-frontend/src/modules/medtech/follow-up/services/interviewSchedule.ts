import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { beijingTodayYmd } from '@/shared/utils/beijingDate'
import type { InterviewSchedulePayload, PatientNotifyPayload } from '@/shared/types/medtechFollowUp'

export async function getTodayInterviewScheduled(registerId: number) {
  const today = beijingTodayYmd()
  const schedules = await medtechFollowUpApi.listDaySchedules({ from: today, to: today })
  return schedules.some(
    (item) =>
      item.registerId === registerId &&
      item.itemType === 'interview' &&
      item.status !== 'cancelled',
  )
}

export async function addToTodayInterview(payload: InterviewSchedulePayload) {
  const result = await medtechFollowUpApi.createDaySchedule({
    registerId: payload.registerId,
    scheduleDate: beijingTodayYmd(),
    itemType: 'interview',
    title: payload.triggerReason || undefined,
  })
  return result
}

/** @deprecated 工作台已改为日级日程，保留兼容旧调用 */
export async function addToWeeklySchedule(payload: InterviewSchedulePayload) {
  return addToTodayInterview(payload)
}

/** @deprecated 使用 getTodayInterviewScheduled */
export async function getWeeklyScheduleStatus(registerId: number) {
  const scheduled = await getTodayInterviewScheduled(registerId)
  return { scheduled, weekStartDate: beijingTodayYmd() }
}

/** 患者端随访模块就绪后启用 */
export async function notifyPatientInterviewScheduled(_payload: PatientNotifyPayload) {
  return Promise.resolve({ skipped: true as const })
}

export async function listWeeklyInterviewSchedules(weekStart?: string) {
  return medtechFollowUpApi.listInterviewSchedules({ weekStart, status: 'scheduled' })
}
