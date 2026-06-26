import { http } from '../request'

export interface MedtechAdminRecord {
  id: number
  deptmentId: number
  deptName?: string
  realname: string
  delmark: number
  userId?: number
  username?: string
  accountStatus?: number
}

export interface MedtechAdminPage {
  records: MedtechAdminRecord[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface MedtechSavePayload {
  realname: string
  deptmentId: number
  createAccount?: boolean
  username?: string
  password?: string
}

export const adminMedtechApi = {
  list(params?: {
    departmentId?: number
    keyword?: string
    includeDisabled?: boolean
    page?: number
    size?: number
  }) {
    return http<MedtechAdminPage>({
      url: '/registration/admin/medtech-employees',
      method: 'GET',
      params,
    })
  },

  get(id: number) {
    return http<MedtechAdminRecord>({
      url: `/registration/admin/medtech-employees/${id}`,
      method: 'GET',
    })
  },

  create(data: MedtechSavePayload) {
    return http<MedtechAdminRecord>({
      url: '/registration/admin/medtech-employees',
      method: 'POST',
      data,
    })
  },

  update(id: number, data: MedtechSavePayload) {
    return http<MedtechAdminRecord>({
      url: `/registration/admin/medtech-employees/${id}`,
      method: 'PUT',
      data,
    })
  },

  updateStatus(id: number, enabled: boolean) {
    return http<MedtechAdminRecord>({
      url: `/registration/admin/medtech-employees/${id}/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },

  createAccount(id: number, data: { username?: string; password?: string }) {
    return http<void>({
      url: `/registration/admin/medtech-employees/${id}/account`,
      method: 'POST',
      data,
    })
  },

  resetPassword(id: number, password?: string) {
    return http<void>({
      url: `/registration/admin/medtech-employees/${id}/account/password`,
      method: 'PUT',
      data: { password },
    })
  },

  updateAccountStatus(id: number, enabled: boolean) {
    return http<void>({
      url: `/registration/admin/medtech-employees/${id}/account/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },
}
