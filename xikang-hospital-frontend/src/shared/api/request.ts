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
  console.warn('[DEBUG forceRedirectToLogin] 被调用！当前 URL =', window.location.href)
  console.trace('[DEBUG forceRedirectToLogin] 调用栈')
  if (sessionExpiredRedirecting) {
    console.warn('[DEBUG forceRedirectToLogin] sessionExpiredRedirecting 已为 true，跳过')
    return
  }
  sessionExpiredRedirecting = true
  if (typeof window !== 'undefined') {
    sessionStorage.setItem(sessionExpiredMessageKey, sessionExpiredMessage)
    if (!isOnLoginPage()) {
      // 直接走 location 替换，避免和 router 守卫竞争
      console.warn('[DEBUG forceRedirectToLogin] 即将执行 window.location.replace(/login)')
      window.location.replace(loginRoutePath)
    }
  }
}

// 请求拦截器：添加 Authorization header
request.interceptors.request.use(
  (config) => {
    const token = getAccessToken()
    if (token) {
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
      if (body.code === 401) {
        // 将 body.code=401 转为标准 401 错误，走下方统一的 refresh 逻辑
        const authError = Object.assign(new Error(body.message || sessionExpiredMessage), {
          response: { ...response, status: 401, data: body },
          config: response.config,
          isAxiosError: true,
        }) as AxiosError<ApiResult>
        return Promise.reject(authError)
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
    console.warn('[DEBUG response.error]', originalRequest?.url, 'status=', status, 'bodyCode=', bodyCode, 'isAuthFailure=', isAuthFailure)

    if (isAuthFailure && shouldSkipAuthHandling(originalRequest as AxiosRequestConfig & RequestOptions)) {
      console.log('[DEBUG] skipAuthHandling 命中，不跳登录，直接 reject')
      return Promise.reject(new Error(error.response?.data?.message || sessionExpiredMessage))
    }

    if (isAuthFailure && originalRequest && !(originalRequest as any).__isRetryRequest) {
      console.warn('[DEBUG] 401 触发 token refresh...')
      ;(originalRequest as any).__isRetryRequest = true
      try {
        await refreshSessionOnce()
        console.log('[DEBUG] refresh 成功，重试原请求')
        if (originalRequest.headers) {
          const nextToken = getAccessToken()
          if (nextToken) {
            originalRequest.headers.set('Authorization', `Bearer ${nextToken}`)
          }
        }
        return request.request(originalRequest)
      } catch (refreshErr) {
        console.warn('[DEBUG] refresh 失败，触发跳登录', refreshErr)
        authStore.clearSession()
        forceRedirectToLogin()
        return Promise.reject(new Error(sessionExpiredMessage))
      }
    }

    if (isAuthFailure) {
      console.warn('[DEBUG] 401 已重试过仍失败，触发跳登录')
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