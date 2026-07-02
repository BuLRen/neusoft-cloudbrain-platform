import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/shared/types/role'
import { authApi } from '@/shared/api/modules/auth'
import { canRefreshSession, refreshAccessToken } from '@/shared/api/authRefresh'

export interface PatientInfo {
  patientId: number
  realName: string
  gender: string
  relation: string
  isPrimary: number
  idCard?: string
  birthdate?: string
  phone?: string
  homeAddress?: string
  allergyHistory?: string
  accountBalance?: number
}

export const useAuthStore = defineStore('auth', () => {
  const userId = ref('')
  const employeeId = ref<number | null>(null)
  const username = ref('')  // 登录用户名
  const role = ref<UserRole>('admin')
  const realName = ref('')
  const sessionChecked = ref(false)
  const token = ref('')
  const patients = ref<PatientInfo[]>([])
  const currentPatientId = ref<number | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  function selectDefaultPatient() {
    const defaultPatient = patients.value.find(p => p.isPrimary === 1) || patients.value[0] || null
    currentPatientId.value = defaultPatient?.patientId || null
  }

  // 当前选中的患者信息
  const currentPatient = computed(() => {
    if (!currentPatientId.value) {
      // 默认返回本人
      return patients.value.find(p => p.isPrimary === 1) || patients.value[0] || null
    }
    return patients.value.find(p => p.patientId === currentPatientId.value) || null
  })

  async function loadSession() {
    try {
      // 先从 localStorage 恢复 token 状态，保证 isAuthenticated 立即生效
      const storedToken = localStorage.getItem('access_token') || ''
      if (storedToken) {
        token.value = storedToken
      }

      // 如果没有 token，直接返回
      if (!token.value && !canRefreshSession()) {
        sessionChecked.value = true
        return
      }

      if (!token.value && canRefreshSession()) {
        await refreshAccessToken()
      }

      const data = await authApi.get<{
        userId: string
        username: string
        role: UserRole
        realName: string
        employeeId?: number
        patients?: PatientInfo[]
      }>('/auth/me', undefined, { skipErrorMessage: true })
      if (data) {
        userId.value = String(data.userId)
        username.value = data.username || ''
        role.value = data.role || 'admin'
        realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
        employeeId.value = data.employeeId ?? null
        patients.value = data.patients || []
        selectDefaultPatient()
      }
    } catch {
      if (canRefreshSession()) {
        try {
          await refreshAccessToken()
          const data = await authApi.get<{
            userId: string
            username: string
            role: UserRole
            realName: string
            employeeId?: number
            patients?: PatientInfo[]
          }>('/auth/me', undefined, { skipErrorMessage: true })
          if (data) {
            userId.value = String(data.userId)
            username.value = data.username || ''
            role.value = data.role || 'admin'
            realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
            employeeId.value = data.employeeId ?? null
            patients.value = data.patients || []
            selectDefaultPatient()
            sessionChecked.value = true
            return
          }
        } catch {
          // fall through to warn
        }
      }
      console.warn('Session load failed, will retry on next request')
    } finally {
      sessionChecked.value = true
    }
  }

  async function login(loginUsername: string, password: string) {
    const data = await authApi.post<{
      userId: string
      username: string
      role: UserRole
      token: string
      refreshToken: string
      realName: string
      employeeId?: number
      patients?: PatientInfo[]
    }>('/auth/login', { username: loginUsername, password })

    if (data) {
      userId.value = String(data.userId)
      username.value = data.username || loginUsername
      role.value = data.role || 'admin'
      realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
      employeeId.value = data.employeeId ?? null
      token.value = data.token || ''
      patients.value = data.patients || []
      // 默认选中本人
      const primaryPatient = patients.value.find(p => p.isPrimary === 1)
      currentPatientId.value = primaryPatient?.patientId || null
      if (data.token) {
        localStorage.setItem('access_token', data.token)
      }
      if (data.refreshToken) {
        localStorage.setItem('refresh_token', data.refreshToken)
      }
    }
    sessionChecked.value = true
  }

  function clearSession() {
    userId.value = ''
    username.value = ''
    role.value = 'admin'
    realName.value = ''
    employeeId.value = null
    token.value = ''
    patients.value = []
    currentPatientId.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    sessionChecked.value = true
  }

  async function logout() {
    try {
      await authApi.post('/auth/logout', undefined, { skipErrorMessage: true, skipAuthHandling: true })
    } finally {
      clearSession()
    }
  }

  // 切换当前患者
  function switchPatient(patientId: number) {
    const patient = patients.value.find(p => p.patientId === patientId)
    if (patient) {
      currentPatientId.value = patientId
    }
  }

  function setPatientBalance(patientId: number, accountBalance: number) {
    const patient = patients.value.find(p => p.patientId === patientId)
    if (patient) {
      patient.accountBalance = accountBalance
    }
  }

  function getToken() {
    if (!token.value) {
      token.value = localStorage.getItem('access_token') || ''
    }
    return token.value
  }

  return {
    userId,
    username,
    role,
    realName,
    employeeId,
    sessionChecked,
    isAuthenticated,
    token,
    patients,
    currentPatientId,
    currentPatient,
    loadSession,
    login,
    logout,
    clearSession,
    getToken,
    switchPatient,
    setPatientBalance,
  }
})