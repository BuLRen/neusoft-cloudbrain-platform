import { request } from './request'

export interface PatientFollowUpPlanItem {
  id: number
  registerId?: number
  followUpType?: string
  planStatus?: string
  plannedDate?: string
  contentTemplate?: string
  doctorName?: string
}

export interface PatientMedicationItem {
  id: number
  registerId?: number
  drugName?: string
  drugUsage?: string
  drugNumber?: string
}

export interface FollowUpCommunicationSession {
  id: number
  registerId: number
  status?: string
  aiEscalationEnabled?: boolean
  creationTime?: string
}

export type CommunicationSenderType = 'doctor' | 'patient' | 'ai' | 'system'

export interface FollowUpCommunicationMessage {
  id: number
  sessionId: number
  senderType: CommunicationSenderType
  messageType?: string
  content: string
  creationTime?: string
}

export interface FollowUpCommunicationMessagesPage {
  items: FollowUpCommunicationMessage[]
  total: number
}

const patientBase = '/medtech/follow-up/patient'
const commBase = '/medtech/follow-up/communication'

export const followupApi = {
  listPlans: (patientId: number, registerIds?: number[]) =>
    request<PatientFollowUpPlanItem[]>({
      url: `${patientBase}/plans`,
      params: { patientId, registerIds },
    }),

  listMedications: (patientId: number, registerIds?: number[]) =>
    request<PatientMedicationItem[]>({
      url: `${patientBase}/medications`,
      params: { patientId, registerIds },
    }),

  completePlan: (planId: number) =>
    request<void>({
      url: `${patientBase}/plans/${planId}/complete`,
      method: 'PATCH',
    }),

  getCommunicationSession: (registerId: number, patientId: number) =>
    request<FollowUpCommunicationSession>({
      url: `${patientBase}/communication/sessions/${registerId}`,
      params: { patientId },
    }),

  listMessages: (registerId: number, patientId: number, limit = 100) =>
    request<FollowUpCommunicationMessagesPage>({
      url: `${patientBase}/communication/sessions/${registerId}/messages`,
      params: { patientId, limit },
    }),

  sendPatientMessage: (sessionId: number, content: string, autoAiReply = true) =>
    request<FollowUpCommunicationMessage>({
      url: `${commBase}/sessions/${sessionId}/patient-messages`,
      method: 'POST',
      data: { content, autoAiReply },
    }),

  markRead: (registerId: number, patientId: number) =>
    request<void>({
      url: `${patientBase}/communication/sessions/${registerId}/mark-read`,
      method: 'POST',
      params: { patientId },
    }),
}
