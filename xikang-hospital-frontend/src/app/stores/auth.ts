
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/shared/types/role'
import { authApi } from '@/shared/api/modules/auth'

export const useAuthStore = defineStore('auth', () => {
  const userId = ref('')
  const role = ref<UserRole>('admin')
  const sessionChecked = ref(false)

  const isAuthenticated = computed(() => Boolean(userId.value))

  async function loadSession() {
    try {
      const session = await authApi.get<{ userId: string; role: UserRole }>('/auth/me')
      userId.value = session.userId
      role.value = session.role || 'admin'
    } catch {
      userId.value = ''
      role.value = 'admin'
    } finally {
      sessionChecked.value = true
    }
  }

  async function login(username: string, password: string) {
    const session = await authApi.post<{ userId: string; role: UserRole }>('/auth/login', { username, password })
    userId.value = session.userId
    role.value = session.role || 'admin'
    sessionChecked.value = true
  }

  async function logout() {
    try {
      await authApi.post<void>('/auth/logout')
    } finally {
      userId.value = ''
      role.value = 'admin'
      sessionChecked.value = true
    }
  }

  return { userId, role, sessionChecked, isAuthenticated, loadSession, login, logout }
})
