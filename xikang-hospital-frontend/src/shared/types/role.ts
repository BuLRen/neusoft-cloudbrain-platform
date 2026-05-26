
export type UserRole = 'admin' | 'physician' | 'registration' | 'medtech' | 'pharmacy' | 'patient'

export interface RoleOption {
  label: string
  value: UserRole
  description: string
}

export const roleOptions: RoleOption[] = [
  { label: '管理员', value: 'admin', description: '排班、分诊台、基础数据' },
  { label: '门诊医生', value: 'physician', description: '接诊、病历、诊断、处方' },
  { label: '挂号收费员', value: 'registration', description: '挂号、收费、退费' },
  { label: '医技人员', value: 'medtech', description: '检查、检验、处置执行' },
  { label: '药房人员', value: 'pharmacy', description: '发药、退药、药库' },
  { label: '患者', value: 'patient', description: '导诊、预问诊、随访' },
]
