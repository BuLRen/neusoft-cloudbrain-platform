import { http } from '../request'
import type {
  DiagnosisSuggestionItem,
  DrugSuggestionItem,
  FollowUpCaseSummary,
  FollowUpCommunicationMessage,
  FollowUpCommunicationMessagesPage,
  FollowUpCommunicationPatientBrief,
  FollowUpCommunicationSession,
  FollowUpDashboardContext,
  FollowUpDashboardPatient,
  FollowUpDayScheduleItem,
  FollowUpHealthMetric,
  FollowUpHistoryEvent,
  FollowUpHistoryEventType,
  FollowUpEnrollPayload,
  FollowUpEnrollResult,
  FollowUpObservationConfirmPayload,
  FollowUpObservationStatus,
  FollowUpOutcomeRecord,
  FollowUpPatientDetail,
  FollowUpPatientOption,
  FollowUpPatientProfile,
  FollowUpSchedulePayload,
  GlucoseAdvice,
  InterviewScheduleItem,
  InterviewSchedulePayload,
  InterviewScheduleStatus,
  LastVisitSnapshot,
  PatientFollowUpFeedbackPayload,
  PatientFollowUpPlanItem,
  PatientFollowUpRecordItem,
  PatientMedicationItem,
  PatientObservationPayload,
} from '@/shared/types/medtechFollowUp'
import type { GlucoseForecastResult } from '@/shared/types/glucoseForecast'

const outcomeBase = '/medtech/follow-up/outcome'
const dashboardBase = '/medtech/follow-up/dashboard'
const communicationBase = '/medtech/follow-up/communication'
const historyBase = '/medtech/follow-up/history'
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

  getMetrics(
    registerId: number,
    params?: { from?: string; to?: string; metricKeys?: string[]; sourceType?: string },
  ) {
    return http<FollowUpHealthMetric[]>({
      url: `${outcomeBase}/metrics/${registerId}`,
      method: 'GET',
      params,
    })
  },

  getLastVisit(registerId: number) {
    return http<LastVisitSnapshot>({
      url: `${outcomeBase}/last-visit/${registerId}`,
      method: 'GET',
    })
  },

  getGlucoseAdvice(registerId: number) {
    return http<GlucoseAdvice>({
      url: `${outcomeBase}/glucose-advice/${registerId}`,
      method: 'GET',
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
      data: { content, messageType: 'text' },
    })
  },

  sendDoctorCard(
    sessionId: number,
    messageType: 'drug_card' | 'diagnosis_card',
    cardPayload: Record<string, unknown>,
  ) {
    return http<FollowUpCommunicationMessage>({
      url: `${communicationBase}/sessions/${sessionId}/messages`,
      method: 'POST',
      data: { messageType, cardPayload },
    })
  },

  suggestDrugs(registerId: number, keyword?: string) {
    return http<DrugSuggestionItem[]>({
      url: `${communicationBase}/suggestions/drugs`,
      method: 'GET',
      params: { registerId, keyword },
    })
  },

  suggestDiagnoses(registerId: number) {
    return http<DiagnosisSuggestionItem[]>({
      url: `${communicationBase}/suggestions/diagnoses`,
      method: 'GET',
      params: { registerId },
    })
  },

  listHistoryEvents(params?: {
    registerId?: number
    from?: string
    to?: string
    eventType?: FollowUpHistoryEventType
    limit?: number
  }) {
    return http<FollowUpHistoryEvent[]>({
      url: historyBase,
      method: 'GET',
      params,
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

  getPatientCommunicationSession(registerId: number, params?: { patientId?: number }) {
    return http<FollowUpCommunicationSession>({
      url: `${patientPortalBase}/communication/sessions/${registerId}`,
      method: 'GET',
      params,
    })
  },

  listPatientCommunicationMessages(
    registerId: number,
    params?: { patientId?: number; limit?: number; offset?: number },
  ) {
    return http<FollowUpCommunicationMessagesPage>({
      url: `${patientPortalBase}/communication/sessions/${registerId}/messages`,
      method: 'GET',
      params,
    })
  },

  getPatientSharedCaseSummary(registerId: number, params?: { patientId?: number }) {
    return http<FollowUpCaseSummary>({
      url: `${patientPortalBase}/communication/case-summary/${registerId}`,
      method: 'GET',
      params,
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

  isGlucoseCohort(registerId: number) {
    return http<{ registerId: number; glucoseCohort: boolean }>({
      url: `${outcomeBase}/glucose-cohort/${registerId}`,
      method: 'GET',
    })
  },

  getGlucoseForecast(registerId: number, params?: { from?: string; to?: string }) {
    return http<GlucoseForecastResult>({
      url: `${outcomeBase}/forecast/${registerId}`,
      method: 'GET',
      params,
    })
  },

  refreshGlucoseForecast(registerId: number) {
    return http<GlucoseForecastResult>({
      url: `${outcomeBase}/forecast/${registerId}/refresh`,
      method: 'POST',
    })
  },

  getPatientGlucoseForecast(params: { patientId?: number; registerId?: number }) {
    return http<GlucoseForecastResult>({
      url: `${patientPortalBase}/glucose-forecast`,
      method: 'GET',
      params,
    })
  },

  refreshPatientGlucoseForecast(params: { patientId?: number; registerId?: number }) {
    return http<GlucoseForecastResult>({
      url: `${patientPortalBase}/glucose-forecast/refresh`,
      method: 'POST',
      params,
    })
  },

  getPatientLastVisit(params: { patientId?: number; registerId?: number }) {
    return http<LastVisitSnapshot>({
      url: `${patientPortalBase}/last-visit`,
      method: 'GET',
      params,
    })
  },

  listPatientObservations(params: {
    patientId?: number
    registerId?: number
    from?: string
    to?: string
    sourceType?: string
  }) {
    return http<FollowUpHealthMetric[]>({
      url: `${patientPortalBase}/observations`,
      method: 'GET',
      params,
    })
  },

  submitPatientObservation(payload: PatientObservationPayload, params?: { patientId?: number }) {
    return http<FollowUpHealthMetric>({
      url: `${patientPortalBase}/observations`,
      method: 'POST',
      params,
      data: payload,
    })
  },

  getPatientGlucoseAdvice(params: { patientId?: number; registerId?: number }) {
    return http<GlucoseAdvice>({
      url: `${patientPortalBase}/glucose-advice`,
      method: 'GET',
      params,
    })
  },
}
