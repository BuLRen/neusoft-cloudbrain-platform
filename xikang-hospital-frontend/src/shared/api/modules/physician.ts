import { http } from '../request'

/** Dify 初步诊断 blocking 调用超时（与后端 read-timeout-ms 一致） */
const PRELIMINARY_AI_TIMEOUT_MS = 5 * 60 * 1000
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

export interface StructuredRecord {
  registerId: number
  patientInfo?: Record<string, unknown>
  chiefComplaint?: string
  symptomDuration?: string
  presentIllness?: string
  presentTreat?: string
  history?: string
  allergy?: string
  physique?: string
  preliminaryImpression?: string
  rawSource?: { inputMode?: string; longText?: string }
}

export interface Disease {
  id: number
  diseaseCode?: string
  diseaseName: string
  diseaseIcd?: string
  diseaseCategory?: string
}

export interface PreliminaryAiMeta {
  aiDiagnosis?: string
  diagnosisBasis?: string
  confidence?: number
  modelId?: string
  llmModel?: string
  suggestedDiseaseNames?: string[]
  suggestedDiseases?: Array<{
    diseaseId?: number
    diseaseName?: string
    recommendIcd?: string
    symptoms?: string
    confidenceLevel?: string
  }>
  preHandle?: boolean
}

export interface PreliminaryDiagnosisOutput {
  registerId?: number
  diagnosisText?: string
  diagnosisBasis?: string
  confidence?: number
  modelId?: string
  llmModel?: string
  suggestedDiseases?: Array<{
    diseaseId?: number
    diseaseName?: string
    recommendIcd?: string
    symptoms?: string
    confidenceLevel?: string
  }>
  preHandle?: boolean
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
  preliminaryDiagnosis?: string
  careful?: string
  diagnosis?: string
  cure?: string
  diseases?: Disease[]
  preliminaryAiMeta?: PreliminaryAiMeta
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

export interface AvailableExamination {
  techId: number
  techCode: string
  techName: string
  techType: string
  category: string
  techPrice?: number
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

export interface W2Output {
  preliminaryAssessment?: string
  recommendedExaminations?: Array<{
    techId: number
    techName: string
    techType: string
    reason: string
    priority: number
  }>
}

export interface W3Output {
  examSummaries?: Array<{
    techName: string
    keyFindings?: string[]
    interpretation?: string
    riskLevel?: string
  }>
  overallAnalysis?: string
  explicitNonDiagnosis?: boolean
}

export interface W4Output {
  primaryDiagnosis?: {
    diseaseName?: string
    recommendIcd?: string
    probability?: number
    diagnosisBasis?: string
  }
  differentialDiagnoses?: Array<{
    diseaseName?: string
    recommendIcd?: string
    probability?: number
    diagnosisBasis?: string
  }>
  clinicalAdvice?: string
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
  medicalTechnologies(techType?: 'check' | 'inspection' | 'disposal', keyword?: string) {
    return http<MedicalTechnology[]>({
      url: '/physician/medical-technologies',
      method: 'GET',
      params: { ...(techType ? { techType } : {}), ...(keyword ? { keyword } : {}) },
    })
  },
  availableExaminations() {
    return http<AvailableExamination[]>({ url: '/physician/ai/available-examinations', method: 'GET' })
  },
  aiW1(data: Record<string, unknown>) {
    return http<StructuredRecord>({ url: '/physician/ai/w1/structure', method: 'POST', data })
  },
  aiPreliminaryDiagnosis(data: { registerId: number; text: string; preHandle: boolean; model: string }) {
    return http<PreliminaryDiagnosisOutput>({
      url: '/physician/ai/preliminary-diagnosis',
      method: 'POST',
      data,
      timeout: PRELIMINARY_AI_TIMEOUT_MS,
    })
  },
  savePreliminaryDiagnosis(data: {
    registerId: number
    preliminaryDiagnosis: string
    diseaseIds?: number[]
    suggestedDiseaseNames?: string[]
  }) {
    return http<void>({ url: '/physician/medical-record/preliminary', method: 'POST', data })
  },
  aiW2(registerId: number) {
    return http<W2Output>({ url: '/physician/ai/w2/recommend', method: 'POST', data: { registerId } })
  },
  aiW2b(registerId: number, autoCreateRequests = true) {
    return http<{ simulatedResults: Record<string, unknown>[] }>({
      url: '/physician/ai/w2b/simulate',
      method: 'POST',
      data: { registerId, autoCreateRequests },
    })
  },
  aiW3(registerId: number) {
    return http<W3Output>({ url: '/physician/ai/w3/analyze', method: 'POST', data: { registerId } })
  },
  aiW4(registerId: number) {
    return http<W4Output>({ url: '/physician/ai/w4/diagnose', method: 'POST', data: { registerId } })
  },
  aiPipelineRun(data: Record<string, unknown>) {
    return http<{
      w1: StructuredRecord
      w2: W2Output
      w2b: { simulatedResults: Record<string, unknown>[] }
      w3: W3Output
      w4: W4Output
    }>({ url: '/physician/ai/pipeline/run', method: 'POST', data })
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
    return http<{ prescriptionIds: number[]; totalAmount: number; confirmedDiagnosis?: string }>({
      url: '/physician/prescription',
      method: 'POST',
      data,
    })
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
