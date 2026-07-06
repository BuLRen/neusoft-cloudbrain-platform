/** 解析 JWT payload（不验签，仅用于客户端过期预判） */
function parseJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(base64)
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return null
  }
}

/** access token 是否已过期或即将过期（默认提前 60s 刷新） */
export function isAccessTokenExpired(token: string, skewMs = 60_000): boolean {
  if (!token) return true
  const payload = parseJwtPayload(token)
  const exp = payload?.exp
  if (typeof exp !== 'number') return false
  return Date.now() >= exp * 1000 - skewMs
}
