import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/shared/types/role'
import { authApi } from '@/shared/api/modules/auth'

export interface PatientInfo {
  patientId: number
  realName: string
  gender: string
  relation: string
  isPrimary: number
  allergyHistory?: string
}

export const useAuthStore = defineStore('auth', () => {
  const userId = ref('')
  const username = ref('')  // 登录用户名
  const role = ref<UserRole>('admin')
  const realName = ref('')
  const sessionChecked = ref(false)
  const token = ref('')
  const patients = ref<PatientInfo[]>([])
  const currentPatientId = ref<number | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

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
      if (!token.value) {
        sessionChecked.value = true
        return
      }

      const data = await authApi.get<{
        userId: string
        username: string  // 新增
        role: UserRole
        realName: string
        patients?: PatientInfo[]
      }>('/auth/me', undefined, { skipErrorMessage: true })
      if (data) {
        userId.value = String(data.userId)
        username.value = data.username || ''
        role.value = data.role || 'admin'
        realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
        patients.value = data.patients || []
        // 默认选中本人
        const primaryPatient = patients.value.find(p => p.isPrimary === 1)
        currentPatientId.value = primaryPatient?.patientId || null
      }
    } catch {
      // API 调用失败时，保留 localStorage 中的 token，不清除
      // 这样下次刷新页面时可以再次尝试
      // 如果 token 无效，后端会返回 401，下次 API 调用时会自动清除
      console.warn('Session load failed, will retry on next request')
    } finally {
      sessionChecked.value = true
    }
  }

  async function login(loginUsername: string, password: string) {
    const data = await authApi.post<{
      userId: string
      username: string  // 新增
      role: UserRole
      token: string
      refreshToken: string
      realName: string
      patients?: PatientInfo[]
    }>('/auth/login', { username: loginUsername, password })

    if (data) {
      userId.value = String(data.userId)
      username.value = data.username || loginUsername  // 从后端获取，如果没有就用登录时传入的
      role.value = data.role || 'admin'
      realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
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

  async function logout() {
    try {
      await authApi.post('/auth/logout')
    } finally {
      userId.value = ''
      username.value = ''
      role.value = 'admin'
      realName.value = ''
      token.value = ''
      patients.value = []
      currentPatientId.value = null
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      sessionChecked.value = true
    }
  }

  // 切换当前患者
  function switchPatient(patientId: number) {
    const patient = patients.value.find(p => p.patientId === patientId)
    if (patient) {
      currentPatientId.value = patientId
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
    username,  // 新增：登录用户名
    role,
    realName,
    sessionChecked,
    isAuthenticated,
    token,
    patients,
    currentPatientId,
    currentPatient,
    loadSession,
    login,
    logout,
    getToken,
    switchPatient,
  }
})