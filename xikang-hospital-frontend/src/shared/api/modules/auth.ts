import { http } from '../request'

export const authApi = {
  get<T>(url: string, params?: Record<string, unknown>, options?: { skipErrorMessage?: boolean }) {
    const token = localStorage.getItem('access_token')
    return http<T>({
      url,
      method: 'GET',
      params,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      skipErrorMessage: options?.skipErrorMessage,
    })
  },
  post<T>(url: string, data?: unknown, options?: { skipErrorMessage?: boolean }) {
    const token = localStorage.getItem('access_token')
    return http<T>({
      url,
      method: 'POST',
      data,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      skipErrorMessage: options?.skipErrorMessage,
    })
  },
  put<T>(url: string, data?: unknown) {
    const token = localStorage.getItem('access_token')
    return http<T>({
      url,
      method: 'PUT',
      data,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },
  delete<T>(url: string) {
    const token = localStorage.getItem('access_token')
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
}