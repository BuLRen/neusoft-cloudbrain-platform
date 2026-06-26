import { http } from '../request'
import type {
  FollowUpDashboardContext,
  FollowUpDashboardPatient,
  FollowUpDayScheduleItem,
  FollowUpHealthMetric,
  FollowUpObservationConfirmPayload,
  FollowUpObservationStatus,
  FollowUpOutcomeRecord,
  FollowUpPatientDetail,
  FollowUpPatientOption,
  FollowUpPatientProfile,
  FollowUpSchedulePayload,
  InterviewScheduleItem,
  InterviewSchedulePayload,
  InterviewScheduleStatus,
} from '@/shared/types/medtechFollowUp'

const outcomeBase = '/medtech/follow-up/outcome'
const dashboardBase = '/medtech/follow-up/dashboard'

export const medtechFollowUpApi = {
  listPatients(visitState = 3) {
    return http<FollowUpPatientOption[]>({
      url: `${outcomeBase}/patients`,
      method: 'GET',
      params: { visitState },
    })
  },

  getProfile(registerId: number) {
    return http<FollowUpPatientProfile>({
      url: `${outcomeBase}/profile/${registerId}`,
      method: 'GET',
    })
  },

  getPatientDetail(registerId: number) {
    return http<FollowUpPatientDetail>({
      url: `${outcomeBase}/patient-detail/${registerId}`,
      method: 'GET',
    })
  },

  getMetrics(registerId: number, params?: { from?: string; to?: string; metricKeys?: string[] }) {
    return http<FollowUpHealthMetric[]>({
      url: `${outcomeBase}/metrics/${registerId}`,
      method: 'GET',
      params,
    })
  },

  getRecords(registerId: number) {
    return http<FollowUpOutcomeRecord[]>({
      url: `${outcomeBase}/records/${registerId}`,
      method: 'GET',
    })
  },

  listInterviewSchedules(params?: { weekStart?: string; status?: string }) {
    return http<InterviewScheduleItem[]>({
      url: `${outcomeBase}/interview-schedule`,
      method: 'GET',
      params,
    })
  },

  getInterviewScheduleStatus(registerId: number) {
    return http<InterviewScheduleStatus>({
      url: `${outcomeBase}/interview-schedule/status/${registerId}`,
      method: 'GET',
    })
  },

  createInterviewSchedule(payload: InterviewSchedulePayload) {
    return http<InterviewScheduleItem>({
      url: `${outcomeBase}/interview-schedule`,
      method: 'POST',
      data: payload,
    })
  },

  getDashboardContext(params?: { date?: string; departmentId?: number }) {
    return http<FollowUpDashboardContext>({
      url: `${dashboardBase}/context`,
      method: 'GET',
      params,
    })
  },

  listDashboardPatients(params?: { date?: string; departmentId?: number }) {
    return http<FollowUpDashboardPatient[]>({
      url: `${dashboardBase}/patients`,
      method: 'GET',
      params,
    })
  },

  listDaySchedules(params: { from: string; to: string; departmentId?: number }) {
    return http<FollowUpDayScheduleItem[]>({
      url: `${dashboardBase}/schedule`,
      method: 'GET',
      params,
    })
  },

  createDaySchedule(payload: FollowUpSchedulePayload) {
    return http<FollowUpDayScheduleItem>({
      url: `${dashboardBase}/schedule`,
      method: 'POST',
      data: payload,
    })
  },

  updateDayScheduleStatus(id: number, status: 'planned' | 'completed' | 'cancelled') {
    return http<FollowUpDayScheduleItem>({
      url: `${dashboardBase}/schedule/${id}`,
      method: 'PATCH',
      data: { status },
    })
  },

  confirmObservation(payload: FollowUpObservationConfirmPayload) {
    return http<FollowUpObservationStatus>({
      url: `${dashboardBase}/observation/confirm`,
      method: 'POST',
      data: payload,
    })
  },

  getObservationStatus(registerId: number, date?: string) {
    return http<FollowUpObservationStatus>({
      url: `${dashboardBase}/observation/status/${registerId}`,
      method: 'GET',
      params: date ? { date } : undefined,
    })
  },
}
