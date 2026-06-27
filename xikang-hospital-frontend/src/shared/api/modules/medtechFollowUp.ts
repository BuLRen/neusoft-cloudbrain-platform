import { http } from '../request'
import type {
  FollowUpCaseSummary,
  FollowUpCommunicationMessage,
  FollowUpCommunicationMessagesPage,
  FollowUpCommunicationPatientBrief,
  FollowUpCommunicationSession,
  FollowUpDashboardContext,
  FollowUpDashboardPatient,
  FollowUpDayScheduleItem,
  FollowUpHealthMetric,
  FollowUpEnrollPayload,
  FollowUpEnrollResult,
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
  PatientFollowUpFeedbackPayload,
  PatientFollowUpPlanItem,
  PatientFollowUpRecordItem,
  PatientMedicationItem,
} from '@/shared/types/medtechFollowUp'

const outcomeBase = '/medtech/follow-up/outcome'
const dashboardBase = '/medtech/follow-up/dashboard'
const communicationBase = '/medtech/follow-up/communication'
const patientPortalBase = '/medtech/follow-up/patient'

export const medtechFollowUpApi = {
  listPatients(visitState?: number) {
    return http<FollowUpPatientOption[]>({
      url: `${outcomeBase}/patients`,
      method: 'GET',
      params: visitState != null ? { visitState } : undefined,
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

  enrollPatient(payload: FollowUpEnrollPayload) {
    return http<FollowUpEnrollResult>({
      url: `${dashboardBase}/enroll`,
      method: 'POST',
      data: payload,
    })
  },

  listCommunicationSessions(params?: { departmentId?: number }) {
    return http<FollowUpCommunicationSession[]>({
      url: `${communicationBase}/sessions`,
      method: 'GET',
      params,
    })
  },

  openCommunicationSession(registerId: number) {
    return http<FollowUpCommunicationSession>({
      url: `${communicationBase}/sessions`,
      method: 'POST',
      data: { registerId },
    })
  },

  getCommunicationSession(sessionId: number) {
    return http<FollowUpCommunicationSession>({
      url: `${communicationBase}/sessions/${sessionId}`,
      method: 'GET',
    })
  },

  listCommunicationMessages(sessionId: number, params?: { limit?: number; offset?: number }) {
    return http<FollowUpCommunicationMessagesPage>({
      url: `${communicationBase}/sessions/${sessionId}/messages`,
      method: 'GET',
      params,
    })
  },

  sendDoctorMessage(sessionId: number, content: string) {
    return http<FollowUpCommunicationMessage>({
      url: `${communicationBase}/sessions/${sessionId}/messages`,
      method: 'POST',
      data: { content },
    })
  },

  sendPatientMessage(sessionId: number, content: string, autoAiReply = true) {
    return http<FollowUpCommunicationMessage>({
      url: `${communicationBase}/sessions/${sessionId}/patient-messages`,
      method: 'POST',
      data: { content, autoAiReply },
    })
  },

  triggerAiReply(sessionId: number) {
    return http<FollowUpCommunicationMessage>({
      url: `${communicationBase}/sessions/${sessionId}/ai-reply`,
      method: 'POST',
    })
  },

  setAiEscalation(sessionId: number, enabled: boolean) {
    return http<void>({
      url: `${communicationBase}/sessions/${sessionId}/ai-escalation`,
      method: 'PATCH',
      data: { enabled },
    })
  },

  generateCaseSummary(registerId: number) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/generate`,
      method: 'POST',
      data: { registerId },
    })
  },

  getLatestCaseSummary(registerId: number) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/${registerId}`,
      method: 'GET',
    })
  },

  getSharedCaseSummary(registerId: number) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/${registerId}/shared`,
      method: 'GET',
    })
  },

  updateCaseSummary(summaryId: number, doctorContent: string) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/${summaryId}`,
      method: 'PUT',
      data: { doctorContent },
    })
  },

  approveCaseSummary(summaryId: number, payload: { doctorContent?: string; sharedToPatient?: boolean }) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/${summaryId}/approve`,
      method: 'POST',
      data: payload,
    })
  },

  revokeCaseSummary(summaryId: number) {
    return http<FollowUpCaseSummary>({
      url: `${communicationBase}/case-summary/${summaryId}/revoke`,
      method: 'POST',
    })
  },

  getCommunicationPatientBrief(registerId: number) {
    return http<FollowUpCommunicationPatientBrief>({
      url: `${communicationBase}/patient-brief/${registerId}`,
      method: 'GET',
    })
  },

  getPatientCommunicationSession(registerId: number) {
    return http<FollowUpCommunicationSession>({
      url: `${communicationBase}/patient/session/${registerId}`,
      method: 'GET',
    })
  },

  listPatientPlans(params: { patientId?: number; registerIds?: number[] }) {
    return http<PatientFollowUpPlanItem[]>({
      url: `${patientPortalBase}/plans`,
      method: 'GET',
      params,
    })
  },

  listPatientRecords(params: { patientId?: number; registerIds?: number[] }) {
    return http<PatientFollowUpRecordItem[]>({
      url: `${patientPortalBase}/records`,
      method: 'GET',
      params,
    })
  },

  listPatientMedications(params: { patientId?: number; registerIds?: number[] }) {
    return http<PatientMedicationItem[]>({
      url: `${patientPortalBase}/medications`,
      method: 'GET',
      params,
    })
  },

  completePatientPlan(planId: number) {
    return http<void>({
      url: `${patientPortalBase}/plans/${planId}/complete`,
      method: 'PATCH',
    })
  },

  submitPatientFeedback(payload: PatientFollowUpFeedbackPayload) {
    return http<{ id?: number }>({
      url: `${patientPortalBase}/feedback`,
      method: 'POST',
      data: payload,
    })
  },
}
