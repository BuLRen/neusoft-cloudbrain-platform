import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import { loginRoutePath } from '@/shared/constants/app'
import { canRefreshSession, refreshAccessToken } from '@/shared/api/authRefresh'
import { getAccessToken } from '@/shared/auth/tokenStorage'
import type { ApiResult, RequestOptions } from './result'

const request = axios.create({
  baseURL: '/api',
  // 默认 15s 不够 AI 导诊（Chat + RAG 检索 + 解析常需 25-60s）
  // 提到 90s 让绝大多数正常请求能跑完；网络异常时仍会触发 axios 超时抛错，走前端 fallback。
  timeout: 90000,
  withCredentials: true,
})

const sessionExpiredMessageKey = 'session_expired_message'
const sessionExpiredMessage = '登录已过期，请重新登录'
const skipAuthHandlingPaths = ['/auth/login', '/auth/register', '/auth/logout', '/auth/captcha']
// 标记：401 触发的跳转只执行一次，避免多个并发请求反复 push
let sessionExpiredRedirecting = false

function isOnLoginPage() {
  if (typeof window === 'undefined') return false
  return window.location.pathname === loginRoutePath
}

function shouldSkipAuthHandling(config?: (AxiosRequestConfig & RequestOptions) | null) {
  const url = config?.url || ''
  return Boolean(config?.skipAuthHandling || skipAuthHandlingPaths.some(path => url.startsWith(path)))
}

function forceRedirectToLogin() {
  if (sessionExpiredRedirecting) {
    return
  }
  sessionExpiredRedirecting = true
  if (typeof window !== 'undefined') {
    sessionStorage.setItem(sessionExpiredMessageKey, sessionExpiredMessage)
    if (!isOnLoginPage()) {
      window.location.replace(loginRoutePath)
    }
  }
}

// 请求拦截器：添加 Authorization header
request.interceptors.request.use(
  (config) => {
    const token = getAccessToken()
    if (token && !config.headers.has('Authorization')) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  },
  (error) => Promise.reject(error),
)

async function refreshSessionOnce() {
  if (!canRefreshSession()) {
    throw new Error(sessionExpiredMessage)
  }
  await refreshAccessToken()
}

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    if (body && typeof body.code === 'number' && body.code !== 200) {
      const config = response.config as AxiosRequestConfig & RequestOptions
      const authStore = useAuthStore()
      if (body.code === 401) {
        if (!shouldSkipAuthHandling(config)) {
          authStore.clearSession()
          forceRedirectToLogin()
        }
        return Promise.reject(new Error(body.message || sessionExpiredMessage))
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResult>) => {
    const authStore = useAuthStore()
    const originalRequest = error.config
    const status = error.response?.status
    const bodyCode = error.response?.data?.code
    const isAuthFailure = status === 401 || bodyCode === 401

    if (isAuthFailure && shouldSkipAuthHandling(originalRequest as AxiosRequestConfig & RequestOptions)) {
      return Promise.reject(new Error(error.response?.data?.message || sessionExpiredMessage))
    }

    if (isAuthFailure && originalRequest && !(originalRequest as any).__isRetryRequest) {
      ;(originalRequest as any).__isRetryRequest = true
      try {
        await refreshSessionOnce()
        return request.request(originalRequest)
      } catch {
        authStore.clearSession()
        forceRedirectToLogin()
        return Promise.reject(new Error(sessionExpiredMessage))
      }
    }

    if (isAuthFailure) {
      authStore.clearSession()
      forceRedirectToLogin()
      return Promise.reject(new Error(sessionExpiredMessage))
    }

    const message = error.response?.data?.message || error.message || '网络请求异常'
    return Promise.reject(new Error(message))
  },
)

export async function http<T>(config: AxiosRequestConfig & RequestOptions): Promise<T> {
  try {
    const response = await request.request<ApiResult<T>>(config)
    return response.data.data
  } catch (error) {
    const message = error instanceof Error ? error.message : '请求失败'
    if (!config.skipErrorMessage && !(sessionExpiredRedirecting && message === sessionExpiredMessage)) {
      ElMessage.error(message)
    }
    throw error
  }
}

export { request }