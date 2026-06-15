
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, DataBoard, FirstAidKit, MagicStick, Menu, Operation, Setting, Tickets, User } from '@element-plus/icons-vue'
import { appName } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { useEncounterStore } from '@/app/stores/encounter'
import { isPhysicianStepPath } from '@/modules/physician/composables/usePhysicianEncounterRoute'
import { physicianRoute } from '@/modules/physician/constants/visitState'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const encounterStore = useEncounterStore()
const iconMap = { Box, DataBoard, FirstAidKit, MagicStick, Menu, Operation, Setting, Tickets, User }

function isRouteAccessible(item: any) {
  if (item?.meta?.hidden) return false
  const roles = item?.meta?.roles
  return !roles?.length || roles.includes(authStore.role)
}

const menuRoutes = computed(() => {
  const root = router.options.routes.find((item) => item.path === '/')
  return (root?.children || [])
    .filter(isRouteAccessible)
    .map((item: any) => {
      const children = (item.children || []).filter(isRouteAccessible)
      return { ...item, children }
    })
    .filter((item: any) => !item.children?.length || item.children.length > 0)
})

function iconComponent(name?: string) {
  return name && name in iconMap ? iconMap[name as keyof typeof iconMap] : Menu
}

function childPath(parentPath: string, childPathSegment: string) {
  return `/${parentPath}/${childPathSegment}`
}

function isPhysicianStepDisabled(path: string) {
  return isPhysicianStepPath(path) && !encounterStore.hasEncounter
}

function handleMenuSelect(index: string) {
  if (index === route.path) return

  if (isPhysicianStepPath(index)) {
    if (!encounterStore.registerId) {
      ElMessage.warning('请先从「待诊接诊」选择患者并进入流程')
      return
    }
    void router.push(physicianRoute(index, encounterStore.registerId))
    return
  }

  void router.push(index)
}
</script>

<template>
  <aside class="app-sidebar">
    <RouterLink class="app-sidebar__brand" to="/dashboard">
      <span class="app-sidebar__logo">希</span>
      <span>
        <strong>{{ appName }}</strong>
        <small>Cloud Hospital</small>
      </span>
    </RouterLink>

    <el-menu
      class="app-sidebar__menu"
      :default-active="route.path"
      @select="handleMenuSelect"
    >
      <template v-for="item in menuRoutes" :key="item.path">
        <el-sub-menu v-if="item.children?.length" :index="`/${item.path}`">
          <template #title>
            <el-icon><component :is="iconComponent(item.meta?.icon)" /></el-icon>
            <span>{{ item.meta?.title }}</span>
          </template>

          <el-menu-item
            v-for="child in item.children"
            :key="child.path"
            :index="childPath(item.path, child.path)"
            :disabled="isPhysicianStepDisabled(childPath(item.path, child.path))"
            :class="{ 'app-sidebar__item--disabled': isPhysicianStepDisabled(childPath(item.path, child.path)) }"
          >
            <span>{{ child.meta?.title }}</span>
          </el-menu-item>
        </el-sub-menu>

        <el-menu-item v-else :index="`/${item.path}`">
          <el-icon><component :is="iconComponent(item.meta?.icon)" /></el-icon>
          <span>{{ item.meta?.title }}</span>
        </el-menu-item>
      </template>
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

.app-sidebar :deep(.el-menu-item.is-disabled),
.app-sidebar :deep(.app-sidebar__item--disabled) {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
