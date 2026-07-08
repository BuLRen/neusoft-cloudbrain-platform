import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { useAuthStore } from '@/app/stores/auth'
import { loginRoutePath } from '@/shared/constants/app'
import { canRefreshSession, refreshAccessToken } from '@/shared/api/authRefresh'
import { getAccessToken } from '@/shared/auth/tokenStorage'
import type { ApiResult } from './result'

const sessionExpiredMessage = '登录已过期，请重新登录'
let sessionExpiredRedirecting = false
export const blobClient = axios.create({
  baseURL: '/api',
  timeout: 60_000,
  withCredentials: true,
})

function isOnLoginPage() {
  if (typeof window === 'undefined') return false
  return window.location.pathname === loginRoutePath
}

function forceRedirectToLogin() {
  if (sessionExpiredRedirecting) return
  sessionExpiredRedirecting = true
  if (typeof window !== 'undefined') {
    sessionStorage.setItem('session_expired_message', sessionExpiredMessage)
    if (!isOnLoginPage()) {
      window.location.replace(loginRoutePath)
    }
  }
}

async function refreshSessionOnce() {
  if (!canRefreshSession()) {
    throw new Error(sessionExpiredMessage)
  }
  await refreshAccessToken()
}

async function parseBlobMessage(data: Blob): Promise<string> {
  const text = await data.text()
  if (!text) {
    return '请求失败'
  }
  try {
    const json = JSON.parse(text) as ApiResult & {
      status?: number
      error?: string
      path?: string
    }
    if (json.message) {
      return json.message
    }
    if (json.error) {
      const status = json.status ?? json.code
      return status ? `${status} ${json.error}` : json.error
    }
    return text
  } catch {
    return text
  }
}

function isJsonBlobResponse(response: AxiosResponse<Blob>) {
  const contentType = String(response.headers['content-type'] || '').toLowerCase()
  return contentType.includes('application/json')
}

blobClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

blobClient.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob' && isJsonBlobResponse(response)) {
      const message = await parseBlobMessage(response.data)
      return Promise.reject(new Error(message))
    }
    return response
  },
  async (error: AxiosError<Blob>) => {
    const authStore = useAuthStore()
    const originalRequest = error.config as (AxiosRequestConfig & { __isRetryRequest?: boolean }) | undefined
    const status = error.response?.status
    const blobData = error.response?.data

    if (status === 401 && originalRequest && !originalRequest.__isRetryRequest) {
      originalRequest.__isRetryRequest = true
      try {
        await refreshSessionOnce()
        if (originalRequest.headers) {
          const nextToken = getAccessToken()
          if (nextToken) {
            originalRequest.headers.set('Authorization', `Bearer ${nextToken}`)
          }
        }
        return blobClient.request(originalRequest)
      } catch {
        authStore.clearSession()
        forceRedirectToLogin()
        return Promise.reject(new Error(sessionExpiredMessage))
      }
    }

    if (status === 401) {
      authStore.clearSession()
      forceRedirectToLogin()
      return Promise.reject(new Error(sessionExpiredMessage))
    }

    if (blobData instanceof Blob) {
      const message = await parseBlobMessage(blobData)
      return Promise.reject(new Error(message))
    }

    return Promise.reject(new Error(error.message || '网络请求异常'))
  },
)

export function downloadBlob(data: BlobPart, filename: string, mimeType: string) {
  const blob = new Blob([data], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function filenameFromContentDisposition(header?: string, fallback = 'download.xlsx') {
  if (!header) return fallback
  const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const plainMatch = header.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || fallback
}
