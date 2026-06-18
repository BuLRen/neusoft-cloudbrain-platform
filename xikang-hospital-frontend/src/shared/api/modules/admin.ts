import { http } from '../request'
import type { PageResult } from '../result'

export interface MedicalTechnologyItem {
  id: number
  techCode: string
  techName: string
  techFormat?: string
  techPrice: number
  techType: 'check' | 'inspection' | 'disposal'
  priceType?: string
  deptmentId?: number
  deptName?: string
}

export interface DepartmentOption {
  id: number
  deptName: string
}

export type ExaminationItemPayload = {
  techCode: string
  techName: string
  techFormat?: string
  techPrice: number
  techType: 'check' | 'inspection' | 'disposal'
  priceType?: string
  deptmentId?: number
}

export const adminApi = {
  departments() {
    return http<DepartmentOption[]>({ url: '/medtech/departments', method: 'GET' })
  },

  /** 医生开单可选的全部医技项目（分页） */
  pageExaminationItems(params?: { techType?: string; keyword?: string; page?: number; size?: number }) {
    return http<PageResult<MedicalTechnologyItem>>({
      url: '/medtech/medical-technologies',
      method: 'GET',
      params: {
        type: params?.techType,
        keyword: params?.keyword || undefined,
        page: params?.page ?? 1,
        size: params?.size ?? 10,
      },
    })
  },

  createExaminationItem(data: ExaminationItemPayload) {
    return http<MedicalTechnologyItem>({ url: '/medtech/medical-technologies', method: 'POST', data })
  },

  updateExaminationItem(id: number, data: Omit<ExaminationItemPayload, 'techCode'>) {
    return http<MedicalTechnologyItem>({ url: `/medtech/medical-technologies/${id}`, method: 'PUT', data })
  },

  deleteExaminationItem(id: number) {
    return http<void>({ url: `/medtech/medical-technologies/${id}`, method: 'DELETE' })
  },
}
