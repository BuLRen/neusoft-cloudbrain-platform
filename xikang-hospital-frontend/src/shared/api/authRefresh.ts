import axios from 'axios'
import { useAuthStore } from '@/app/stores/auth'
import type { ApiResult } from './result'
import {
  canRefreshSession as hasRefreshToken,
  getRefreshToken,
  setAccessToken,
} from '@/shared/auth/tokenStorage'

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

let refreshPromise: Promise<string> | null = null

/** 使用 refresh_token 刷新 access_token，并同步到当前会话存储 */
export async function refreshAccessToken(): Promise<string> {
  if (!getRefreshToken()) {
    throw new Error('No refresh token')
  }
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiResult<{ token?: string; accessToken?: string }>>('/auth/refresh')
      .then((response) => {
        const body = response.data
        if (body.code !== 200) {
          throw new Error(body.message || '刷新登录失败')
        }
        const nextToken = body.data?.token || body.data?.accessToken || ''
        if (!nextToken) {
          throw new Error('刷新登录未返回 access token')
        }
        setAccessToken(nextToken)
        const authStore = useAuthStore()
        authStore.token = nextToken
        return nextToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export function canRefreshSession(): boolean {
  return hasRefreshToken()
}
