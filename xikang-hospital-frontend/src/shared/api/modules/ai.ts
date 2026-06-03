import { http } from '../request'
import type {
  ExamAnalyzePayload,
  ExamAnalyzeResult,
  FollowUpCreatePayload,
  FollowUpCreateResult,
  FollowUpFeedbackPayload,
  FollowUpPlan,
  FollowUpQuestion,
  FollowUpRecord,
  MedicationGuidePayload,
  MedicationGuideResult,
  PrevisitChatPayload,
  PrevisitChatResult,
  PrevisitPayload,
  PrevisitRecord,
  PrevisitResult,
  PrevisitSummaryPayload,
  PrevisitSummaryResult,
  TriageAnalyzePayload,
  TriageAnalysisDetail,
  TriageAnalysisResult,
  TriageChatPayload,
  TriageChatResult,
  TriageHistoryRecord,
} from '@/shared/types/ai'

function parseJsonField<T>(value: unknown): T | null {
  if (value == null || value === '') {
    return null
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }
  return value as T
}

function normalizeTriageHistory(record: TriageHistoryRecord): TriageHistoryRecord {
  return {
    ...record,
    aiAnalysis: parseJsonField<TriageAnalysisDetail>(record.aiAnalysis) || record.aiAnalysis || null,
  }
}

function normalizeFollowUpStatus(status: FollowUpPlan['status']): FollowUpPlan['status'] {
  if (typeof status === 'string' && /^\d+$/.test(status)) {
    return Number(status) as FollowUpPlan['status']
  }
  return status
}

function normalizeFollowUpPlan(plan: FollowUpPlan): FollowUpPlan {
  return {
    ...plan,
    followUpItems: parseJsonField<FollowUpQuestion[]>(plan.followUpItems) || [],
    status: normalizeFollowUpStatus(plan.status),
  }
}

export const aiApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'PUT', data })
  },
  delete<T>(url: string) {
    return http<T>({ url, method: 'DELETE' })
  },
  triageAnalyze(data: TriageAnalyzePayload) {
    return http<TriageAnalysisResult>({ url: '/ai/triage/analyze', method: 'POST', data, skipErrorMessage: true })
  },
  triageChat(data: TriageChatPayload) {
    return http<TriageChatResult>({ url: '/ai/triage/chat', method: 'POST', data })
  },
  async triageRecord(id: number) {
    const result = await http<TriageHistoryRecord>({ url: `/ai/triage/record/${id}`, method: 'GET' })
    return normalizeTriageHistory(result)
  },
  async triageRecordsByPatient(patientId: number) {
    const result = await http<TriageHistoryRecord[]>({ url: `/ai/triage/records/patient/${patientId}`, method: 'GET', skipErrorMessage: true })
    return result.map(normalizeTriageHistory)
  },
  previsit(data: PrevisitPayload) {
    return http<PrevisitResult>({ url: '/ai/consult/previsit', method: 'POST', data })
  },
  previsitSummary(data: PrevisitSummaryPayload) {
    return http<PrevisitSummaryResult>({ url: '/ai/consult/summary', method: 'POST', data })
  },
  previsitChat(data: PrevisitChatPayload) {
    return http<PrevisitChatResult>({ url: '/ai/consult/chat', method: 'POST', data })
  },
  previsitRecord(id: number) {
    return http<PrevisitRecord>({ url: `/ai/consult/record/${id}`, method: 'GET', skipErrorMessage: true })
  },
  previsitRecordByRegister(registerId: number) {
    return http<PrevisitRecord>({ url: `/ai/consult/record/register/${registerId}`, method: 'GET', skipErrorMessage: true })
  },
  examAnalyze(data: ExamAnalyzePayload) {
    return http<ExamAnalyzeResult>({ url: '/ai/diagnosis/exam-analyze', method: 'POST', data, skipErrorMessage: true })
  },
  followupCreate(data: FollowUpCreatePayload) {
    return http<FollowUpCreateResult>({ url: '/ai/pharmacy/followup', method: 'POST', data })
  },
  async followupDetail(id: number) {
    const result = await http<FollowUpPlan>({ url: `/ai/pharmacy/followup/${id}`, method: 'GET' })
    return normalizeFollowUpPlan(result)
  },
  async followupPlansByPatient(patientId: number) {
    const result = await http<FollowUpPlan[]>({ url: `/ai/pharmacy/followup/patient/${patientId}`, method: 'GET' })
    return result.map(normalizeFollowUpPlan)
  },
  submitFollowupFeedback(planId: number, data: FollowUpFeedbackPayload) {
    return http<void>({ url: `/ai/pharmacy/followup/${planId}/feedback`, method: 'POST', data })
  },
  followupRecords(planId: number) {
    return http<FollowUpRecord[]>({ url: `/ai/pharmacy/followup/${planId}/records`, method: 'GET' })
  },
  medicationGuide(data: MedicationGuidePayload) {
    return http<MedicationGuideResult>({ url: '/ai/pharmacy/guide', method: 'POST', data, skipErrorMessage: true })
  },
}
