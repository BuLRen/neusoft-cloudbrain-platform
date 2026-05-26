
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'

export function setupRouterGuard(router: Router) {
  router.beforeEach((to) => {
    const authStore = useAuthStore()

    if (to.path === loginRoutePath && authStore.isAuthenticated) {
      return defaultRoutePath
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { path: loginRoutePath, query: { redirect: to.fullPath } }
    }

    const roles = to.meta.roles
    if (roles?.length && !roles.includes(authStore.role)) {
      return '/403'
    }

    return true
  })
}
