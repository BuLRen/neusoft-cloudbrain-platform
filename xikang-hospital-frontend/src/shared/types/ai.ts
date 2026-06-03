export type UrgencyLevel = 'I' | 'II' | 'III' | 'IV' | 'V'
export type ConfidenceLevel = 'high' | 'medium' | 'low'

export interface TriageDoctorRecommendation {
  id: number
  name: string
  title?: string
  available?: boolean
}

export interface TriageAnalysisDetail {
  possibleConditions?: string[]
  suggestedExaminations?: string[]
  selfCareAdvice?: string
}

export interface TriageAnalyzePayload {
  symptoms: string
  patientId?: number
  sessionId?: string
}

export interface TriageAnalysisResult {
  // 紧迫性分级（面向患者）
  urgencyLevel?: UrgencyLevel           // I-V 五级
  urgencyAdvice?: string                 // 一句话行动建议
  // 兼容旧字段
  riskLevel?: string                     // normal/critical → low/critical
  // 推荐科室
  recommendedDepartment?: string
  recommendedDepartmentId?: number
  // 分诊依据
  departmentReason?: string              // 为什么要推荐这个科室
  // 推荐挂号级别
  recommendedRegistLevelId?: number      // 1=普通号, 2=专家号, 3=主任医师号
  registLevelReason?: string             // 挂号级别推荐理由
  alternativeDepartments?: string[]      // 备选科室
  // 可信度
  confidenceLevel?: ConfidenceLevel      // high/medium/low
  confidenceReason?: string              // 可信度说明
  // 红旗征
  redFlags?: string[]                    // 必须立即就医的症状
  // 自助建议
  selfCareAdvice?: string
  // 推荐医生
  recommendedDoctors?: TriageDoctorRecommendation[]
  // AI分析详情
  aiAnalysis?: TriageAnalysisDetail | null
  sessionId?: string
}

export interface TriageChatPayload {
  message: string
  sessionId?: string
}

export interface TriageChatResult {
  reply?: string
  sessionId?: string
  suggestions?: string[]
}

export interface TriageHistoryRecord {
  id: number
  patientId?: number
  sessionId?: string
  symptoms?: string
  symptomsJson?: string
  conversationHistory?: string
  recommendedDepartmentId?: number
  recommendedDepartment?: string
  recommendedPhysicianId?: number
  recommendedPhysicianName?: string
  riskLevel?: string
  aiAnalysis?: string | TriageAnalysisDetail | null
  possibleConditions?: string
  suggestedExaminations?: string
  status?: string
  createTime?: string
  updateTime?: string
}

export interface PrevisitPayload {
  patientId: number
  registerId?: number
  sessionId?: string
}

export interface PrevisitResult {
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  allergyHistory?: string
  physicalExamination?: string
  preliminaryDiagnosis?: string
  summary?: string
  suggestedTests?: string[]
  sessionId?: string
}

export interface PrevisitSummaryPayload {
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  allergyHistory?: string
  physicalExamination?: string
  preliminaryDiagnosis?: string
  summary?: string
  suggestedTests?: string[]
  sessionId?: string
  [key: string]: unknown
}

export interface PrevisitSummaryResult {
  summary?: string
  structuredData?: {
    chiefComplaint?: string
    presentIllness?: string
    pastHistory?: string
    allergyHistory?: string
    physicalExamination?: string
  }
}

export interface PrevisitChatPayload {
  message: string
  sessionId?: string
}

export interface PrevisitChatResult {
  reply?: string
  sessionId?: string
  nextQuestions?: string[]
}

export interface PrevisitRecord {
  id: number
  patientId?: number
  registerId?: number
  sessionId?: string
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  allergyHistory?: string
  physicalExamination?: string
  preliminaryDiagnosis?: string
  summary?: string
  rawConversation?: string
  status?: string
  createTime?: string
  updateTime?: string
}

export interface ExamAnalyzePayload {
  patientId?: number
  registerId?: number
  requestId?: number
  examType?: string
  result?: unknown
}

export interface ExamAnalyzeIndicator {
  name?: string
  value?: string
  reference?: string
  status?: string
}

export interface ExamAnalyzeResult {
  abnormalIndicators?: ExamAnalyzeIndicator[]
  riskLevel?: string
  analysisReport?: string
  suggestions?: string[]
}

export interface FollowUpQuestion {
  question?: string
  type?: string
  [key: string]: unknown
}

export interface FollowUpCreatePayload {
  patientId: number
  registerId?: number
  prescriptionId?: number
}

export interface FollowUpCreateResult {
  planType?: string
  startDate?: string
  endDate?: string
  frequency?: string
  followUpItems?: FollowUpQuestion[]
  instructions?: string
  planId?: number
  status?: string
}

export type FollowUpPlanStatus = 0 | 1 | 2 | 3

export interface FollowUpPlan {
  id: number
  patientId?: number
  registerId?: number
  prescriptionId?: number
  planType?: string
  startDate?: string
  endDate?: string
  frequency?: string
  followUpItems?: FollowUpQuestion[]
  instructions?: string
  status?: FollowUpPlanStatus | string
  createTime?: string
  updateTime?: string
}

export interface FollowUpFeedbackPayload {
  patientId: number
  medicationCompliance?: string
  symptomFeedback?: string
  sideEffects?: string
  recoveryStatus?: string
}

export interface FollowUpRecord {
  id: number
  planId?: number
  patientId?: number
  medicationCompliance?: string
  symptomFeedback?: string
  sideEffects?: string
  recoveryStatus?: string
  aiAssessment?: string
  nextFollowUpDate?: string
  remark?: string
  recordTime?: string
  createTime?: string
}

export interface MedicationGuidePayload {
  drugName: string
}

export interface MedicationGuideResult {
  drugName?: string
  usage?: string
  dosage?: string
  timing?: string
  duration?: string
  precautions?: string[]
  sideEffects?: string[]
  storage?: string
}
