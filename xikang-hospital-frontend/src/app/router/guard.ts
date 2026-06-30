
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import { PHYSICIAN_QUEUE } from '@/modules/physician/constants/visitState'

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()

    if (!authStore.sessionChecked) {
      await authStore.loadSession()
    }

    if (to.path === loginRoutePath) {
      if (authStore.isAuthenticated) {
        return authStore.role === 'patient' ? '/patient/overview' : defaultRoutePath
      }
      if (to.query.redirect) {
        return { path: loginRoutePath, query: {} }
      }
      return true
    }

    if (authStore.role === 'patient' && authStore.isAuthenticated) {
      const nonPatientPaths = ['/dashboard', '/registration', '/physician', '/medical-tech', '/pharmacy', '/admin']
      if (nonPatientPaths.some(path => to.path.startsWith(path))) {
        return '/patient/overview'
      }
    }

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      if (to.path !== loginRoutePath) {
        return { path: loginRoutePath, query: {} }
      }
    }

    const roles = to.meta.roles
    if (roles?.length && !roles.includes(authStore.role)) {
      return '/403'
    }

    if (to.meta.requiresEncounter && !to.query.registerId) {
      const encounterStore = useEncounterStore()
      if (encounterStore.registerId) {
        return {
          path: to.path,
          query: { ...to.query, registerId: String(encounterStore.registerId) },
        }
      }
      if (!to.query.selectFor) {
        return {
          path: to.path,
          query: { ...to.query, selectFor: to.path },
        }
      }
    }

    if (to.path === PHYSICIAN_QUEUE && to.query.needEncounter === '1' && !to.query.selectFor) {
      return { path: PHYSICIAN_QUEUE, query: {} }
    }

    return true
  })
}
