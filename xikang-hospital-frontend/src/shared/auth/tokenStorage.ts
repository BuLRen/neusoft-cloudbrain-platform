const REMEMBER_ME_KEY = 'remember_me'
const REMEMBERED_USERNAME_KEY = 'remembered_username'
const ACCESS_TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

export function isRememberMeEnabled(): boolean {
  return localStorage.getItem(REMEMBER_ME_KEY) === '1'
}

export function setRememberMePreference(enabled: boolean) {
  localStorage.setItem(REMEMBER_ME_KEY, enabled ? '1' : '0')
}

export function getRememberedUsername(): string {
  if (!isRememberMeEnabled()) return ''
  return localStorage.getItem(REMEMBERED_USERNAME_KEY) || ''
}

export function saveRememberedUsername(username: string, enabled: boolean) {
  if (enabled) {
    localStorage.setItem(REMEMBERED_USERNAME_KEY, username.trim())
  } else {
    localStorage.removeItem(REMEMBERED_USERNAME_KEY)
  }
}

function getRefreshTokenStorage(): Storage | null {
  if (localStorage.getItem(REFRESH_TOKEN_KEY)) return localStorage
  if (sessionStorage.getItem(REFRESH_TOKEN_KEY)) return sessionStorage
  return null
}

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || sessionStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function canRefreshSession(): boolean {
  return Boolean(getRefreshToken())
}

export function setTokens(accessToken: string, refreshToken: string, rememberMe: boolean) {
  const primary = rememberMe ? localStorage : sessionStorage
  const secondary = rememberMe ? sessionStorage : localStorage

  primary.setItem(ACCESS_TOKEN_KEY, accessToken)
  primary.setItem(REFRESH_TOKEN_KEY, refreshToken)
  secondary.removeItem(ACCESS_TOKEN_KEY)
  secondary.removeItem(REFRESH_TOKEN_KEY)
  setRememberMePreference(rememberMe)
}

export function setAccessToken(accessToken: string) {
  const storage = getRefreshTokenStorage() || (isRememberMeEnabled() ? localStorage : sessionStorage)
  storage.setItem(ACCESS_TOKEN_KEY, accessToken)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}
