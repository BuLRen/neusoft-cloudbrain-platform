
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/shared/types/role'

const tokenKey = 'xikang-session-token'
const roleKey = 'xikang-session-role'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(tokenKey) || '')
  const role = ref<UserRole>((sessionStorage.getItem(roleKey) as UserRole) || 'admin')
  const isAuthenticated = computed(() => Boolean(token.value))

  function loginAs(nextRole: UserRole) {
    role.value = nextRole
    token.value = `dev-${nextRole}-token`
    sessionStorage.setItem(tokenKey, token.value)
    sessionStorage.setItem(roleKey, role.value)
  }

  function logout() {
    token.value = ''
    sessionStorage.removeItem(tokenKey)
    sessionStorage.removeItem(roleKey)
  }

  return { token, role, isAuthenticated, loginAs, logout }
})
