
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Box, DataBoard, FirstAidKit, MagicStick, Menu, Operation, Setting, Tickets, User } from '@element-plus/icons-vue'
import { appName } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const iconMap = { Box, DataBoard, FirstAidKit, MagicStick, Menu, Operation, Setting, Tickets, User }

// 患者角色时隐藏左侧菜单
const isPatient = computed(() => authStore.role === 'patient')

const menuRoutes = computed(() => {
  const root = router.options.routes.find((item) => item.path === '/')
  return (root?.children || []).filter((item) => {
    if (item.meta?.hidden) return false
    const roles = item.meta?.roles
    return !roles?.length || roles.includes(authStore.role)
  })
})

function iconComponent(name?: string) {
  return name && name in iconMap ? iconMap[name as keyof typeof iconMap] : Menu
}
</script>

<template>
  <!-- 患者角色时隐藏左侧菜单 -->
  <aside v-if="!isPatient" class="app-sidebar">
    <RouterLink class="app-sidebar__brand" to="/dashboard">
      <span class="app-sidebar__logo">希</span>
      <span>
        <strong>{{ appName }}</strong>
        <small>Cloud Hospital</small>
      </span>
    </RouterLink>

    <el-menu class="app-sidebar__menu" router :default-active="route.path">
      <el-menu-item v-for="item in menuRoutes" :key="item.path" :index="`/${item.path}`">
        <el-icon><component :is="iconComponent(item.meta?.icon)" /></el-icon>
        <span>{{ item.meta?.title }}</span>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<style scoped>
.app-sidebar {
  position: sticky;
  inset-block-start: var(--shell-gap);
  width: var(--sidebar-width);
  height: calc(100vh - var(--shell-gap) * 2);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  background: var(--color-sidebar);
  box-shadow: var(--shadow-md);
  backdrop-filter: var(--blur-glass);
}

.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border-radius: var(--radius-lg);
}

.app-sidebar__logo {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 15px;
  color: #ffffff;
  background: var(--gradient-primary);
  box-shadow: 0 16px 32px rgba(31, 140, 255, 0.24);
  font-size: 19px;
  font-weight: 850;
}

.app-sidebar__brand strong,
.app-sidebar__brand small {
  display: block;
}

.app-sidebar__brand strong {
  font-size: 16px;
  letter-spacing: -0.03em;
}

.app-sidebar__brand small {
  margin-block-start: 2px;
  color: var(--color-text-soft);
  font-size: 12px;
}

.app-sidebar__menu {
  margin-block-start: var(--space-4);
}

.app-sidebar :deep(.el-menu),
.app-sidebar :deep(.el-menu-item),
.app-sidebar :deep(.el-sub-menu__title) {
  background-color: transparent !important;
}

.app-sidebar :deep(.el-menu-item:hover),
.app-sidebar :deep(.el-sub-menu__title:hover) {
  background-color: var(--color-menu-hover) !important;
}

.app-sidebar :deep(.el-menu-item.is-active) {
  background-color: var(--color-primary-soft) !important;
}
</style>
