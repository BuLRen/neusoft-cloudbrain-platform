
<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { appName, defaultRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { roleOptions, type UserRole } from '@/shared/types/role'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const selectedRole = ref<UserRole>('admin')

function login() {
  authStore.loginAs(selectedRole.value)
  router.push(String(route.query.redirect || defaultRoutePath))
}
</script>

<template>
  <main class="login-page">
    <section class="login-page__panel u-glass">
      <div class="login-page__intro">
        <span class="login-page__logo">希</span>
        <p>Frontend Framework</p>
        <h1>{{ appName }}</h1>
        <span>请选择一个开发角色进入框架。后续接入真实登录接口时，替换当前演示登录即可。</span>
      </div>

      <div class="login-page__form">
        <el-radio-group v-model="selectedRole" class="login-page__roles">
          <el-radio-button v-for="role in roleOptions" :key="role.value" :label="role.value">
            {{ role.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button size="large" type="primary" @click="login">进入系统</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--space-8);
}

.login-page__panel {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--space-8);
  width: min(980px, 100%);
  padding: var(--space-8);
  border-radius: var(--radius-2xl);
}

.login-page__logo {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin-block-end: var(--space-5);
  border-radius: 26px;
  color: #ffffff;
  background: var(--gradient-primary);
  box-shadow: var(--shadow-md);
  font-size: 32px;
  font-weight: 850;
}

.login-page__intro p {
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-page__intro h1 {
  margin-block: var(--space-3);
  font-size: clamp(42px, 5vw, 64px);
  line-height: 1;
  letter-spacing: -0.06em;
}

.login-page__intro span {
  display: block;
  max-width: 520px;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.login-page__form {
  display: grid;
  align-content: center;
  gap: var(--space-5);
}

.login-page__roles {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
