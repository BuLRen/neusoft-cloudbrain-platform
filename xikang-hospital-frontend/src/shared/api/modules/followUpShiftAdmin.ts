import { http } from '../request'
import type {
  FollowUpShiftAiTask,
  FollowUpShiftChangeRequest,
  FollowUpMonitoringTransferRequest,
  FollowUpStaffShift,
} from '@/shared/types/medtechFollowUp'

const adminBase = '/medtech/follow-up/shift/admin'

export const followUpShiftAdminApi = {
  getPlan(departmentId: number, month: string) {
    return http<Record<string, unknown> | null>({
      url: `${adminBase}/plan`,
      method: 'GET',
      params: { departmentId, month },
    })
  },

  listShifts(departmentId: number, from: string, to: string) {
    return http<FollowUpStaffShift[]>({
      url: `${adminBase}/shifts`,
      method: 'GET',
      params: { departmentId, from, to },
    })
  },

  aiGenerate(payload: { departmentId: number; month: string; departmentName?: string }) {
    return http<FollowUpShiftAiTask>({
      url: `${adminBase}/ai-generate`,
      method: 'POST',
      data: payload,
    })
  },

  aiGenerateActive(departmentId: number, month: string) {
    return http<FollowUpShiftAiTask>({
      url: `${adminBase}/ai-generate/active`,
      method: 'GET',
      params: { departmentId, month },
    })
  },

  publish(planId: number) {
    return http<Record<string, unknown>>({
      url: `${adminBase}/publish/${planId}`,
      method: 'POST',
    })
  },

  pendingChangeRequests(departmentId?: number) {
    return http<FollowUpShiftChangeRequest[]>({
      url: `${adminBase}/change-requests/pending`,
      method: 'GET',
      params: departmentId ? { departmentId } : undefined,
    })
  },

  pendingCount(departmentId?: number) {
    return http<number>({
      url: `${adminBase}/change-requests/count`,
      method: 'GET',
      params: departmentId ? { departmentId } : undefined,
    })
  },

  approveChangeRequest(id: number, adminNote?: string) {
    return http<FollowUpShiftChangeRequest>({
      url: `${adminBase}/change-requests/${id}/approve`,
      method: 'POST',
      data: adminNote ? { adminNote } : undefined,
    })
  },

  rejectChangeRequest(id: number, adminNote?: string) {
    return http<FollowUpShiftChangeRequest>({
      url: `${adminBase}/change-requests/${id}/reject`,
      method: 'POST',
      data: adminNote ? { adminNote } : undefined,
    })
  },

  assignMonitoring(payload: { registerId: number; employeeId: number; departmentId?: number }) {
    return http<Record<string, unknown>>({
      url: `${adminBase}/monitoring/assign`,
      method: 'POST',
      data: payload,
    })
  },

  pendingTransferRequests(departmentId?: number) {
    return http<FollowUpMonitoringTransferRequest[]>({
      url: `${adminBase}/monitoring/transfer-requests/pending`,
      method: 'GET',
      params: departmentId ? { departmentId } : undefined,
    })
  },

  pendingTransferCount(departmentId?: number) {
    return http<number>({
      url: `${adminBase}/monitoring/transfer-requests/count`,
      method: 'GET',
      params: departmentId ? { departmentId } : undefined,
    })
  },

  approveTransferRequest(id: number, adminNote?: string) {
    return http<FollowUpMonitoringTransferRequest>({
      url: `${adminBase}/monitoring/transfer-requests/${id}/approve`,
      method: 'POST',
      data: adminNote ? { adminNote } : undefined,
    })
  },

  rejectTransferRequest(id: number, adminNote?: string) {
    return http<FollowUpMonitoringTransferRequest>({
      url: `${adminBase}/monitoring/transfer-requests/${id}/reject`,
      method: 'POST',
      data: adminNote ? { adminNote } : undefined,
    })
  },
}
