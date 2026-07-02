<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChatDotRound,
  Document,
  Key,
  Lock,
  MagicStick,
  Monitor,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/app/stores/auth'
import { authApi } from '@/shared/api/modules/auth'
import { ElMessage, ElCheckbox } from 'element-plus'
import {
  getRememberedUsername,
  isRememberMeEnabled,
} from '@/shared/auth/tokenStorage'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const platformTitle = '熙康云脑诊疗平台'
const platformSubtitle = 'AI 辅助诊疗 · 智慧医院信息系统'

const features = [
  { icon: ChatDotRound, label: '智能预问诊' },
  { icon: MagicStick, label: 'AI 辅助诊断' },
  { icon: Document, label: '电子病历管理' },
  { icon: Monitor, label: '医学影像协同' },
] as const

const submitting = ref(false)
const rememberMe = ref(false)
const isLogin = ref(true)
const captchaId = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const form = reactive({
  username: '',
  password: '',
  captcha: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  idCard: '',
})

onMounted(() => {
  const expiredMessage = sessionStorage.getItem('session_expired_message')
  if (expiredMessage) {
    sessionStorage.removeItem('session_expired_message')
    ElMessage.warning(expiredMessage)
  }
  rememberMe.value = isRememberMeEnabled()
  form.username = getRememberedUsername()
  loadCaptcha()
})

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await authApi.getCaptcha()
    if (!data) return
    captchaId.value = data.captchaId
    captchaImage.value = data.imageBase64.startsWith('data:')
      ? data.imageBase64
      : `data:image/png;base64,${data.imageBase64}`
    form.captcha = ''
  } catch {
    captchaId.value = ''
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (!form.captcha.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  if (!captchaId.value) {
    ElMessage.warning('验证码加载失败，请点击图片刷新')
    return
  }

  submitting.value = true
  try {
    await authStore.login(form.username, form.password, captchaId.value, form.captcha, rememberMe.value)
    const rawRedirect = route.query.redirect
    const redirect = typeof rawRedirect === 'string' && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//') ? rawRedirect : ''
    if (redirect) {
      router.push(redirect)
    } else if (authStore.role === 'patient') {
      router.push('/patient/overview')
    } else if (authStore.role === 'followup') {
      router.push('/follow-up/outcome')
    } else {
      router.push('/dashboard')
    }
  } catch {
    await loadCaptcha()
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
      userType: 6,
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
  form.captcha = ''
  form.confirmPassword = ''
  form.realName = ''
  form.phone = ''
  form.idCard = ''
  if (isLogin.value) {
    loadCaptcha()
  }
}

function handleForgotPassword() {
  ElMessage.info('请联系系统管理员重置密码')
}
</script>

<template>
  <main class="login-page">
    <div class="login-page__backdrop" aria-hidden="true" />

    <section class="login-page__brand">
      <div class="login-page__brand-inner">
        <h1 class="login-page__title">{{ platformTitle }}</h1>
        <p class="login-page__subtitle">{{ platformSubtitle }}</p>

        <ul class="login-page__features">
          <li v-for="feature in features" :key="feature.label" class="login-page__feature">
            <span class="login-page__feature-icon">
              <el-icon :size="22">
                <component :is="feature.icon" />
              </el-icon>
            </span>
            <span>{{ feature.label }}</span>
          </li>
        </ul>
      </div>
    </section>

    <section class="login-page__panel">
      <div class="login-card">
        <header class="login-card__header">
          <div class="login-card__logo" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M36 20c0-6.627-5.373-12-12-12S12 13.373 12 20c0 4.418 2.254 8.31 5.68 10.58C14.92 33.04 12 36.96 12 41.5h24c0-4.54-2.92-8.46-5.68-10.92C33.746 28.31 36 24.418 36 20Z"
                fill="currentColor"
              />
              <rect x="22" y="26" width="4" height="12" rx="1" fill="#fff" />
              <rect x="18" y="30" width="12" height="4" rx="1" fill="#fff" />
            </svg>
          </div>
          <h2 class="login-card__title">{{ isLogin ? '欢迎登录' : '欢迎注册' }}</h2>
          <p class="login-card__desc">
            {{ isLogin ? '请使用医院系统账号进入工作台' : '创建新账号，开启智慧就医之旅' }}
          </p>
        </header>

        <el-form
          v-if="isLogin"
          class="login-card__form"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              autocomplete="username"
              placeholder="请输入用户名"
              size="large"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              size="large"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="验证码">
            <div class="login-card__captcha-row">
              <el-input
                v-model="form.captcha"
                placeholder="请输入验证码"
                size="large"
              >
                <template #prefix>
                  <el-icon><Key /></el-icon>
                </template>
              </el-input>
              <button
                type="button"
                class="login-card__captcha-image"
                :class="{ 'is-loading': captchaLoading }"
                title="点击刷新验证码"
                :disabled="captchaLoading"
                @click="loadCaptcha"
              >
                <img v-if="captchaImage" :src="captchaImage" alt="验证码">
                <span v-else>加载中</span>
              </button>
            </div>
          </el-form-item>

          <div class="login-card__options">
            <ElCheckbox v-model="rememberMe">记住我</ElCheckbox>
            <button type="button" class="login-card__link" @click="handleForgotPassword">
              忘记密码?
            </button>
          </div>

          <el-button
            class="login-card__submit"
            size="large"
            type="primary"
            :loading="submitting"
            native-type="submit"
            @click="handleLogin"
          >
            登录系统
          </el-button>

          <div class="login-card__switch">
            <span>还没有账号？</span>
            <button type="button" class="login-card__link" @click="switchMode">立即注册</button>
          </div>
        </el-form>

        <el-form
          v-else
          class="login-card__form login-card__form--register"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              autocomplete="username"
              placeholder="请输入用户名（至少3位）"
              size="large"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" placeholder="请输入真实姓名（选填）" size="large" />
          </el-form-item>

          <el-form-item label="身份证号" required>
            <el-input
              v-model="form.idCard"
              placeholder="请输入18位身份证号"
              maxlength="18"
              size="large"
            />
          </el-form-item>

          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="请输入手机号（选填）" size="large" />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请输入密码（至少6位）"
              size="large"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="确认密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请再次输入密码"
              size="large"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-button
            class="login-card__submit"
            size="large"
            type="primary"
            :loading="submitting"
            native-type="submit"
            @click="handleRegister"
          >
            注册账号
          </el-button>

          <div class="login-card__switch">
            <span>已有账号？</span>
            <button type="button" class="login-card__link" @click="switchMode">立即登录</button>
          </div>
        </el-form>

        <footer class="login-card__footer">© 2026 {{ platformTitle }}</footer>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  --login-primary: #0052d9;
  --login-primary-hover: #003cab;
  --login-card-width: 420px;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.95fr);
  min-height: 100vh;
  overflow: hidden;
  background: #e8f2ff;
}

