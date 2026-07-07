const rawBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '').trim()
export const API_BASE_URL = rawBaseUrl.replace(/\/$/, '')
export const REQUEST_TIMEOUT = 90000
