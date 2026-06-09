
<script setup lang="ts">
import { Sunny, Moon, SwitchButton } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/app/stores/app'
import { useAuthStore } from '@/app/stores/auth'
import { useUserStore } from '@/app/stores/user'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const userStore = useUserStore()

async function logout() {
  try {
    await authStore.logout()
  } finally {
    // 主动登出：不携带任何 redirect，避免下次被自动送回原页面
    router.push({ path: '/login', query: {} })
  }
}
</script>

<template>
  <header class="app-header">
    <div>
      <p class="app-header__eyebrow">Frontend Framework</p>
      <h2>门诊全流程协作平台</h2>
    </div>

    <div class="app-header__actions">
      <el-button round @click="appStore.toggleTheme">
        <el-icon><component :is="appStore.theme === 'light' ? Moon : Sunny" /></el-icon>
        {{ appStore.themeLabel }}模式
      </el-button>
      <div class="app-header__user">
        <strong>{{ userStore.profile.name }}</strong>
        <span>{{ userStore.profile.description }}</span>
      </div>
      <el-button circle :icon="SwitchButton" @click="logout" />
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--header-height);
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  backdrop-filter: var(--blur-glass);
}

.app-header__eyebrow {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.app-header h2 {
  margin-block-start: 2px;
  font-size: 17px;
  letter-spacing: -0.03em;
}

.app-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.app-header__user {
  min-width: 136px;
  padding: 6px var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-user-chip);
}

.app-header__user strong,
.app-header__user span {
  display: block;
}

.app-header__user span {
  margin-block-start: 2px;
  color: var(--color-text-soft);
  font-size: 12px;
}
</style>
