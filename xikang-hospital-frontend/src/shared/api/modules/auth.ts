
import { http } from '../request'

export const authApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'PUT', data })
  },
  delete<T>(url: string) {
    return http<T>({ url, method: 'DELETE' })
  },
}
