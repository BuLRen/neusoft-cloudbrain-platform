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

export type BalanceTransactionType = 'RECHARGE' | 'DEDUCT' | 'REFUND'

export interface PatientBalanceTransaction {
  id: number
  transactionNo: string
  patientId: number
  transactionType: BalanceTransactionType
  amount: number
  balanceBefore: number
  balanceAfter: number
  businessType?: string | null
  businessId?: number | null
  operatorId?: number | null
  operatorName?: string | null
  remark?: string | null
  transactionTime: string
  createTime?: string
}

export interface BalanceChangeResult {
  success: boolean
  message?: string
  patientId?: number
  accountBalance: number
  transactionNo?: string
  transactionType?: BalanceTransactionType
  transactionTime?: string
  idempotent?: boolean
}

function balanceUrl(patientId: number, suffix: string): string {
  return '/patient/' + patientId + suffix
}

export const patientApi = {
  async getPatientList(userId: number): Promise<PatientInfo[]> {
    return http<PatientInfo[]>({
      method: 'GET',
      url: '/patient/list',
      params: { userId },
    })
  },

  async getPatient(patientId: number): Promise<PatientInfo> {
    return http<PatientInfo>({
      method: 'GET',
      url: balanceUrl(patientId, ''),
    })
  },

  async addFamilyMember(userId: number, patient: Partial<PatientInfo>, relation: string): Promise<void> {
    return http<void>({
      method: 'POST',
      url: '/patient/family',
      params: { userId, relation },
      data: patient,
    })
  },

  async updatePatient(patientId: number, patient: Partial<PatientInfo>): Promise<void> {
    return http<void>({
      method: 'PUT',
      url: balanceUrl(patientId, ''),
      data: patient,
    })
  },

  async deletePatient(patientId: number): Promise<void> {
    return http<void>({
      method: 'DELETE',
      url: balanceUrl(patientId, ''),
    })
  },

  async setDefaultPatient(patientId: number, userId: number): Promise<void> {
    return http<void>({
      method: 'PUT',
      url: balanceUrl(patientId, '/default'),
      params: { userId },
    })
  },

  async getBalance(patientId: number): Promise<{ patientId: number; accountBalance: number }> {
    return http<{ patientId: number; accountBalance: number }>({
      method: 'GET',
      url: balanceUrl(patientId, '/balance'),
    })
  },

  async rechargeBalance(patientId: number, amount: number, remark?: string): Promise<BalanceChangeResult> {
    return http<BalanceChangeResult>({
      method: 'POST',
      url: balanceUrl(patientId, '/balance/recharge'),
      data: {
        amount,
        businessType: 'RECHARGE',
        operatorName: '患者自助',
        remark: remark || '账户充值',
      },
    })
  },

  async getBalanceTransactions(patientId: number, type?: BalanceTransactionType): Promise<PatientBalanceTransaction[]> {
    return http<PatientBalanceTransaction[]>({
      method: 'GET',
      url: balanceUrl(patientId, '/balance/transactions'),
      params: type ? { type } : undefined,
    })
  },
}
