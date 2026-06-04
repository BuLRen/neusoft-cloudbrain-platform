import { http } from '../request'

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

export type CheckEquipmentPayload = {
  techCode: string
  techName: string
  techFormat?: string
  techPrice: number
  techType: 'check'
  priceType?: string
  deptmentId?: number
}

export const adminApi = {
  departments() {
    return http<DepartmentOption[]>({ url: '/medtech/departments', method: 'GET' })
  },

  listCheckEquipment(keyword?: string) {
    return http<MedicalTechnologyItem[]>({
      url: '/medtech/medical-technologies',
      method: 'GET',
      params: { type: 'check', keyword: keyword || undefined },
    })
  },

  createCheckEquipment(data: CheckEquipmentPayload) {
    return http<MedicalTechnologyItem>({
      url: '/medtech/medical-technologies',
      method: 'POST',
      data: { ...data, techType: 'check' },
    })
  },

  updateCheckEquipment(id: number, data: Omit<CheckEquipmentPayload, 'techCode'>) {
    return http<MedicalTechnologyItem>({
      url: `/medtech/medical-technologies/${id}`,
      method: 'PUT',
      data: { ...data, techType: 'check' },
    })
  },

  deleteCheckEquipment(id: number) {
    return http<void>({ url: `/medtech/medical-technologies/${id}`, method: 'DELETE' })
  },
}
