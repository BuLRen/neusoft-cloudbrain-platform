
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import { isPhysicianStepPath } from '@/modules/physician/composables/usePhysicianEncounterRoute'

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()

    if (!authStore.sessionChecked) {
      await authStore.loadSession()
    }

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

    if (isPhysicianStepPath(to.path) && !to.query.registerId) {
      const encounterStore = useEncounterStore()
      if (encounterStore.registerId) {
        return {
          path: to.path,
          query: { ...to.query, registerId: String(encounterStore.registerId) },
        }
      }
      return '/physician/queue'
    }

    return true
  })
}
