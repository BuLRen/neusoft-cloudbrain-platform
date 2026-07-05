import { API_BASE_URL, REQUEST_TIMEOUT } from '../config/env'
import { clearSession, session } from '../stores/session'

export interface ApiResult<T> { code:number; message:string; data:T }
export interface RequestOptions { url:string; method?:UniApp.RequestOptions['method']; data?:unknown; header?:Record<string,string>; skipAuth?:boolean; showError?:boolean }
let redirecting = false

function redirectToLogin() {
  if (redirecting) return
  redirecting = true
  clearSession()
  uni.reLaunch({ url:'/pages/login/index', complete:()=>setTimeout(()=>{redirecting=false},500) })
}

export function request<T>(options: RequestOptions): Promise<T> {
  return new Promise((resolve,reject) => {
    if (!API_BASE_URL) { reject(new Error('未配置后端 API 地址')); return }
    const headers: Record<string,string> = { 'Content-Type':'application/json', ...(options.header || {}) }
    if (!options.skipAuth && session.token) headers.Authorization = `Bearer ${session.token}`
    uni.request<ApiResult<T>>({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data as UniApp.RequestOptions['data'],
      header: headers,
      timeout: REQUEST_TIMEOUT,
      success(response) {
        const body = response.data
        if (response.statusCode === 401 || body?.code === 401) {
          redirectToLogin(); reject(new Error(body?.message || '登录已过期')); return
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          const error = new Error(body?.message || `网络请求失败（${response.statusCode}）`)
          if (options.showError !== false) uni.showToast({ title:error.message, icon:'none' })
          reject(error); return
        }
        if (!body || body.code !== 200) {
          const error = new Error(body?.message || '请求失败')
          if (options.showError !== false) uni.showToast({ title:error.message, icon:'none' })
          reject(error); return
        }
        resolve(body.data)
      },
      fail(error) {
        const message = error.errMsg?.includes('timeout') ? '请求超时，请检查后端服务和网络' : '无法连接后端服务'
        if (options.showError !== false) uni.showToast({ title:message, icon:'none', duration:2500 })
        reject(new Error(message))
      },
    })
  })
}
