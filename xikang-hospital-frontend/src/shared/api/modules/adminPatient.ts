import { http } from '../request'

export interface PatientManagedUser {
  userId: number
  username?: string
  relation?: string
}

export interface PatientAdminRecord {
  id: number
  realName: string
  idCard: string
  gender: string
  birthdate: string
  phone?: string
  homeAddress?: string
  allergyHistory?: string
  accountBalance: number
  delmark: number
  createTime?: string
  updateTime?: string
  managedUsers?: PatientManagedUser[]
}

export interface PatientAdminPage {
  records: PatientAdminRecord[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface PatientSavePayload {
  realName: string
  idCard: string
  gender: string
  birthdate?: string
  phone?: string
  homeAddress?: string
  allergyHistory?: string
}

export const adminPatientApi = {
  list(params?: {
    keyword?: string
    includeDisabled?: boolean
    page?: number
    size?: number
  }) {
    return http<PatientAdminPage>({
      url: '/registration/admin/patients',
      method: 'GET',
      params,
    })
  },

  get(id: number) {
    return http<PatientAdminRecord>({
      url: `/registration/admin/patients/${id}`,
      method: 'GET',
    })
  },

  create(data: PatientSavePayload) {
    return http<PatientAdminRecord>({
      url: '/registration/admin/patients',
      method: 'POST',
      data,
    })
  },

  update(id: number, data: Partial<PatientSavePayload>) {
    return http<PatientAdminRecord>({
      url: `/registration/admin/patients/${id}`,
      method: 'PUT',
      data,
    })
  },

  updateStatus(id: number, delmark: number) {
    return http<PatientAdminRecord>({
      url: `/registration/admin/patients/${id}/status`,
      method: 'PATCH',
      data: { delmark },
    })
  },
}