.login-page__backdrop {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(232, 242, 255, 0.92) 0%, rgba(232, 242, 255, 0.55) 48%, rgba(232, 242, 255, 0.18) 72%, rgba(232, 242, 255, 0.08) 100%),
    url('/images/login-bg.png') center / cover no-repeat;
  z-index: 0;
}

.login-page__brand,
.login-page__panel {
  position: relative;
  z-index: 1;
}

.login-page__brand {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: clamp(32px, 6vw, 72px) clamp(24px, 4vw, 48px) clamp(32px, 6vw, 72px) clamp(32px, 5vw, 80px);
}

.login-page__brand-inner {
  width: min(720px, 100%);
}

.login-page__title {
  margin: 0;
  color: #0a2a5e;
  font-size: clamp(32px, 4.2vw, 52px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: 0.02em;
}

.login-page__subtitle {
  margin: 16px 0 0;
  color: #3d5f8a;
  font-size: clamp(15px, 1.6vw, 18px);
  line-height: 1.6;
}

.login-page__features {
  display: flex;
  flex-wrap: nowrap;
  gap: 12px;
  margin: 36px 0 0;
  padding: 0;
  list-style: none;
}

.login-page__feature {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 8px 8px;
  border-radius: 999px;
  color: #1a3f6e;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8px 24px rgba(31, 73, 125, 0.08);
  backdrop-filter: blur(12px);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.login-page__feature-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: var(--login-primary);
  background: rgba(0, 82, 217, 0.1);
}

.login-page__panel {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: clamp(24px, 4vw, 48px) clamp(32px, 5vw, 72px) clamp(24px, 4vw, 48px) clamp(12px, 2vw, 24px);
}

.login-card {
  width: min(var(--login-card-width), 100%);
  padding: 36px 36px 28px;
  border-radius: 16px;
  background: #fff;
  box-shadow:
    0 24px 64px rgba(16, 48, 96, 0.12),
    0 4px 16px rgba(16, 48, 96, 0.06);
}

.login-card__header {
  text-align: center;
}

.login-card__logo {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 16px;
  color: var(--login-primary);
  background: linear-gradient(135deg, rgba(0, 82, 217, 0.12), rgba(47, 216, 196, 0.12));
}

.login-card__logo svg {
  width: 34px;
  height: 34px;
}

.login-card__title {
  margin: 0;
  color: #0a2a5e;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
}

.login-card__desc {
  margin: 8px 0 0;
  color: #8b9cb0;
  font-size: 14px;
  line-height: 1.5;
}

.login-card__form {
  margin-top: 28px;
}

.login-card__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-card__form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #4a6078;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.login-card__form :deep(.el-input__wrapper) {
  padding-inline: 14px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #d8e3f0 inset;
  transition: box-shadow var(--duration-fast) var(--ease-standard);
}

