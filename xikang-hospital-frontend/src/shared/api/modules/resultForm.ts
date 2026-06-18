import { http } from '../request'
import type { ResultFormField, ResultFormSchema } from '@/shared/types/resultForm'

export interface ResultFormCategory {
  categoryCode: string
  categoryName: string
  description?: string
}

export interface TechExtensionContext {
  techId: number
  techCode: string
  techName: string
  categoryCode: string
  categoryName: string
  baseFields: ResultFormField[]
  extensionFields: ResultFormField[]
}

export const resultFormApi = {
  resolveCheckForm(params: { checkRequestId?: number; medicalTechnologyId?: number }) {
    return http<ResultFormSchema>({
      url: '/medtech/check/result-form/resolve',
      method: 'GET',
      params,
    })
  },

  resolveInspectionForm(params: { inspectionRequestId?: number; medicalTechnologyId?: number }) {
    return http<ResultFormSchema>({
      url: '/medtech/inspection/result-form/resolve',
      method: 'GET',
      params,
    })
  },

  listCategories() {
    return http<ResultFormCategory[]>({ url: '/medtech/result-form/categories', method: 'GET' })
  },

  listCategoryFields(categoryCode: string) {
    return http<ResultFormField[]>({
      url: `/medtech/result-form/categories/${categoryCode}/fields`,
      method: 'GET',
    })
  },

  saveCategoryFields(categoryCode: string, fields: ResultFormField[]) {
    return http<void>({
      url: `/medtech/result-form/categories/${categoryCode}/fields`,
      method: 'PUT',
      data: fields,
    })
  },

  getTechExtensions(techId: number) {
    return http<TechExtensionContext>({
      url: `/medtech/result-form/tech/${techId}/extensions`,
      method: 'GET',
    })
  },

  saveTechExtensions(techId: number, fields: ResultFormField[]) {
    return http<void>({
      url: `/medtech/result-form/tech/${techId}/extensions`,
      method: 'PUT',
      data: fields,
    })
  },
}
