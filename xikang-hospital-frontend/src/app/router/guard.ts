
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import { isPhysicianStepPath } from '@/modules/physician/composables/usePhysicianEncounterRoute'

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()

    // 如果 session 还没检查过，等待 session 加载完成
    if (!authStore.sessionChecked) {
      await authStore.loadSession()
    }

    // 访问登录页时，绝不带任何 redirect 参数；已登录直接送回首页
    if (to.path === loginRoutePath) {
      if (authStore.isAuthenticated) {
        return authStore.role === 'patient' ? '/patient/overview' : defaultRoutePath
      }
      // 强制清掉任何可能由旧逻辑塞进来的 redirect，避免登出后又被送回
      if (to.query.redirect) {
        return { path: loginRoutePath, query: {} }
      }
      return true
    }

    // 患者角色访问非患者页面时，重定向到患者首页
    if (authStore.role === 'patient' && authStore.isAuthenticated) {
      const nonPatientPaths = ['/dashboard', '/registration', '/physician', '/medical-tech', '/pharmacy', '/admin']
      if (nonPatientPaths.some(path => to.path.startsWith(path))) {
        return '/patient/overview'
      }
    }

    // 需要认证但未登录，重定向到登录页（不携带 redirect，避免登出后被送回上一个失败页）
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      if (to.path !== loginRoutePath) {
        return { path: loginRoutePath, query: {} }
      }
    }

    // 角色权限检查
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
      return { path: '/physician/queue', query: { needEncounter: '1' } }
    }

    return true
  })
}
