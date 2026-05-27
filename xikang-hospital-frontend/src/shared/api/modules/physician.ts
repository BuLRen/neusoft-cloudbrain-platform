
import { http } from '../request'
import type { PageResult } from '../result'

export interface AiConsultSummary {
  chiefComplaint?: string
  symptomDuration?: string
  historySummary?: string
  allergySummary?: string
  medicationSummary?: string
  aiSummary?: string
  suggestedExam?: string
}

export interface PhysicianPatient {
  registerId: number
  caseNumber: string
  realName: string
  gender?: string
  age?: number
  visitDate?: string
  visitState: number
  hasAiConsultation?: boolean
  aiConsultSummary?: AiConsultSummary | null
}

export interface Disease {
  id: number
  diseaseCode?: string
  diseaseName: string
  diseaseIcd?: string
  diseaseCategory?: string
}

export interface MedicalRecord {
  id?: number
  registerId?: number
  readme?: string
  present?: string
  presentTreat?: string
  history?: string
  allergy?: string
  physique?: string
  proposal?: string
  careful?: string
  diagnosis?: string
  cure?: string
  diseases?: Disease[]
}

export interface MedicalTechnology {
  id: number
  techCode: string
  techName: string
  techFormat?: string
  techPrice: number
  techType: 'check' | 'inspection' | 'disposal'
  priceType?: string
  deptName?: string
}

export interface Drug {
  id: number
  drugCode: string
  drugName: string
  drugFormat?: string
  drugUnit?: string
  manufacturer?: string
  drugDosage?: string
  drugType?: string
  drugPrice: number
  mnemonicCode?: string
}

export interface AiExamAnalysis {
  riskLevel?: string
  analysisReport?: string
  abnormalIndicators?: string
  correlationAnalysis?: string
}

export interface CheckResult {
  id: number
  techName: string
  checkPosition?: string
  checkResult?: string
  checkState?: string
  checkTime?: string
  aiAnalysis?: AiExamAnalysis | null
}

export interface InspectionResult {
  id: number
  techName: string
  inspectionPosition?: string
  inspectionResult?: string
  inspectionState?: string
  inspectionTime?: string
  aiAnalysis?: AiExamAnalysis | null
}

export interface PrescriptionItem {
  id: number
  drugId: number
  drugName: string
  drugFormat?: string
  drugPrice: number
  drugUsage?: string
  drugNumber?: string
  drugState?: string
}

export const physicianApi = {
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
  patients(params: { keyword?: string; page?: number; size?: number }) {
    return http<PageResult<PhysicianPatient>>({ url: '/physician/patients', method: 'GET', params })
  },
  patientStats() {
    return http<{ totalVisited: number; totalWaiting: number }>({ url: '/physician/patient-stats', method: 'GET' })
  },
  medicalRecord(registerId: number) {
    return http<MedicalRecord | null>({ url: '/physician/medical-record', method: 'GET', params: { registerId }, skipErrorMessage: true })
  },
  createMedicalRecord(data: Record<string, unknown>) {
    return http<{ id: number; registerId: number }>({ url: '/physician/medical-record', method: 'POST', data })
  },
  updateMedicalRecord(id: number, data: Record<string, unknown>) {
    return http<void>({ url: `/physician/medical-record/${id}`, method: 'PUT', data })
  },
  diseases(keyword?: string) {
    return http<Disease[]>({ url: '/physician/diseases', method: 'GET', params: { keyword } })
  },
  medicalTechnologies(techType: 'check' | 'inspection' | 'disposal', keyword?: string) {
    return http<MedicalTechnology[]>({ url: '/physician/medical-technologies', method: 'GET', params: { techType, keyword } })
  },
  createCheckRequest(data: Record<string, unknown>) {
    return http<{ requestIds: number[] }>({ url: '/physician/check-request', method: 'POST', data })
  },
  createInspectionRequest(data: Record<string, unknown>) {
    return http<{ requestIds: number[] }>({ url: '/physician/inspection-request', method: 'POST', data })
  },
  createDisposalRequest(data: Record<string, unknown>) {
    return http<{ requestIds: number[] }>({ url: '/physician/disposal-request', method: 'POST', data })
  },
  checkResults(registerId: number) {
    return http<CheckResult[]>({ url: '/physician/check-results', method: 'GET', params: { registerId } })
  },
  inspectionResults(registerId: number) {
    return http<InspectionResult[]>({ url: '/physician/inspection-results', method: 'GET', params: { registerId } })
  },
  submitDiagnosis(data: Record<string, unknown>) {
    return http<void>({ url: '/physician/diagnosis', method: 'POST', data })
  },
  drugs(keyword?: string) {
    return http<Drug[]>({ url: '/physician/drugs', method: 'GET', params: { keyword } })
  },
  createPrescription(data: Record<string, unknown>) {
    return http<{ prescriptionIds: number[]; totalAmount: number; aiReviewResult?: Record<string, unknown> }>({ url: '/physician/prescription', method: 'POST', data })
  },
  prescriptions(registerId: number) {
    return http<PrescriptionItem[]>({ url: `/physician/prescription/${registerId}`, method: 'GET' })
  },
  deletePrescription(id: number) {
    return http<void>({ url: `/physician/prescription/${id}`, method: 'DELETE' })
  },
  examSuggestions(registerId: number) {
    return http<Record<string, unknown>[]>({ url: '/physician/ai/exam-suggestions', method: 'GET', params: { registerId }, skipErrorMessage: true })
  },
  diagnosisSuggestions(registerId: number) {
    return http<Record<string, unknown>[]>({ url: '/physician/ai/diagnosis-suggestions', method: 'GET', params: { registerId }, skipErrorMessage: true })
  },
}
