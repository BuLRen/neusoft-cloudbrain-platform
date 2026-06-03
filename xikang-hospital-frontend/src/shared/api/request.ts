import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import type { ApiResult, RequestOptions } from './result'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

let refreshPromise: Promise<void> | null = null

// 请求拦截器：添加 Authorization header
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token') || ''
    if (token && !config.headers.has('Authorization')) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  },
  (error) => Promise.reject(error),
)

async function refreshSessionOnce() {
  if (!refreshPromise) {
    refreshPromise = refreshClient.post('/auth/refresh').then(() => undefined)
  }
  try {
    await refreshPromise
  } finally {
    refreshPromise = null
  }
}

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResult>) => {
    const authStore = useAuthStore()
    const originalRequest = error.config
    const status = error.response?.status

    if (status === 401 && originalRequest && !(originalRequest as any).__isRetryRequest) {
      ;(originalRequest as any).__isRetryRequest = true
      try {
        await refreshSessionOnce()
        return request.request(originalRequest)
      } catch {
        await authStore.logout()
      }
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
    if (!config.skipErrorMessage) {
      ElMessage.error(error instanceof Error ? error.message : '请求失败')
    }
    throw error
  }
}

export { request }