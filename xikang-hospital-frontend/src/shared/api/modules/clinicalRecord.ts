import { http } from '../request'

export interface ClinicalTimelineEntry {
  eventType: string
  occurredAt?: string
  title: string
  summary: string
  status?: 'pending' | 'completed'
  sourceType: string
  sourceId?: number
  detail?: Record<string, unknown>
}

export interface ClinicalVisitSummary {
  registerId: number
  caseNumber?: string
  visitDate?: string
  visitState?: number
  departmentName?: string
  physicianName?: string
  diagnosis?: string
  preliminaryDiagnosis?: string
  clinicalArchivedAt?: string
  archived: boolean
  patientVisible: boolean
  archiveStatus: 'archived' | 'pending'
}

export interface ClinicalVisitDetail {
  registerId: number
  patientId?: number
  caseNumber?: string
  realName?: string
  gender?: string
  age?: number
  visitDate?: string
  visitState?: number
  departmentName?: string
  physicianName?: string
  clinicalArchivedAt?: string
  archived: boolean
  patientVisible: boolean
  archiveStatus: 'archived' | 'pending'
  message?: string
  timeline: ClinicalTimelineEntry[]
}

export interface PatientClinicalProfile {
  patientId: number
  realName?: string
  allergySummary?: string
  chronicConditions?: string
  pastDiagnosisSummary?: string
  lastVisitAt?: string
  updatedAt?: string
}

export const clinicalRecordApi = {
  physicianTimeline(registerId: number) {
    return http<{ registerId: number; clinicalArchivedAt?: string; archived?: boolean; timeline: ClinicalTimelineEntry[] }>({
      url: `/physician/clinical-record/visit/${registerId}/timeline`,
      method: 'GET',
    })
  },

  physicianArchive(registerId: number) {
    return http<{ registerId: number; timeline: ClinicalTimelineEntry[] }>({
      url: `/physician/clinical-record/visit/${registerId}/archive`,
      method: 'POST',
    })
  },

  physicianProfile(patientId: number) {
    return http<PatientClinicalProfile>({
      url: `/physician/clinical-record/patient/${patientId}/profile`,
      method: 'GET',
    })
  },

  patientVisits(patientId: number) {
    return http<ClinicalVisitSummary[]>({
      url: `/registration/clinical-record/patient/${patientId}/visits`,
      method: 'GET',
    })
  },

  patientVisitDetail(registerId: number) {
    return http<ClinicalVisitDetail>({
      url: `/registration/clinical-record/visit/${registerId}`,
      method: 'GET',
    })
  },

  patientProfile(patientId: number) {
    return http<PatientClinicalProfile>({
      url: `/registration/clinical-record/patient/${patientId}/profile`,
      method: 'GET',
    })
  },
}
