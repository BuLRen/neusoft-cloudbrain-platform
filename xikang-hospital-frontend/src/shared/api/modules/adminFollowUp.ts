import { http } from '../request'
import { blobClient, downloadBlob, filenameFromContentDisposition } from '../blobClient'
import type { PersonnelImportResult, PersonnelListFilters } from '@/shared/types/adminPersonnel'

export interface FollowUpAdminRecord {
  id: number
  deptmentId: number
  deptName?: string
  realname: string
  delmark: number
  userId?: number
  username?: string
  accountStatus?: number
}

export interface FollowUpAdminPage {
  records: FollowUpAdminRecord[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface FollowUpSavePayload {
  realname: string
  deptmentId: number
  createAccount?: boolean
  username?: string
  password?: string
}

export const adminFollowUpApi = {
  list(params?: {
    departmentId?: number
    keyword?: string
    includeDisabled?: boolean
    page?: number
    size?: number
  }) {
    return http<FollowUpAdminPage>({
      url: '/registration/admin/follow-up-employees',
      method: 'GET',
      params,
    })
  },

  get(id: number) {
    return http<FollowUpAdminRecord>({
      url: `/registration/admin/follow-up-employees/${id}`,
      method: 'GET',
    })
  },

  create(data: FollowUpSavePayload) {
    return http<FollowUpAdminRecord>({
      url: '/registration/admin/follow-up-employees',
      method: 'POST',
      data,
    })
  },

  update(id: number, data: FollowUpSavePayload) {
    return http<FollowUpAdminRecord>({
      url: `/registration/admin/follow-up-employees/${id}`,
      method: 'PUT',
      data,
    })
  },

  updateStatus(id: number, enabled: boolean) {
    return http<FollowUpAdminRecord>({
      url: `/registration/admin/follow-up-employees/${id}/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },

  createAccount(id: number, data: { username?: string; password?: string }) {
    return http<void>({
      url: `/registration/admin/follow-up-employees/${id}/account`,
      method: 'POST',
      data,
    })
  },

  resetPassword(id: number, password?: string) {
    return http<void>({
      url: `/registration/admin/follow-up-employees/${id}/account/password`,
      method: 'PUT',
      data: { password },
    })
  },

  updateAccountStatus(id: number, enabled: boolean) {
    return http<void>({
      url: `/registration/admin/follow-up-employees/${id}/account/status`,
      method: 'PATCH',
      data: { enabled },
    })
  },

  async downloadTemplate() {
    const res = await blobClient.get('/registration/admin/follow-up-employees/import/template', {
      responseType: 'blob',
    })
    downloadBlob(
      res.data,
      filenameFromContentDisposition(res.headers['content-disposition'], '随访人员导入模板.xlsx'),
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  },

  async exportExcel(filters?: PersonnelListFilters) {
    const res = await blobClient.get('/registration/admin/follow-up-employees/export', {
      responseType: 'blob',
      params: {
        departmentId: filters?.departmentId,
        keyword: filters?.keyword || undefined,
        includeDisabled: filters?.includeDisabled,
      },
    })
    downloadBlob(
      res.data,
      filenameFromContentDisposition(res.headers['content-disposition'], '随访人员.xlsx'),
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  },

  importExcel(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http<PersonnelImportResult>({
      url: '/registration/admin/follow-up-employees/import',
      method: 'POST',
      data: formData,
    })
  },
}
