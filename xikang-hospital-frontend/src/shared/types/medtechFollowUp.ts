export interface FollowUpPatientOption {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  visitState?: number
  enrolled?: boolean
  enrollmentPriority?: string
  diseases?: FollowUpDisease[]
}

export interface FollowUpDisease {
  diseaseId?: number
  diseaseName?: string
  diseaseCategory?: string
  diseaseIcd?: string
}

export interface FollowUpPatientProfile {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  visitState?: number
  diagnosis?: string
  preliminaryDiagnosis?: string
  diseases?: FollowUpDisease[]
  primaryDiseaseCategory?: string
}

export interface FollowUpPatientDetail {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  birthdate?: string
  age?: number
  ageType?: string
  idCard?: string
  cardNumber?: string
  phone?: string
  email?: string
  homeAddress?: string
  contactAddress?: string
  visitDate?: string
  noon?: string
  visitState?: number
  departmentName?: string
  physicianName?: string
  registLevelName?: string
  settleCategoryName?: string
  registMethod?: string
  registMoney?: number
  isBook?: string
  diagnosis?: string
  preliminaryDiagnosis?: string
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  allergy?: string
  physique?: string
  treatmentProposal?: string
  precautions?: string
  diseases?: FollowUpDisease[]
}

export interface FollowUpHealthMetric {
  id?: number
  registerId?: number
  recordDate: string
  recordedAt?: string
  metricKey: string
  metricValue: number
  unit?: string
  source?: string
  note?: string
}

export interface FollowUpOutcomeRecord {
  id: number
  followUpPlanId?: number
  registerId?: number
  symptomRelief?: string
  hasSideEffect?: number
  patientFeedback?: string
  aiAssessment?: string
  aiAdvice?: string
  followUpTime?: string
}

export interface InterviewScheduleItem {
  id?: number
  registerId: number
  caseNumber?: string
  patientName?: string
  weekStartDate: string
  status?: string
  triggerReason?: string
  triggerMetricKey?: string
  patientNotified?: number
  creationTime?: string
}

export interface InterviewScheduleStatus {
  scheduled: boolean
  weekStartDate?: string
  status?: string
  id?: number
}

export interface InterviewSchedulePayload {
  registerId: number
  triggerReason?: string
  triggerMetricKey?: string
  weekStartDate?: string
}

export interface PatientNotifyPayload {
  registerId: number
  patientName?: string
  weekStartDate: string
  message?: string
}

export type FollowUpPriorityLevel = 'normal' | 'high' | 'critical'

export interface FollowUpTrackedDate {
  trackedDate: string
  trackedType?: string
}

export interface FollowUpDashboardPatient {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  departmentId?: number
  enrolled?: boolean
  priorityLevel?: FollowUpPriorityLevel
  interviewIntervalDays?: number
  observationIntervalDays?: number
  lastInterviewDate?: string
  lastTrackedDate?: string
  observedToday?: boolean
  interviewDueToday?: boolean
  observationDueToday?: boolean
  interviewScheduledToday?: boolean
  trackedDates?: FollowUpTrackedDate[]
  diseases?: FollowUpDisease[]
}

export interface FollowUpDayScheduleItem {
  id: number
  registerId?: number
  departmentId?: number
  scheduleDate: string
  itemType?: 'interview' | 'observation' | 'custom'
  title: string
  status?: 'planned' | 'completed' | 'cancelled'
  createdBy?: number
  creationTime?: string
  patientName?: string
  caseNumber?: string
}

export interface FollowUpDashboardStats {
  totalPatients?: number
  todayInterviewsPlanned?: number
  todayObservationPending?: number
}

export interface FollowUpDashboardContext {
  userId?: number
  role?: string
  employeeId?: number
  departmentId?: number
  departmentName?: string
  employeeRealName?: string
  adminAllAccess?: boolean
  targetDate?: string
  effectiveDepartmentId?: number
  stats?: FollowUpDashboardStats
}

export interface FollowUpObservationStatus {
  registerId: number
  observationDate: string
  observed: boolean
  observedBy?: number
  confirmedAt?: string
  note?: string
}

export interface FollowUpSchedulePayload {
  registerId?: number
  departmentId?: number
  scheduleDate: string
  itemType?: 'interview' | 'observation' | 'custom'
  title?: string
}

