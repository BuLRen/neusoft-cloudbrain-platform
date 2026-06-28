import { http } from '../request'
import { blobClient, downloadBlob, filenameFromContentDisposition } from '../blobClient'
import type { PersonnelImportResult, PersonnelListFilters } from '@/shared/types/adminPersonnel'

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

  async downloadTemplate() {
    const res = await blobClient.get('/registration/admin/physicians/import/template', {
      responseType: 'blob',
    })
    downloadBlob(
      res.data,
      filenameFromContentDisposition(res.headers['content-disposition'], '诊疗医生导入模板.xlsx'),
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  },

  async exportExcel(filters?: PersonnelListFilters) {
    const res = await blobClient.get('/registration/admin/physicians/export', {
      responseType: 'blob',
      params: {
        departmentId: filters?.departmentId,
        keyword: filters?.keyword || undefined,
        includeDisabled: filters?.includeDisabled,
      },
    })
    downloadBlob(
      res.data,
      filenameFromContentDisposition(res.headers['content-disposition'], '诊疗医生.xlsx'),
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  },

  importExcel(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http<PersonnelImportResult>({
      url: '/registration/admin/physicians/import',
      method: 'POST',
      data: formData,
    })
  },
}
