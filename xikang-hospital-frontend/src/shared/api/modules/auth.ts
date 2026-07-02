import { http } from '../request'
import { getAccessToken } from '@/shared/auth/tokenStorage'

export interface CaptchaResponse {
  captchaId: string
  imageBase64: string
}

export const authApi = {
  get<T>(url: string, params?: Record<string, unknown>, options?: { skipErrorMessage?: boolean; skipAuthHandling?: boolean }) {
    const token = getAccessToken()
    return http<T>({
      url,
      method: 'GET',
      params,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      skipErrorMessage: options?.skipErrorMessage,
      skipAuthHandling: options?.skipAuthHandling,
    })
  },
  post<T>(url: string, data?: unknown, options?: { skipErrorMessage?: boolean; skipAuthHandling?: boolean }) {
    const token = getAccessToken()
    return http<T>({
      url,
      method: 'POST',
      data,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      skipErrorMessage: options?.skipErrorMessage,
      skipAuthHandling: options?.skipAuthHandling,
    })
  },
  put<T>(url: string, data?: unknown) {
    const token = getAccessToken()
    return http<T>({
      url,
      method: 'PUT',
      data,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },
  delete<T>(url: string) {
    const token = getAccessToken()
    return http<T>({
      url,
      method: 'DELETE',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },

  // Change password
  changePassword(oldPassword: string, newPassword: string) {
    return authApi.post('/auth/change-password', {
      oldPassword,
      newPassword,
    })
  },

  getCaptcha() {
    return authApi.get<CaptchaResponse>('/auth/captcha', undefined, { skipAuthHandling: true })
  },
}