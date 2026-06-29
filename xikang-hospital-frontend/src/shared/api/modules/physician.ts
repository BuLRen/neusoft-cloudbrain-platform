import { http } from '../request'

/** Dify 初步诊断 blocking 调用超时（与后端 read-timeout-ms 一致） */
const PRELIMINARY_AI_TIMEOUT_MS = 5 * 60 * 1000
/** Dify W2 检查推荐 blocking 调用超时 */
const W2_AI_TIMEOUT_MS = 5 * 60 * 1000
/** Dify W3 结果解读 blocking 调用超时 */
const W3_AI_TIMEOUT_MS = 5 * 60 * 1000
const W4_AI_TIMEOUT_MS = 5 * 60 * 1000
const W5_AI_TIMEOUT_MS = 5 * 60 * 1000
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

/** AI 初步诊断 — 单条建议疾病（与工作流 diseaseDetail 对齐，部分字段待工作流补充） */
export interface SuggestedDiseaseItem {
  diseaseId?: number
  diseaseName?: string
  recommendIcd?: string
  symptoms?: string
  confidenceLevel?: string
  /** 工作流 schema 为 string，后端会规范为 number */
  rank?: number | string
  role?: 'primary' | 'differential' | string
  rationale?: string
  diagnosisBasis?: string
  keyEvidence?: string[]
  missingOrWeakEvidence?: string[]
  recommendedWorkup?: string[]
}

export interface ExcludedDiagnosisItem {
  diseaseName?: string
  reason?: string
}

export interface PreliminaryAiMeta {
  aiDiagnosis?: string
  clinicalSummary?: string
  primaryDiagnosis?: string
  diagnosisBasis?: string
  /** 知识库召回原文，对应工作流 knowledgeBaseRecall */
  knowledgeBaseRecall?: string
  isRecalled?: boolean
  confidence?: number
  modelId?: string
  llmModel?: string
  suggestedDiseaseNames?: string[]
  suggestedDiseases?: SuggestedDiseaseItem[]
  excludedDiagnoses?: ExcludedDiagnosisItem[]
  redFlags?: string[]
  preHandle?: boolean
}

export interface PreliminaryDiagnosisOutput {
  registerId?: number
  diagnosisText?: string
  clinicalSummary?: string
  primaryDiagnosis?: string
  diagnosisBasis?: string
  knowledgeBaseRecall?: string
  isRecalled?: boolean
  confidence?: number
  modelId?: string
  llmModel?: string
  suggestedDiseases?: SuggestedDiseaseItem[]
  excludedDiagnoses?: ExcludedDiagnosisItem[]
  redFlags?: string[]
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
  checkRemark?: string
  aiAnalysis?: AiExamAnalysis | null
}

