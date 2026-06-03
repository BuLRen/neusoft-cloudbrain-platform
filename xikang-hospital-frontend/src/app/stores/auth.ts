import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/shared/types/role'
import { authApi } from '@/shared/api/modules/auth'

export const useAuthStore = defineStore('auth', () => {
  const userId = ref('')
  const role = ref<UserRole>('admin')
  const realName = ref('')
  const sessionChecked = ref(false)
  const token = ref('')

  const isAuthenticated = computed(() => Boolean(token.value))

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

      const data = await authApi.get<{ userId: string; role: UserRole; realName: string }>('/auth/me', undefined, { skipErrorMessage: true })
      if (data) {
        userId.value = String(data.userId)
        role.value = data.role || 'admin'
        realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
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

  async function login(username: string, password: string) {
    const data = await authApi.post<{
      userId: string
      role: UserRole
      token: string
      refreshToken: string
      realName: string
    }>('/auth/login', { username, password })

    if (data) {
      userId.value = String(data.userId)
      role.value = data.role || 'admin'
      realName.value = data.realName || (data.role === 'patient' ? '患者' : '未知用户')
      token.value = data.token || ''
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
      role.value = 'admin'
      realName.value = ''
      token.value = ''
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      sessionChecked.value = true
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
    role,
    realName,
    sessionChecked,
    isAuthenticated,
    token,
    loadSession,
    login,
    logout,
    getToken,
  }
})