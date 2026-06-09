<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { appName } from '@/shared/constants/app'
import { useAuthStore } from '@/app/stores/auth'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const isLogin = ref(true) // Toggle between login and register
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  idCard: '',
})

async function handleLogin() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }

  submitting.value = true
  try {
    await authStore.login(form.username, form.password)
    // 只接受我们自己守卫塞进来的、同源路径 redirect；任何外部或空值都走默认首页
    const rawRedirect = route.query.redirect
    const redirect = typeof rawRedirect === 'string' && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//') ? rawRedirect : ''
    if (redirect) {
      router.push(redirect)
    } else if (authStore.role === 'patient') {
      router.push('/patient/overview')
    } else {
      router.push('/dashboard')
    }
  } catch {
    // Error message is handled by request interceptor
  } finally {
    submitting.value = false
  }
}

async function handleRegister() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (form.username.length < 3) {
    ElMessage.warning('用户名至少3个字符')
    return
  }
  if (!form.idCard.trim()) {
    ElMessage.warning('请输入身份证号')
    return
  }
  if (form.idCard.length !== 18) {
    ElMessage.warning('身份证号必须为18位')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (form.password.length < 6) {
    ElMessage.warning('密码至少6个字符')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }

  submitting.value = true
  try {
    const { authApi } = await import('@/shared/api/modules/auth')
    await authApi.post('/auth/register', {
      username: form.username,
      password: form.password,
      realName: form.realName || form.username,
      phone: form.phone,
      idCard: form.idCard,
      userType: 6, // Patient by default
    })
    ElMessage.success('注册成功，请登录')
    isLogin.value = true
    form.password = ''
    form.confirmPassword = ''
    form.idCard = ''
  } catch {
    // Error message is handled by request interceptor
  } finally {
    submitting.value = false
  }
}

function switchMode() {
  isLogin.value = !isLogin.value
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.realName = ''
  form.phone = ''
  form.idCard = ''
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-page__panel u-glass">
      <div class="auth-page__intro">
        <span class="auth-page__logo">希</span>
        <p>Frontend Framework</p>
        <h1>{{ appName }}</h1>
        <span>{{ isLogin ? '请输入账号密码登录系统' : '创建新账号开始就医之旅' }}</span>
      </div>

      <div class="auth-page__form">
        <!-- Login Form -->
        <el-form v-if="isLogin" @submit.prevent="handleLogin">
          <el-form-item label="用户名">
            <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
          </el-form-item>
          <el-button size="large" type="primary" :loading="submitting" native-type="submit" @click="handleLogin">
            登录
          </el-button>
          <div class="auth-page__switch">
            <span>还没有账号？</span>
            <a @click="switchMode">立即注册</a>
          </div>
        </el-form>

        <!-- Register Form -->
        <el-form v-else @submit.prevent="handleRegister">
          <el-form-item label="用户名">
            <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名（至少3位）" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" placeholder="请输入真实姓名（选填）" />
          </el-form-item>
          <el-form-item label="身份证号" required>
            <el-input v-model="form.idCard" placeholder="请输入18位身份证号" maxlength="18" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="请输入手机号（选填）" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="请输入密码（至少6位）" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" placeholder="请再次输入密码" />
          </el-form-item>
          <el-button size="large" type="primary" :loading="submitting" native-type="submit" @click="handleRegister">
            注册
          </el-button>
          <div class="auth-page__switch">
            <span>已有账号？</span>
            <a @click="switchMode">立即登录</a>
          </div>
        </el-form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--space-8);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.auth-page__panel {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--space-8);
  width: min(980px, 100%);
  padding: var(--space-8);
  border-radius: var(--radius-2xl);
  background: rgba(255, 255, 255, 0.95);
}

.auth-page__logo {
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

.auth-page__intro p {
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.auth-page__intro h1 {
  margin-block: var(--space-3);
  font-size: clamp(42px, 5vw, 64px);
  line-height: 1;
  letter-spacing: -0.06em;
}

.auth-page__intro span {
  display: block;
  max-width: 520px;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.auth-page__form {
  display: grid;
  align-content: center;
  gap: var(--space-5);
}

.auth-page__switch {
  text-align: center;
  margin-top: var(--space-4);
  color: var(--color-text-muted);
}

.auth-page__switch a {
  color: var(--color-primary);
  cursor: pointer;
  margin-left: var(--space-2);
}

.auth-page__switch a:hover {
  text-decoration: underline;
}
</style>