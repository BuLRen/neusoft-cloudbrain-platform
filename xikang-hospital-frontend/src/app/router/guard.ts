
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import { PHYSICIAN_QUEUE } from '@/modules/physician/constants/visitState'

// 公共页面：无需登录，也不触发 session 加载（避免持有过期 token 时被 /auth/me 401 强制踢到登录）
// 大屏 / 报到机这类公共设备页面，不应该受任何登录状态影响
const PUBLIC_PATHS = ['/calling-board', '/test-checkin']
function isPublicPath(path: string) {
  return PUBLIC_PATHS.some(p => path === p || path.startsWith(p + '/'))
}

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()
    console.log('[DEBUG guard] 进入守卫: to.path =', to.path, 'sessionChecked =', authStore.sessionChecked, 'hasToken =', Boolean(localStorage.getItem('access_token')))

    // 公共页面（候诊大屏 / 报到机）：完全跳过 session 加载与登录跳转
    if (isPublicPath(to.path)) {
      console.log('[DEBUG guard] 命中公共页面，直接放行: ', to.path)
      return true
    }

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