export interface InspectionResult {
  id: number
  techName: string
  inspectionPosition?: string
  inspectionResult?: string
  inspectionState?: string
  inspectionTime?: string
  inspectionRemark?: string
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

export interface W2RecommendedExamination {
  techId: number
  techName: string
  techType: string
  reason: string
  priority: number
  purpose?: string
  position?: string
  remark?: string
}

export interface W2UnmatchedSuggestion {
  name: string
  reason: string
}

export interface W2Output {
  preliminaryAssessment?: string
  recommendedExaminations?: W2RecommendedExamination[]
  notRecommendedNote?: string
  unmatchedSuggestions?: W2UnmatchedSuggestion[]
  workflowRunId?: string
  modelId?: string
}

export interface W3IndicatorRow {
  itemCode?: string
  itemName: string
  value: string | number
  unit?: string
  referenceRange?: string
  status: string
  aiNote?: string
}

export interface W3ExamSummary {
  techName: string
  techType?: string
  riskLevel?: string
  clinicalImpression?: string
  indicatorRows?: W3IndicatorRow[]
  keyFindings?: string[]
  interpretation?: string
}

export interface W3Output {
  clinicalImpression?: string
  examSummaries?: W3ExamSummary[]
  overallAnalysis?: string
  explicitNonDiagnosis?: boolean
}

export interface W3Status {
  registerId?: number
  completed: boolean
  examSummaryCount?: number
  clinicalImpression?: string
  overallAnalysis?: string
  explicitNonDiagnosis?: boolean
  w3Output?: W3Output
}

export interface W4Suggestion {
  id?: number
  diseaseId?: number
  diagnosisName?: string
  diseaseName?: string
  recommendIcd?: string
  probability?: number
  riskLevel?: string
  diagnosisBasis?: string
  treatmentDirection?: string
  sortOrder?: number
  isAdopted?: boolean
}

export interface W4FallbackSuggestion {
  diagnosisName?: string
  estimatedIcdPrefix?: string
  probability?: number
  riskLevel?: string
  diagnosisBasis?: string
  note?: string
}

export interface W4Output {
  status?: 'success' | 'empty' | 'fallback'
  registerId?: number
  suggestions?: W4Suggestion[]
  fallbackSuggestions?: W4FallbackSuggestion[]
  clinicalSummaryForDoctor?: string
  differentialDiagnosis?: Array<{ diagnosisName?: string; reason?: string }>
  warningSigns?: string[]
  searchAdvice?: string
  workflowRunId?: string
  modelId?: string
}

export interface W5Suggestion {
  id?: number
  drugId?: number
  drugName?: string
  drugCode?: string
  recommendUsage?: string
  recommendQuantity?: number
  confidence?: number
  recommendationBasis?: string
  cautionNotes?: string
  sortOrder?: number
  isAdopted?: number
}

export interface W5FallbackSuggestion {
  drugName?: string
  recommendUsage?: string
  recommendationBasis?: string
  note?: string
}

export interface W5Output {
  status?: 'success' | 'fallback'
  registerId?: number
  suggestions?: W5Suggestion[]
  fallbackSuggestions?: W5FallbackSuggestion[]
  clinicalSummaryForDoctor?: string
  allergyWarnings?: string[]
  searchAdvice?: string
  workflowRunId?: string
  modelId?: string
}

export interface PhysicianHistoricalSummary {
  totalCompletedVisits: number
  todayCompletedVisits: number
  uniquePatientsServed: number
  totalEncounters: number
  totalCheckOrders: number
  totalInspectionOrders: number
  todayCheckOrders: number
  todayInspectionOrders: number
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
  patient(registerId: number) {
    return http<PhysicianPatient>({ url: `/physician/patients/${registerId}`, method: 'GET' })
  },
  patientStats() {
    return http<{ totalVisited: number; totalWaiting: number }>({ url: '/physician/patient-stats', method: 'GET' })
  },
  statsSummary() {
    return http<PhysicianHistoricalSummary>({ url: '/physician/stats/summary', method: 'GET' })
  },
  startEncounter(registerId: number) {
    return http<{ registerId: number; visitState: number }>({
      url: `/physician/register/${registerId}/visit-state`,
      method: 'PATCH',
      data: { action: 'start' },
    })
  },
  endVisit(registerId: number) {
    return http<{ registerId: number; visitState: number }>({
      url: `/physician/register/${registerId}/visit-state`,
      method: 'PATCH',
      data: { action: 'end' },
    })
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
    return http<W2Output>({
      url: '/physician/ai/w2/recommend',
      method: 'POST',
      data: { registerId },
      timeout: W2_AI_TIMEOUT_MS,
    })
  },
  aiW2b(registerId: number, autoCreateRequests = true) {
    return http<{ simulatedResults: Record<string, unknown>[] }>({
      url: '/physician/ai/w2b/simulate',
      method: 'POST',
      data: { registerId, autoCreateRequests },
    })
  },
  aiW3(registerId: number) {
    return http<W3Output>({
      url: '/physician/ai/w3/analyze',
      method: 'POST',
      data: { registerId },
      timeout: W3_AI_TIMEOUT_MS,
    })
  },
  w3Status(registerId: number) {
    return http<W3Status>({ url: '/physician/ai/w3/status', method: 'GET', params: { registerId } })
  },
  aiW4(registerId: number) {
    return http<W4Output>({
      url: '/physician/ai/w4/diagnose',
      method: 'POST',
      data: { registerId },
      timeout: W4_AI_TIMEOUT_MS,
    })
  },
  aiW5(registerId: number) {
    return http<W5Output>({
      url: '/physician/ai/w5/recommend-drugs',
      method: 'POST',
      data: { registerId },
      timeout: W5_AI_TIMEOUT_MS,
    })
  },
  w5Suggestions(registerId: number) {
    return http<W5Suggestion[]>({
      url: `/physician/ai/w5/suggestions/${registerId}`,
      method: 'GET',
    })
  },
  adoptW5Suggestion(id: number) {
    return http<void>({
      url: `/physician/ai/w5/suggestions/${id}/adopt`,
      method: 'PATCH',
    })
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
    return http<{ requestIds: number[]; visitState?: number }>({ url: '/physician/check-request', method: 'POST', data })
  },
  createInspectionRequest(data: Record<string, unknown>) {
    return http<{ requestIds: number[]; visitState?: number }>({ url: '/physician/inspection-request', method: 'POST', data })
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
  drug(id: number) {
    return http<Drug>({ url: `/physician/drugs/${id}`, method: 'GET' })
  },
  createPrescription(data: Record<string, unknown>) {
    return http<{ prescriptionIds: number[]; totalAmount: number; confirmedDiagnosis?: string; visitState?: number }>({
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
    return http<W4Suggestion[]>({ url: '/physician/ai/diagnosis-suggestions', method: 'GET', params: { registerId }, skipErrorMessage: true })
  },
}
