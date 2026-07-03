
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
    group?: 'attending' | 'exam' | 'follow-up'
    step?: number
    owner?: 'A' | 'B' | '共同'
    requiresEncounter?: boolean
    fullscreen?: boolean
  }
}
