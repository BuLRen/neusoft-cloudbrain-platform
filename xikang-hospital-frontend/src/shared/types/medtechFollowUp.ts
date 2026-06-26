export interface FollowUpPatientOption {
  registerId: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  visitState?: number
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
