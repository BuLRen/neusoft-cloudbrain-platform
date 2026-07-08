import { API_BASE_URL, REQUEST_TIMEOUT } from '../config/env'
import { clearSession, saveSession, session } from '../stores/session'
import { disconnectNotification } from '../stores/notification'
import { replacePage } from '../utils/navigation'

export interface ApiResult<T> { code:number; message:string; data:T }
export interface RequestOptions { url:string; method?:UniApp.RequestOptions['method']; data?:unknown; params?:Record<string, unknown>; header?:Record<string,string>; skipAuth?:boolean; showError?:boolean; _retried?:boolean }

function buildQueryString(params: Record<string, unknown>): string {
  const pairs: string[] = []
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item === undefined || item === null || item === '') continue
        pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(item))}`)
      }
    } else {
      pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    }
  }
  return pairs.length ? `?${pairs.join('&')}` : ''
}
let redirecting = false
let refreshing: Promise<string> | null = null

function refreshAccessToken(): Promise<string> {
  if (refreshing) return refreshing
  refreshing = new Promise((resolve,reject) => {
    if (!session.refreshToken) { reject(new Error('缺少刷新令牌')); return }
    uni.request<ApiResult<{token?:string;accessToken?:string}>>({
      url:`${API_BASE_URL}/auth/refresh`, method:'POST', data:{refreshToken:session.refreshToken},
      header:{'Content-Type':'application/json','X-Refresh-Token':session.refreshToken}, timeout:REQUEST_TIMEOUT,
      success(response){const token=response.data?.data?.token||response.data?.data?.accessToken;if(response.statusCode===200&&response.data?.code===200&&token){saveSession({token});resolve(token)}else reject(new Error(response.data?.message||'登录已过期'))},
      fail:()=>reject(new Error('刷新登录状态失败')),
      complete:()=>setTimeout(()=>{refreshing=null},0),
    })
  })
  return refreshing
}

function redirectToLogin() {
  if (redirecting) return
  redirecting = true
  clearSession()
  try { disconnectNotification() } catch { /* ignore */ }
  replacePage('/pages/login/index', true)
  setTimeout(()=>{redirecting=false},800)
}

export function request<T>(options: RequestOptions): Promise<T> {
  return new Promise((resolve,reject) => {
    if (!API_BASE_URL) { reject(new Error('未配置后端 API 地址')); return }
    const headers: Record<string,string> = { 'Content-Type':'application/json', ...(options.header || {}) }
    if (!options.skipAuth && session.token) headers.Authorization = `Bearer ${session.token}`
    const query = options.params ? buildQueryString(options.params) : ''
    uni.request<ApiResult<T>>({
      url: `${API_BASE_URL}${options.url}${query}`,
      method: options.method || 'GET',
      data: options.data as UniApp.RequestOptions['data'],
      header: headers,
      timeout: REQUEST_TIMEOUT,
      async success(response) {
        const body = response.data
        if (response.statusCode === 401 || body?.code === 401) {
          if (!options.skipAuth && !options._retried && session.refreshToken) {
            try { await refreshAccessToken(); resolve(await request<T>({...options,_retried:true})); return } catch { /* refresh token 也已失效 */ }
          }
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
