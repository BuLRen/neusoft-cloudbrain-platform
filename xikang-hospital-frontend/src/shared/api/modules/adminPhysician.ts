import { http } from '../request'

export interface PhysicianAdminRecord {
  id: number
  deptmentId: number
  deptName?: string
  registLevelId: number
  registName?: string
  realname: string
  delmark: number
  userId?: number
  username?: string
  accountStatus?: number
}

export interface PhysicianAdminPage {
  records: PhysicianAdminRecord[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface PhysicianSavePayload {
  realname: string
  deptmentId: number
  registLevelId: number
  createAccount?: boolean
  username?: string
  password?: string
}

export const adminPhysicianApi = {
  list(params?: {
    departmentId?: number
    keyword?: string
    includeDisabled?: boolean
    page?: number
    size?: number
  }) {
    return http<PhysicianAdminPage>({
      url: '/registration/admin/physicians',
      method: 'GET',
      params,
    })
  },

  get(id: number) {
    return http<PhysicianAdminRecord>({
      url: `/registration/admin/physicians/${id}`,
      method: 'GET',
    })
  },

  create(data: PhysicianSavePayload) {
    return http<PhysicianAdminRecord>({
      url: '/registration/admin/physicians',
      method: 'POST',
      data,
    })
  },

  update(id: number, data: PhysicianSavePayload) {
    return http<PhysicianAdminRecord>({
      url: `/registration/admin/physicians/${id}`,
      method: 'PUT',
      data,
    })
  },

  updateStatus(id: number, enabled: boolean) {
    return http<PhysicianAdminRecord>({
      url: `/registration/admin/physicians/${id}/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },

  createAccount(id: number, data: { username?: string; password?: string }) {
    return http<void>({
      url: `/registration/admin/physicians/${id}/account`,
      method: 'POST',
      data,
    })
  },

  resetPassword(id: number, password?: string) {
    return http<void>({
      url: `/registration/admin/physicians/${id}/account/password`,
      method: 'PUT',
      data: { password },
    })
  },

  updateAccountStatus(id: number, enabled: boolean) {
    return http<void>({
      url: `/registration/admin/physicians/${id}/account/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },
}
