import { http } from '../request'

export interface CriticalItemHit {
  itemName?: string
  fieldKey?: string
  value?: string
  unit?: string
  referenceRange?: string
  rule?: string
  severity?: string
  reason?: string
}

export interface CriticalDetectResult {
  suspected: boolean
  severity?: string
  detectSource?: string
  items?: CriticalItemHit[]
}

export interface CriticalValueAlert {
  id: number
  registerId: number
  patientName?: string
  caseNumber?: string
  sourceType: 'inspection' | 'check' | 'disposal' | string
  sourceId: number
  techName?: string
  criticalItems?: CriticalItemHit[] | string
  severity?: string
  reporterId?: number
  reporterName?: string
  doctorId?: number
  doctorName?: string
  status: 'PENDING' | 'ACKNOWLEDGED' | 'HANDLED' | 'ESCALATED' | 'CLOSED' | string
  reportedTime?: string
  acknowledgedTime?: string
  handledTime?: string
  handleNote?: string
  escalatedTime?: string
  ackDeadline?: string
}

export interface CriticalValueBoardStats {
  pendingCount?: number
  escalatedCount?: number
  overdueRate?: number
  avgAckMinutes?: number
  avgHandleMinutes?: number
}

export interface CriticalValueReportPayload {
  registerId: number
  sourceType: 'inspection' | 'check' | 'disposal'
  sourceId: number
  techName?: string
  severity?: string
  items: CriticalItemHit[]
}

export const criticalValueApi = {
  report(data: CriticalValueReportPayload) {
    return http<{ alertId: number; status: string; ackDeadline?: string }>({
      url: '/medtech/critical-value/report',
      method: 'POST',
      data,
    })
  },
  pending(doctorId: number) {
    return http<CriticalValueAlert[]>({
      url: '/medtech/critical-value/pending',
      method: 'GET',
      params: { doctorId },
    })
  },
  ack(alertId: number) {
    return http<CriticalValueAlert>({
      url: `/medtech/critical-value/${alertId}/ack`,
      method: 'POST',
    })
  },
  handle(alertId: number, handleNote: string) {
    return http<CriticalValueAlert>({
      url: `/medtech/critical-value/${alertId}/handle`,
      method: 'POST',
      data: { handleNote },
    })
  },
  board() {
    return http<{ alerts: CriticalValueAlert[]; stats: CriticalValueBoardStats }>({
      url: '/medtech/critical-value/board',
      method: 'GET',
    })
  },
}