export interface FollowUpObservationConfirmPayload {
  registerId: number
  observationDate?: string
  note?: string
}

export interface FollowUpEnrollPayload {
  registerId: number
  departmentId?: number
  priorityLevel?: FollowUpPriorityLevel
  interviewIntervalDays?: number
  observationIntervalDays?: number
}

export interface FollowUpEnrollResult {
  registerId: number
  departmentId?: number
  priorityLevel?: FollowUpPriorityLevel
  enrolled?: boolean
  realName?: string
  caseNumber?: string
}

export interface PatientFollowUpPlanItem {
  id: number
  registerId?: number
  followUpType?: string
  planStatus?: string
  plannedDate?: string
  contentTemplate?: string
  doctorName?: string
}

export interface PatientFollowUpRecordItem {
  id: number
  registerId?: number
  symptomRelief?: string
  patientFeedback?: string
  followUpTime?: string
}

export interface PatientMedicationItem {
  id: number
  registerId?: number
  drugName?: string
  drugUsage?: string
  drugNumber?: string
}

export interface PatientFollowUpFeedbackPayload {
  registerId: number
  followUpPlanId?: number
  symptom: string
  feedback?: string
  rating?: number
}

export type CommunicationSenderType = 'doctor' | 'patient' | 'ai' | 'system'
export type CommunicationMessageType = 'text' | 'case_summary' | 'notice'
export type CaseSummaryStatus = 'draft' | 'approved' | 'shared' | 'revoked'

export interface FollowUpCommunicationSession {
  id: number
  registerId: number
  departmentId?: number
  status?: 'active' | 'closed'
  aiEscalationEnabled?: boolean
  doctorLastActiveAt?: string
  creationTime?: string
  realName?: string
  caseNumber?: string
  gender?: string
  age?: number
  priorityLevel?: FollowUpPriorityLevel
  patientMessageCount?: number
  lastMessagePreview?: string
  lastMessageTime?: string
}

export interface FollowUpCommunicationMessage {
  id: number
  sessionId: number
  senderType: CommunicationSenderType
  messageType?: CommunicationMessageType
  content: string
  summaryId?: number
  workflowRunId?: string
  creationTime?: string
}

export interface FollowUpCommunicationMessagesPage {
  items: FollowUpCommunicationMessage[]
  total: number
}

export interface FollowUpCaseSummary {
  id?: number
  registerId: number
  sessionId?: number
  aiDraftContent?: string
  aiMedicalAdvice?: string
  aiRiskAlerts?: string
  doctorContent?: string
  status?: CaseSummaryStatus
  sharedToPatient?: boolean
  workflowRunId?: string
  modelId?: string
  approvedBy?: number
  approvedAt?: string
  creationTime?: string
  exists?: boolean
  content?: string
  followUpFocus?: string[]
  confidence?: number
  source?: string
}

export interface FollowUpCommunicationPatientBrief {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  diagnosis?: string
  chiefComplaint?: string
  allergy?: string
  diseases?: FollowUpDisease[]
  recentMetrics?: FollowUpHealthMetric[]
  observedToday?: boolean
  interviewScheduledToday?: boolean
  latestSummary?: FollowUpCaseSummary
}

export interface ProfessionalMetricItem {
  value: number
  unit?: string
  label?: string
}

export interface LastVisitSnapshot {
  registerId: number
  visitDate?: string
  diagnosisSummary?: string
  professionalMetrics?: Record<string, ProfessionalMetricItem>
  doctorName?: string
  departmentName?: string
  updatedAt?: string
}

export interface PatientObservationPayload {
  registerId: number
  metricValue: number
  observedAt?: string
  note?: string
}

/** @deprecated 复诊申请已下线，随访仅提供复诊提醒 */
export interface RevisitRequest {
  id?: number
  registerId: number
  patientId?: number
  reason: string
  urgency?: 'normal' | 'urgent'
  status?: 'pending' | 'acknowledged' | 'scheduled'
  createdAt?: string
  caseNumber?: string
  patientName?: string
}

export interface GlucoseAdvice {
  registerId: number
  riskLevel?: string
  revisitRecommended?: boolean
  adviceText?: string
  recentReportCount?: number
  forecastMin?: number
  forecastMax?: number
  modelId?: string
  confidence?: number
}
