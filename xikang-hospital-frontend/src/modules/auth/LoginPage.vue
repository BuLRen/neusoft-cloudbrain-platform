
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { appName, defaultRoutePath } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const form = reactive({
  username: 'admin',
  password: 'admin',
})

async function login() {
  submitting.value = true
  try {
    await authStore.login(form.username, form.password)
    router.push(String(route.query.redirect || defaultRoutePath))
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-page__panel u-glass">
      <div class="login-page__intro">
        <span class="login-page__logo">希</span>
        <p>Frontend Framework</p>
        <h1>{{ appName }}</h1>
        <span>请输入账号密码登录（当前为开发模式：后端尚未校验数据库）。</span>
      </div>

      <div class="login-page__form">
        <el-form @submit.prevent="login">
          <el-form-item label="用户名">
            <el-input v-model="form.username" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-button size="large" type="primary" :loading="submitting" @click="login">登录</el-button>
        </el-form>
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
</style>
