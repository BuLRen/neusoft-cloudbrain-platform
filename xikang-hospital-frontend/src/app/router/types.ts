
import 'vue-router'
import type { UserRole } from '@/shared/types/role'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    description?: string
    icon?: string
    roles?: UserRole[]
    requiresAuth?: boolean
    hidden?: boolean
    owner?: 'A' | 'B' | '共同'
  }
}
