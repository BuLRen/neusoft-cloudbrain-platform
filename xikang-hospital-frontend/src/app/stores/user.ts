
import { computed } from 'vue'
import { defineStore } from 'pinia'
import { roleOptions } from '@/shared/types/role'
import { useAuthStore } from './auth'

export const useUserStore = defineStore('user', () => {
  const authStore = useAuthStore()
  const profile = computed(() => {
    const role = roleOptions.find((item) => item.value === authStore.role)
    return {
      name: role?.label || '开发用户',
      roleLabel: role?.label || '管理员',
      description: role?.description || '系统管理',
    }
  })

  return { profile }
})
