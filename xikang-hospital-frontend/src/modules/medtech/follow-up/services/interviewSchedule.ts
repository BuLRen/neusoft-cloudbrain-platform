import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { InterviewSchedulePayload, PatientNotifyPayload } from '@/shared/types/medtechFollowUp'

export async function addToWeeklySchedule(payload: InterviewSchedulePayload) {
  const result = await medtechFollowUpApi.createInterviewSchedule(payload)
  // 患者端随访模块就绪后启用：
  // await notifyPatientInterviewScheduled({
  //   registerId: payload.registerId,
  //   weekStartDate: result.weekStartDate,
  //   message: payload.triggerReason,
  // })
  return result
}

/** 患者端随访模块就绪后启用 */
export async function notifyPatientInterviewScheduled(_payload: PatientNotifyPayload) {
  // TODO: POST /patient/followup/notifications
  return Promise.resolve({ skipped: true as const })
}

export async function listWeeklyInterviewSchedules(weekStart?: string) {
  return medtechFollowUpApi.listInterviewSchedules({ weekStart, status: 'scheduled' })
}

export async function getWeeklyScheduleStatus(registerId: number) {
  return medtechFollowUpApi.getInterviewScheduleStatus(registerId)
}
