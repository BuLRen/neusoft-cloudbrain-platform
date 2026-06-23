export interface MedtechApplicationQuery {
  registrationId?: number
  status?: number
}

export interface CheckApplication {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  physicianName?: string
  medicalTechnologyName?: string
  clinicalDiagnosis?: string
  bodyPart?: string
  status?: number
  statusName?: string
  checkTime?: string
  reportTime?: string
  createTime?: string
}

export interface CheckResultPayload {
  result?: string
  aiAnalysis?: string
  findings?: string
  conclusion?: string
  impression?: string
}

export interface CheckResultSubmitResult {
  status?: string
  reportTime?: string
  aiAnalysisTriggered?: boolean
}

export interface CheckReport extends CheckApplication {
  result?: string
  findings?: string
  conclusion?: string
  impression?: string
  aiAnalysis?: string
  reportUrl?: string
}

export interface InspectionApplication {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  physicianName?: string
  medicalTechnologyName?: string
  specimenType?: string
  status?: number
  statusName?: string
  specimenTime?: string
  resultTime?: string
  createTime?: string
}

export interface InspectionSpecimenPayload {
  [key: string]: unknown
}

export interface InspectionResultPayload {
  result: Record<string, unknown> | string
  aiAnalysis?: string
}

export interface InspectionResultSubmitResult {
  status?: string
  resultTime?: string
  aiAnalysisTriggered?: boolean
}

export interface DisposalApplication {
  id: number
  registerId?: number
  patientId?: number
  patientName?: string
  physicianName?: string
  medicalTechnologyName?: string
  description?: string
  quantity?: number
  status?: number
  statusName?: string
  executeTime?: string
  createTime?: string
}

export interface DisposalResultPayload {
  result?: string
  remarks?: string
}

export interface MedicalTechnologyCatalogItem {
  id: number
  name: string
  code?: string
  type?: string
  departmentId?: number
  departmentName?: string
  price?: number
  specimenType?: string
  container?: string
  instructions?: string
  preparation?: string
  turnaroundTime?: string
  status?: number
  description?: string
  createTime?: string
  updateTime?: string
}
