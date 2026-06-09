import { http } from '../request'

export interface PatientInfo {
  id: number
  realName: string
  idCard: string
  gender: string
  birthdate: string
  phone?: string
  avatar?: string
  homeAddress?: string
  allergyHistory?: string
  accountBalance?: number
  delmark: number
  relation?: string
  isPrimary?: number
  createTime?: string
  updateTime?: string
}

export const patientApi = {
  /**
   * 获取患者列表（本人+家人）
   */
  async getPatientList(userId: number): Promise<PatientInfo[]> {
    return http<PatientInfo[]>({
      method: 'GET',
      url: '/patient/list',
      params: { userId },
    })
  },

  /**
   * 获取单个患者信息
   */
  async getPatient(patientId: number): Promise<PatientInfo> {
    return http<PatientInfo>({
      method: 'GET',
      url: `/patient/${patientId}`,
    })
  },

  /**
   * 添加家人（创建患者档案并建立关联）
   * @param userId 用户ID
   * @param patient 患者信息
   * @param relation 关系（父亲、母亲、配偶、子女、其他）
   */
  async addFamilyMember(userId: number, patient: Partial<PatientInfo>, relation: string): Promise<void> {
    return http<void>({
      method: 'POST',
      url: '/patient/family',
      params: { userId, relation },
      data: patient,
    })
  },

  /**
   * 更新患者档案
   */
  async updatePatient(patientId: number, patient: Partial<PatientInfo>): Promise<void> {
    return http<void>({
      method: 'PUT',
      url: `/patient/${patientId}`,
      data: patient,
    })
  },

  /**
   * 删除患者档案
   */
  async deletePatient(patientId: number): Promise<void> {
    return http<void>({
      method: 'DELETE',
      url: `/patient/${patientId}`,
    })
  },

  /**
   * 设置默认就诊人
   */
  async setDefaultPatient(patientId: number, userId: number): Promise<void> {
    return http<void>({
      method: 'PUT',
      url: `/patient/${patientId}/default`,
      params: { userId },
    })
  },

  async getBalance(patientId: number): Promise<{ patientId: number; accountBalance: number }> {
    return http<{ patientId: number; accountBalance: number }>({
      method: 'GET',
      url: `/patient/${patientId}/balance`,
    })
  },

  async rechargeBalance(patientId: number, amount: number): Promise<{ patientId: number; accountBalance: number }> {
    return http<{ patientId: number; accountBalance: number }>({
      method: 'POST',
      url: `/patient/${patientId}/balance/recharge`,
      data: { amount },
    })
  },
}