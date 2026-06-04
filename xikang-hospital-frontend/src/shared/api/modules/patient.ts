import { authApi } from './auth'

export interface PatientInfo {
  patientId: number
  realName: string
  gender: string
  relation: string
  isPrimary: number
  allergyHistory?: string
}

export interface Patient extends PatientInfo {
  phone?: string
  avatar?: string
  homeAddress?: string
  birthdate?: string
}

export const patientApi = {
  /**
   * 获取患者列表（本人+家人）
   */
  getPatientList(userId: number): Promise<Result<Patient[]>> {
    return authApi.get<Result<Patient[]>>('/api/patient/list', { userId })
  },

  /**
   * 获取单个患者信息
   */
  getPatient(patientId: number): Promise<Result<Patient>> {
    return authApi.get<Result<Patient>>(`/api/patient/${patientId}`)
  },

  /**
   * 创建患者档案
   */
  createPatient(patient: Partial<Patient>): Promise<Result<Patient>> {
    return authApi.post<Result<Patient>>('/api/patient', patient)
  },

  /**
   * 更新患者档案
   */
  updatePatient(patientId: number, patient: Partial<Patient>): Promise<Result<void>> {
    return authApi.put<Result<void>>(`/api/patient/${patientId}`, patient)
  },

  /**
   * 删除患者档案
   */
  deletePatient(patientId: number): Promise<Result<void>> {
    return authApi.delete<Result<void>>(`/api/patient/${patientId}`)
  },
}

interface Result<T> {
  code: number
  message: string
  data: T
}