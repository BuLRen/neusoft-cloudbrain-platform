import { http } from '../request'
import type {
  FollowUpHealthMetric,
  FollowUpOutcomeRecord,
  FollowUpPatientDetail,
  FollowUpPatientOption,
  FollowUpPatientProfile,
  InterviewScheduleItem,
  InterviewSchedulePayload,
  InterviewScheduleStatus,
} from '@/shared/types/medtechFollowUp'

const base = '/medtech/follow-up/outcome'

export const medtechFollowUpApi = {
  listPatients(visitState = 3) {
    return http<FollowUpPatientOption[]>({
      url: `${base}/patients`,
      method: 'GET',
      params: { visitState },
    })
  },

  getProfile(registerId: number) {
    return http<FollowUpPatientProfile>({
      url: `${base}/profile/${registerId}`,
      method: 'GET',
    })
  },

  getPatientDetail(registerId: number) {
    return http<FollowUpPatientDetail>({
      url: `${base}/patient-detail/${registerId}`,
      method: 'GET',
    })
  },

  getMetrics(registerId: number, params?: { from?: string; to?: string; metricKeys?: string[] }) {
    return http<FollowUpHealthMetric[]>({
      url: `${base}/metrics/${registerId}`,
      method: 'GET',
      params,
    })
  },

  getRecords(registerId: number) {
    return http<FollowUpOutcomeRecord[]>({
      url: `${base}/records/${registerId}`,
      method: 'GET',
    })
  },

  listInterviewSchedules(params?: { weekStart?: string; status?: string }) {
    return http<InterviewScheduleItem[]>({
      url: `${base}/interview-schedule`,
      method: 'GET',
      params,
    })
  },

  getInterviewScheduleStatus(registerId: number) {
    return http<InterviewScheduleStatus>({
      url: `${base}/interview-schedule/status/${registerId}`,
      method: 'GET',
    })
  },

  createInterviewSchedule(payload: InterviewSchedulePayload) {
    return http<InterviewScheduleItem>({
      url: `${base}/interview-schedule`,
      method: 'POST',
      data: payload,
    })
  },
}
