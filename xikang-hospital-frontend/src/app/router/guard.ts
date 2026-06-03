
import type { Router } from 'vue-router'
import { defaultRoutePath, loginRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'

export function setupRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()

    // 如果 session 还没检查过，等待 session 加载完成
    if (!authStore.sessionChecked) {
      await authStore.loadSession()
    }

    // 已登录用户访问登录页，根据角色重定向
    if (to.path === loginRoutePath && authStore.isAuthenticated) {
      const target = authStore.role === 'patient' ? '/patient/overview' : defaultRoutePath
      return target
    }

    // 患者角色访问非患者页面时，重定向到患者首页
    if (authStore.role === 'patient' && authStore.isAuthenticated) {
      // 这些是非患者专属页面
      const nonPatientPaths = ['/dashboard', '/registration', '/physician', '/medical-tech', '/pharmacy', '/admin']
      if (nonPatientPaths.some(path => to.path.startsWith(path))) {
        return '/patient/overview'
      }
    }

    // 需要认证但未登录，重定向到登录页
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { path: loginRoutePath, query: { redirect: to.fullPath } }
    }

    // 角色权限检查
    const roles = to.meta.roles
    if (roles?.length && !roles.includes(authStore.role)) {
      return '/403'
    }

    return true
  })
}
