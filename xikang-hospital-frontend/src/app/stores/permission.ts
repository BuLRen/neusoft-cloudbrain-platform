
import { computed } from 'vue'
import { defineStore } from 'pinia'
import { routes } from '@/app/router/routes'
import { useAuthStore } from './auth'

export const usePermissionStore = defineStore('permission', () => {
  const authStore = useAuthStore()
  const accessibleRoutes = computed(() => {
    return routes.filter((route) => {
      const roles = route.meta?.roles
      return !roles?.length || roles.includes(authStore.role)
    })
  })

  return { accessibleRoutes }
})
