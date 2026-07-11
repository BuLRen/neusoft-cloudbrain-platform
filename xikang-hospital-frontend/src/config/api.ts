import { apiLocalConfig } from './api.local'

export type { ApiLocalConfig } from './api.local.example'

/** 合并后的 API 配置（来源：api.local.ts） */
export const apiConfig = apiLocalConfig

/** Axios baseURL：优先 apiBaseUrl，否则 apiBasePath */
export function getAxiosBaseURL(): string {
  const absolute = apiConfig.apiBaseUrl?.trim()
  if (absolute) {
    return absolute.replace(/\/$/, '')
  }
  return (apiConfig.apiBasePath || '/api').replace(/\/$/, '')
}

/**
 * 拼接完整 API URL，供 fetch / EventSource 等使用。
 * @param path 接口路径，如 `/auth/login`、`/registration/calling/stream/global`
 */
export function apiUrl(path: string): string {
  const base = getAxiosBaseURL()
  let normalized = path.startsWith('/') ? path : `/${path}`
  const basePath = (apiConfig.apiBasePath || '/api').replace(/\/$/, '')

  if (normalized.startsWith(`${basePath}/`)) {
    normalized = normalized.slice(basePath.length)
  } else if (normalized === basePath) {
    normalized = ''
  }

  return `${base}${normalized}`
}

/** Vite 开发代理目标（仅 dev server） */
export function getDevProxyTarget(): string {
  return apiConfig.devProxyTarget.replace(/\/$/, '')
}
