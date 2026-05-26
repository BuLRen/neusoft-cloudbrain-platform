
import { createRouter, createWebHistory } from 'vue-router'
import './types'
import { routes } from './routes'
import { setupRouterGuard } from './guard'

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

setupRouterGuard(router)