.login-card__form :deep(.el-input__wrapper:hover),
.login-card__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--login-primary) inset;
}

.login-card__form :deep(.el-input__prefix) {
  color: #9aadc2;
}

.login-card__captcha-row {
  display: grid;
  grid-template-columns: 1fr 112px;
  gap: 12px;
  width: 100%;
}

.login-card__captcha-image {
  display: grid;
  place-items: center;
  width: 112px;
  min-height: 40px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: #f4f8fd;
  box-shadow: 0 0 0 1px #d8e3f0 inset;
  overflow: hidden;
  cursor: pointer;
}

.login-card__captcha-image.is-loading {
  cursor: wait;
}

.login-card__captcha-image:disabled {
  opacity: 0.72;
}

.login-card__captcha-image img {
  display: block;
  width: 100%;
  height: 40px;
  object-fit: cover;
}

.login-card__captcha-image span {
  color: #8b9cb0;
  font-size: 12px;
}

.login-card__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 20px;
}

.login-card__options :deep(.el-checkbox__label) {
  color: #5f7288;
  font-size: 14px;
}

.login-card__submit {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 10px;
  background: var(--login-primary);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.login-card__submit:hover,
.login-card__submit:focus {
  background: var(--login-primary-hover);
}

.login-card__switch {
  margin-top: 18px;
  text-align: center;
  color: #8b9cb0;
  font-size: 14px;
}

.login-card__link {
  padding: 0;
  border: none;
  background: none;
  color: var(--login-primary);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.login-card__link:hover {
  text-decoration: underline;
}

.login-card__footer {
  margin-top: 24px;
  text-align: center;
  color: #b0bec9;
  font-size: 12px;
}

.login-card__form--register {
  max-height: min(62vh, 560px);
  overflow: auto;
  padding-right: 4px;
}

@media (max-width: 1024px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-page__backdrop {
    background:
      linear-gradient(180deg, rgba(232, 242, 255, 0.94) 0%, rgba(232, 242, 255, 0.82) 42%, rgba(232, 242, 255, 0.72) 100%),
      url('/images/login-bg.png') center / cover no-repeat;
  }

  .login-page__brand {
    align-items: flex-end;
    justify-content: flex-start;
    padding-block-end: 0;
  }

  .login-page__features {
    flex-wrap: wrap;
    margin-top: 24px;
  }

  .login-page__panel {
    justify-content: center;
    padding-block-start: 20px;
  }
}

@media (max-width: 520px) {
  .login-card {
    padding: 28px 22px 22px;
    border-radius: 14px;
  }

  .login-page__features {
    gap: 10px;
  }

  .login-page__feature {
    padding: 8px 12px 8px 8px;
    font-size: 13px;
  }

  .login-card__captcha-row {
    grid-template-columns: 1fr 96px;
  }
}
</style>
